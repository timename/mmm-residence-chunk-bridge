package local.mmm.residencechunk.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import local.mmm.residencechunk.MMMResidenceChunkBridgePlugin;
import local.mmm.residencechunk.config.PluginSettings;
import local.mmm.residencechunk.model.ChunkBounds;
import local.mmm.residencechunk.model.ChunkBounds.Direction;
import local.mmm.residencechunk.model.ManagedClaim;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class LandService {

    private static final String MAIN_AREA = "main";

    private final MMMResidenceChunkBridgePlugin plugin;
    private PluginSettings settings;
    private final LandDataStore dataStore;
    private final EconomyService economyService;
    private final CustomCurrencyService customCurrencyService;
    private final ResidenceHook residenceHook;
    private final AuditLogService auditLogService;

    public LandService(
        MMMResidenceChunkBridgePlugin plugin,
        PluginSettings settings,
        LandDataStore dataStore,
        EconomyService economyService,
        CustomCurrencyService customCurrencyService,
        ResidenceHook residenceHook,
        AuditLogService auditLogService
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.dataStore = dataStore;
        this.economyService = economyService;
        this.customCurrencyService = customCurrencyService;
        this.residenceHook = residenceHook;
        this.auditLogService = auditLogService;
    }

    public void reloadSettings(PluginSettings settings) {
        this.settings = settings;
    }

    public List<ManagedClaim> getClaims(UUID ownerUuid) {
        return dataStore.findOwnedBy(ownerUuid).stream()
            .sorted(Comparator.comparing(ManagedClaim::residenceName))
            .toList();
    }

    public List<ManagedClaim> getAllClaims() {
        return dataStore.allClaims().stream()
            .sorted(Comparator.comparing(ManagedClaim::ownerName).thenComparing(ManagedClaim::residenceName))
            .toList();
    }

    public void sendHelp(CommandSender sender) {
        for (String line : plugin.getConfig().getStringList("messages.help")) {
            sender.sendMessage(plugin.color(line));
        }
    }

    public CreateCheckResult prepareCreateClaim(Player player, String displayName) {
        return prepareCreateClaim(player, displayName, ChunkBounds.single(player.getChunk()));
    }

    public CreateCheckResult prepareCreateClaim(Player player, String displayName, ChunkBounds bounds) {
        if (!isAllowedWorld(player.getWorld().getName())) {
            return CreateCheckResult.denied(plugin.message("world-not-allowed"));
        }
        if (!isAllowedShape(bounds)) {
            return CreateCheckResult.denied(plugin.message("rectangle-only"));
        }

        int currentClaims = getClaims(player.getUniqueId()).size();
        int maxClaims = getMaxClaims(player);
        if (currentClaims >= maxClaims) {
            return CreateCheckResult.denied(render(plugin.message("too-many-claims"), Map.of("limit", Integer.toString(maxClaims))));
        }

        int nextOrdinal = currentClaims + 1;
        if (bounds.area() > settings.maxChunksPerClaim()) {
            return CreateCheckResult.denied(render(plugin.message("too-many-chunks"), Map.of(
                "chunks", Integer.toString(bounds.area()),
                "limit", Integer.toString(settings.maxChunksPerClaim())
            )));
        }

        double price = getCreatePrice(nextOrdinal, bounds.area());
        if (price > 0 && !economyService.has(player.getUniqueId(), price)) {
            return CreateCheckResult.denied(money(plugin.message("insufficient-funds"), price));
        }

        String finalDisplayName = normalizeRequestedDisplayName(player, displayName);
        if (finalDisplayName == null) {
            return CreateCheckResult.denied(null);
        }

        if (isInsideProtectedCenter(bounds)) {
            return CreateCheckResult.denied(plugin.message("inside-protected-center"));
        }

        String internalName = generateInternalName(player);
        Object area = toArea(player.getWorld(), bounds);
        Object manager = getResidenceManager();
        String collision = residenceHook.checkAreaCollision(manager, area, null, player.getUniqueId());
        if (collision != null) {
            return CreateCheckResult.denied(render(plugin.message("collision"), Map.of("target", collision)));
        }

        return CreateCheckResult.allowed(finalDisplayName, internalName, player.getWorld().getName(), bounds, price);
    }

    public void createClaim(Player player, String displayName) {
        createClaim(player, displayName, ChunkBounds.single(player.getChunk()));
    }

    public void createClaim(Player player, String displayName, ChunkBounds bounds) {
        CreateCheckResult check = prepareCreateClaim(player, displayName, bounds);
        createPreparedClaim(player, check);
    }

    public void createPreparedClaim(Player player, CreateCheckResult check) {
        if (!check.allowed()) {
            if (check.message() != null && !check.message().isBlank()) {
                player.sendMessage(check.message());
            }
            return;
        }

        if (check.price() > 0 && !economyService.withdraw(player.getUniqueId(), check.price())) {
            player.sendMessage(money(plugin.message("insufficient-funds"), check.price()));
            return;
        }

        boolean created = residenceHook.addResidence(getResidenceManager(), player, player.getName(), player.getUniqueId(), check.internalName(),
            lowLocation(player.getWorld(), check.bounds()), highLocation(player.getWorld(), check.bounds()), true);
        if (!created) {
            player.sendMessage(plugin.message("residence-missing"));
            return;
        }

        Object residence = residenceHook.getByName(getResidenceManager(), check.internalName());
        if (residence == null) {
            player.sendMessage(plugin.message("residence-missing"));
            return;
        }

        if (plugin.getConfig().getBoolean("claims.set-teleport-on-create", true)) {
            residenceHook.setTeleportLocation(residence, player, true);
        }

        ManagedClaim claim = new ManagedClaim(
            check.internalName(),
            check.displayName(),
            player.getUniqueId(),
            player.getName(),
            check.worldName(),
            check.bounds()
        );
        dataStore.put(claim);
        dataStore.save();

        player.sendMessage(money(
            plugin.message("create-success"),
            Map.of("name", claim.displayName()),
            check.price()
        ));
        auditLogService.log(player, "CREATE", "claim=" + claim.residenceName() + " display=" + claim.displayName()
            + " world=" + claim.worldName() + " chunks=" + claim.bounds().area() + " price=" + formatAmount(check.price()));
    }

    public void expandClaim(Player player, String residenceName, String directionInput, String amountInput) {
        ManagedClaim claim = requireOwnedClaim(player, residenceName);
        if (claim == null) {
            return;
        }

        Direction direction = Direction.parse(directionInput);
        if (direction == null) {
            player.sendMessage(plugin.message("invalid-direction"));
            return;
        }

        Integer amount = parsePositiveInt(amountInput);
        if (amount == null) {
            player.sendMessage(plugin.message("invalid-amount"));
            return;
        }

        ExpandCost cost = previewExpandCost(claim, directionInput, amount);
        ChunkBounds newBounds = cost.bounds();
        int deltaChunks = cost.deltaChunks();
        if (!isAllowedShape(newBounds)) {
            player.sendMessage(plugin.message("rectangle-only"));
            return;
        }

        String availabilityMessage = checkCurrencyAvailability(cost);
        if (availabilityMessage != null) {
            player.sendMessage(availabilityMessage);
            return;
        }

        if (cost.price() > 0 && !hasFunds(player, cost)) {
            player.sendMessage(money(plugin.message("insufficient-funds"), cost.price(), cost.currencyDisplayName()));
            return;
        }

        World world = requireClaimWorld(player, claim);
        if (world == null) {
            return;
        }

        Object area = toArea(world, newBounds);
        Object manager = getResidenceManager();
        Object residence = residenceHook.getByName(manager, claim.residenceName());
        if (residence == null) {
            player.sendMessage(plugin.message("residence-missing"));
            return;
        }

        String collision = residenceHook.checkAreaCollision(manager, area, residence, player.getUniqueId());
        if (collision != null) {
            player.sendMessage(render(plugin.message("collision"), Map.of("target", collision)));
            return;
        }

        if (cost.price() > 0 && !withdrawFunds(player, cost, "mmm-land-expand:" + claim.residenceName())) {
            player.sendMessage(money(plugin.message("insufficient-funds"), cost.price(), cost.currencyDisplayName()));
            return;
        }

        boolean updated = residenceHook.replaceArea(residence, player, area, MAIN_AREA, true);
        if (!updated) {
            player.sendMessage(plugin.message("residence-missing"));
            return;
        }

        ManagedClaim updatedClaim = new ManagedClaim(
            claim.residenceName(),
            claim.displayName(),
            claim.ownerUuid(),
            claim.ownerName(),
            claim.worldName(),
            newBounds
        );
        dataStore.put(updatedClaim);
        dataStore.save();

        player.sendMessage(money(
            plugin.message("expand-success"),
            Map.of("name", updatedClaim.displayName(), "delta", Integer.toString(deltaChunks)),
            cost.price(),
            cost.currencyDisplayName()
        ));
        auditLogService.log(player, "EXPAND", "claim=" + updatedClaim.residenceName() + " display=" + updatedClaim.displayName()
            + " delta=" + deltaChunks + " chunks=" + updatedClaim.bounds().area() + " price=" + formatAmount(cost.price())
            + " currency=" + cost.currencyDisplayName());
    }

    public void contractClaim(Player player, String residenceName, String directionInput, String amountInput) {
        ManagedClaim claim = requireOwnedClaim(player, residenceName);
        if (claim == null) {
            return;
        }

        Direction direction = Direction.parse(directionInput);
        if (direction == null) {
            player.sendMessage(plugin.message("invalid-direction"));
            return;
        }

        Integer amount = parsePositiveInt(amountInput);
        if (amount == null) {
            player.sendMessage(plugin.message("invalid-amount"));
            return;
        }

        ChunkBounds newBounds = claim.bounds().contract(direction, amount);
        if (!isAllowedShape(newBounds)) {
            player.sendMessage(plugin.message("rectangle-only"));
            return;
        }
        if (newBounds.width() <= 0 || newBounds.depth() <= 0 || newBounds.area() < settings.minChunks()) {
            player.sendMessage(plugin.message("cannot-contract-below-min"));
            return;
        }

        World world = requireClaimWorld(player, claim);
        if (world == null) {
            return;
        }

        Object residence = residenceHook.getByName(getResidenceManager(), claim.residenceName());
        if (residence == null) {
            player.sendMessage(plugin.message("residence-missing"));
            return;
        }

        boolean updated = residenceHook.replaceArea(residence, player, toArea(world, newBounds), MAIN_AREA, true);
        if (!updated) {
            player.sendMessage(plugin.message("residence-missing"));
            return;
        }

        int deltaChunks = claim.bounds().area() - newBounds.area();
        ManagedClaim updatedClaim = new ManagedClaim(
            claim.residenceName(),
            claim.displayName(),
            claim.ownerUuid(),
            claim.ownerName(),
            claim.worldName(),
            newBounds
        );
        dataStore.put(updatedClaim);
        dataStore.save();

        player.sendMessage(render(
            plugin.message("contract-success"),
            Map.of("name", updatedClaim.displayName(), "delta", Integer.toString(deltaChunks))
        ));
        auditLogService.log(player, "CONTRACT", "claim=" + updatedClaim.residenceName() + " display=" + updatedClaim.displayName()
            + " delta=" + deltaChunks + " chunks=" + updatedClaim.bounds().area());
    }

    public void deleteClaim(Player player, String residenceName) {
        ManagedClaim claim = requireOwnedClaim(player, residenceName);
        if (claim == null) {
            return;
        }

        Object manager = getResidenceManager();
        Object residence = residenceHook.getByName(manager, claim.residenceName());
        if (residence == null) {
            player.sendMessage(plugin.message("residence-missing"));
            return;
        }

        residenceHook.removeResidence(manager, player, residence, true);
        dataStore.remove(claim.residenceName());
        dataStore.save();
        player.sendMessage(render(plugin.message("delete-success"), Map.of("name", claim.displayName())));
        auditLogService.log(player, "DELETE", "claim=" + claim.residenceName() + " display=" + claim.displayName());
    }

    public void adminListClaims(Player player) {
        List<ManagedClaim> claims = getAllClaims();
        if (claims.isEmpty()) {
            player.sendMessage(plugin.message("no-managed-claims"));
            return;
        }
        player.sendMessage(plugin.message("admin-list-header").replace("%count%", Integer.toString(claims.size())));
        for (ManagedClaim claim : claims) {
            player.sendMessage(render(
                plugin.message("admin-list-entry"),
                Map.of(
                    "owner", claim.ownerName(),
                    "display", claim.displayName(),
                    "name", claim.residenceName(),
                    "world", claim.worldName(),
                    "chunks", Integer.toString(claim.bounds().area())
                )
            ));
        }
    }

    public void adminCheckClaims(Player player) {
        int missing = 0;
        Object manager = getResidenceManager();
        for (ManagedClaim claim : getAllClaims()) {
            if (residenceHook.getByName(manager, claim.residenceName()) == null) {
                missing++;
                player.sendMessage(render(plugin.message("admin-check-missing"), Map.of(
                    "owner", claim.ownerName(),
                    "display", claim.displayName(),
                    "name", claim.residenceName()
                )));
            }
        }
        player.sendMessage(plugin.message("admin-check-done")
            .replace("%missing%", Integer.toString(missing))
            .replace("%total%", Integer.toString(getAllClaims().size())));
    }

    public void adminCleanClaims(Player player) {
        int removed = 0;
        Object manager = getResidenceManager();
        for (ManagedClaim claim : getAllClaims()) {
            if (residenceHook.getByName(manager, claim.residenceName()) == null) {
                dataStore.remove(claim.residenceName());
                removed++;
                auditLogService.log(player, "ADMIN_CLEAN", "claim=" + claim.residenceName()
                    + " display=" + claim.displayName() + " owner=" + claim.ownerName());
            }
        }
        if (removed > 0) {
            dataStore.save();
        }
        player.sendMessage(plugin.message("admin-clean-done").replace("%removed%", Integer.toString(removed)));
    }

    public void adminDeleteClaim(Player player, String residenceName) {
        ManagedClaim claim = resolveAnyClaim(residenceName);
        if (claim == null) {
            player.sendMessage(plugin.message("residence-not-managed"));
            return;
        }
        deleteClaimInternal(player, claim, true);
    }

    public void renameClaim(Player player, String currentName, String newDisplayName) {
        ManagedClaim claim = requireOwnedClaim(player, currentName);
        if (claim == null) {
            return;
        }

        String normalized = normalizeRequestedDisplayName(player, newDisplayName, claim.residenceName());
        if (normalized == null) {
            return;
        }

        ManagedClaim updatedClaim = new ManagedClaim(
            claim.residenceName(),
            normalized,
            claim.ownerUuid(),
            claim.ownerName(),
            claim.worldName(),
            claim.bounds()
        );
        dataStore.put(updatedClaim);
        dataStore.save();
        player.sendMessage(render(plugin.message("rename-success"), Map.of(
            "old", claim.displayName(),
            "new", updatedClaim.displayName()
        )));
    }

    public void trustPlayer(Player player, String residenceName, String targetName) {
        ManagedClaim claim = requireOwnedClaim(player, residenceName);
        if (claim == null || !isValidPlayerName(player, targetName)) {
            return;
        }
        if (!applyResidencePlayerFlag(claim, targetName, "trusted", "true")) {
            player.sendMessage(plugin.message("permission-change-failed"));
            return;
        }
        player.sendMessage(render(plugin.message("trust-success"), Map.of(
            "name", claim.displayName(),
            "player", targetName
        )));
        auditLogService.log(player, "TRUST", "claim=" + claim.residenceName() + " display=" + claim.displayName() + " target=" + targetName);
    }

    public void untrustPlayer(Player player, String residenceName, String targetName) {
        ManagedClaim claim = requireOwnedClaim(player, residenceName);
        if (claim == null || !isValidPlayerName(player, targetName)) {
            return;
        }
        if (!applyResidencePlayerFlag(claim, targetName, "trusted", "remove")) {
            player.sendMessage(plugin.message("permission-change-failed"));
            return;
        }
        player.sendMessage(render(plugin.message("untrust-success"), Map.of(
            "name", claim.displayName(),
            "player", targetName
        )));
        auditLogService.log(player, "UNTRUST", "claim=" + claim.residenceName() + " display=" + claim.displayName() + " target=" + targetName);
    }

    public void denyPlayer(Player player, String residenceName, String targetName) {
        ManagedClaim claim = requireOwnedClaim(player, residenceName);
        if (claim == null || !isValidPlayerName(player, targetName)) {
            return;
        }
        boolean move = applyResidencePlayerFlag(claim, targetName, "move", "false");
        boolean tp = applyResidencePlayerFlag(claim, targetName, "tp", "false");
        if (!move || !tp) {
            player.sendMessage(plugin.message("permission-change-failed"));
            return;
        }
        player.sendMessage(render(plugin.message("deny-success"), Map.of(
            "name", claim.displayName(),
            "player", targetName
        )));
        auditLogService.log(player, "DENY", "claim=" + claim.residenceName() + " display=" + claim.displayName() + " target=" + targetName);
    }

    public void undenyPlayer(Player player, String residenceName, String targetName) {
        ManagedClaim claim = requireOwnedClaim(player, residenceName);
        if (claim == null || !isValidPlayerName(player, targetName)) {
            return;
        }
        boolean move = applyResidencePlayerFlag(claim, targetName, "move", "remove");
        boolean tp = applyResidencePlayerFlag(claim, targetName, "tp", "remove");
        if (!move || !tp) {
            player.sendMessage(plugin.message("permission-change-failed"));
            return;
        }
        player.sendMessage(render(plugin.message("undeny-success"), Map.of(
            "name", claim.displayName(),
            "player", targetName
        )));
        auditLogService.log(player, "UNDENY", "claim=" + claim.residenceName() + " display=" + claim.displayName() + " target=" + targetName);
    }

    public void listClaims(Player player) {
        List<ManagedClaim> claims = getClaims(player.getUniqueId());
        if (claims.isEmpty()) {
            player.sendMessage(plugin.message("no-managed-claims"));
            return;
        }

        player.sendMessage(plugin.message("list-header"));
        for (ManagedClaim claim : claims) {
            player.sendMessage(render(
                plugin.message("list-entry"),
                Map.of(
                    "name", claim.displayName(),
                    "world", claim.worldName(),
                    "minChunkX", Integer.toString(claim.bounds().minChunkX()),
                    "minChunkZ", Integer.toString(claim.bounds().minChunkZ()),
                    "maxChunkX", Integer.toString(claim.bounds().maxChunkX()),
                    "maxChunkZ", Integer.toString(claim.bounds().maxChunkZ())
                )
            ));
        }
    }

    public List<String> ownedClaimNames(Player player) {
        return getClaims(player.getUniqueId()).stream()
            .map(ManagedClaim::displayName)
            .collect(Collectors.toCollection(ArrayList::new));
    }

    public List<String> ownedClaimNames(String playerName) {
        OfflinePlayer owner = resolveOfflinePlayer(playerName);
        if (owner == null) {
            return List.of();
        }
        return getClaims(owner.getUniqueId()).stream()
            .map(ManagedClaim::displayName)
            .collect(Collectors.toCollection(ArrayList::new));
    }

    public void adminCreateClaim(Player admin, String ownerName, String displayName) {
        OfflinePlayer owner = resolveOfflinePlayer(ownerName);
        if (owner == null) {
            admin.sendMessage(plugin.message("invalid-player"));
            return;
        }

        ChunkBounds bounds = ChunkBounds.single(admin.getChunk());
        CreateCheckResult check = prepareAdminCreateClaim(admin, owner, displayName, bounds);
        if (!check.allowed()) {
            if (check.message() != null) {
                admin.sendMessage(check.message());
            }
            return;
        }

        boolean created = residenceHook.addResidence(getResidenceManager(), admin, owner.getName(), owner.getUniqueId(), check.internalName(),
            lowLocation(admin.getWorld(), bounds), highLocation(admin.getWorld(), bounds), true);
        if (!created) {
            admin.sendMessage(plugin.message("residence-missing"));
            return;
        }

        Object residence = residenceHook.getByName(getResidenceManager(), check.internalName());
        if (residence != null && plugin.getConfig().getBoolean("claims.set-teleport-on-create", true)) {
            residenceHook.setTeleportLocation(residence, admin, true);
        }

        ManagedClaim claim = new ManagedClaim(
            check.internalName(),
            check.displayName(),
            owner.getUniqueId(),
            owner.getName(),
            admin.getWorld().getName(),
            bounds
        );
        dataStore.put(claim);
        dataStore.save();
        admin.sendMessage(render(plugin.message("admin-create-success"), Map.of(
            "name", claim.displayName(),
            "owner", claim.ownerName(),
            "world", claim.worldName(),
            "x", Integer.toString(bounds.minChunkX()),
            "z", Integer.toString(bounds.minChunkZ())
        )));
        auditLogService.log(admin, "ADMIN_CREATE", "claim=" + claim.residenceName() + " display=" + claim.displayName()
            + " owner=" + claim.ownerName() + " world=" + claim.worldName() + " chunks=" + claim.bounds().area());
    }

    public void adminExpandClaim(Player admin, String ownerName, String residenceName, String directionInput, String amountInput) {
        ManagedClaim claim = requireTargetClaim(admin, ownerName, residenceName);
        if (claim == null) {
            return;
        }
        adminTransformClaim(admin, claim, directionInput, amountInput, true);
    }

    public void adminContractClaim(Player admin, String ownerName, String residenceName, String directionInput, String amountInput) {
        ManagedClaim claim = requireTargetClaim(admin, ownerName, residenceName);
        if (claim == null) {
            return;
        }
        adminTransformClaim(admin, claim, directionInput, amountInput, false);
    }

    public void adminDeletePlayerClaim(Player admin, String ownerName, String residenceName) {
        ManagedClaim claim = requireTargetClaim(admin, ownerName, residenceName);
        if (claim == null) {
            return;
        }
        deleteClaimInternal(admin, claim, true);
    }

    public ManagedClaim resolveOwnedClaim(Player player, String input) {
        List<ManagedClaim> owned = getClaims(player.getUniqueId());
        if (owned.isEmpty()) {
            player.sendMessage(plugin.message("residence-not-managed"));
            return null;
        }

        ManagedClaim exactInternal = matchFirst(owned, claim -> claim.residenceName().equalsIgnoreCase(input));
        if (exactInternal != null) {
            return exactInternal;
        }

        ManagedClaim exactDisplay = matchFirst(owned, claim -> claim.displayName().equalsIgnoreCase(input));
        if (exactDisplay != null) {
            return exactDisplay;
        }

        List<ManagedClaim> partialDisplay = owned.stream()
            .filter(claim -> claim.displayName().toLowerCase(Locale.ROOT).contains(input.toLowerCase(Locale.ROOT)))
            .toList();
        if (partialDisplay.size() == 1) {
            return partialDisplay.get(0);
        }
        if (partialDisplay.size() > 1) {
            player.sendMessage(plugin.message("display-name-ambiguous"));
            return null;
        }

        player.sendMessage(plugin.message("residence-not-managed"));
        return null;
    }

    public double previewCreatePrice(Player player) {
        return getCreatePrice(getClaims(player.getUniqueId()).size() + 1, 1);
    }

    public double getExpandPricePerChunk() {
        return settings.expandPricePerChunk();
    }

    public String getCustomExpandCurrencyDisplayName() {
        return customCurrencyService.displayName(settings.expandCustomCurrencyId(), settings.expandCustomCurrencyDisplayName());
    }

    public ChunkBounds singleChunkBounds(Player player) {
        return ChunkBounds.single(player.getChunk());
    }

    public ChunkBounds previewBounds(ManagedClaim claim, boolean expand, String directionInput, int amount) {
        Direction direction = Direction.parse(directionInput);
        if (direction == null || amount <= 0) {
            return claim.bounds();
        }
        return expand ? claim.bounds().expand(direction, amount) : claim.bounds().contract(direction, amount);
    }

    public ExpandCost previewExpandCost(ManagedClaim claim, String directionInput, int amount) {
        Direction direction = Direction.parse(directionInput);
        if (direction == null || amount <= 0) {
            return new ExpandCost(claim.bounds(), 0, 0D, economyService.currencyDisplayName(), false);
        }
        ChunkBounds newBounds = claim.bounds().expand(direction, amount);
        int deltaChunks = Math.max(0, newBounds.area() - claim.bounds().area());
        boolean customCurrency = requiresCustomCurrency(newBounds);
        double price = deltaChunks * (customCurrency ? settings.expandCustomPricePerChunk() : settings.expandPricePerChunk());
        String currency = customCurrency
            ? customCurrencyService.displayName(settings.expandCustomCurrencyId(), settings.expandCustomCurrencyDisplayName())
            : economyService.currencyDisplayName();
        return new ExpandCost(newBounds, deltaChunks, price, currency, customCurrency);
    }

    private ManagedClaim requireOwnedClaim(Player player, String residenceName) {
        ManagedClaim claim = resolveOwnedClaim(player, residenceName);
        if (claim == null) {
            return null;
        }
        if (!claim.ownerUuid().equals(player.getUniqueId()) && !player.hasPermission("mmmland.admin")) {
            player.sendMessage(plugin.message("residence-not-owner"));
            return null;
        }
        return claim;
    }

    private ManagedClaim requireTargetClaim(Player admin, String ownerName, String residenceName) {
        OfflinePlayer owner = resolveOfflinePlayer(ownerName);
        if (owner == null) {
            admin.sendMessage(plugin.message("invalid-player"));
            return null;
        }
        ManagedClaim claim = resolveOwnedClaim(owner.getUniqueId(), residenceName);
        if (claim == null) {
            admin.sendMessage(plugin.message("residence-not-managed"));
            return null;
        }
        return claim;
    }

    private ManagedClaim resolveOwnedClaim(UUID ownerUuid, String input) {
        List<ManagedClaim> owned = getClaims(ownerUuid);
        ManagedClaim exactInternal = matchFirst(owned, claim -> claim.residenceName().equalsIgnoreCase(input));
        if (exactInternal != null) {
            return exactInternal;
        }
        ManagedClaim exactDisplay = matchFirst(owned, claim -> claim.displayName().equalsIgnoreCase(input));
        if (exactDisplay != null) {
            return exactDisplay;
        }
        List<ManagedClaim> partialDisplay = owned.stream()
            .filter(claim -> claim.displayName().toLowerCase(Locale.ROOT).contains(input.toLowerCase(Locale.ROOT)))
            .toList();
        return partialDisplay.size() == 1 ? partialDisplay.get(0) : null;
    }

    private ManagedClaim resolveAnyClaim(String input) {
        ManagedClaim exactInternal = dataStore.find(input);
        if (exactInternal != null) {
            return exactInternal;
        }
        List<ManagedClaim> displayMatches = getAllClaims().stream()
            .filter(claim -> claim.displayName().equalsIgnoreCase(input))
            .toList();
        return displayMatches.size() == 1 ? displayMatches.get(0) : null;
    }

    private Object getResidenceManager() {
        return residenceHook.getResidenceManager();
    }

    private World requireClaimWorld(Player player, ManagedClaim claim) {
        World world = plugin.getServer().getWorld(claim.worldName());
        if (world == null) {
            player.sendMessage(plugin.message("residence-missing"));
        }
        return world;
    }

    private boolean isAllowedWorld(String worldName) {
        return settings.allowedWorlds().isEmpty() || settings.allowedWorlds().contains(worldName);
    }

    private int getMaxClaims(Player player) {
        int limit = settings.defaultMaxClaims();
        for (Map.Entry<String, Integer> entry : settings.permissionMaxClaims().entrySet()) {
            if (player.hasPermission(entry.getKey())) {
                limit = Math.max(limit, entry.getValue());
            }
        }
        return limit;
    }

    private double getCreatePrice(int ordinal, int chunks) {
        double basePrice;
        Double exact = settings.createTiers().get(ordinal);
        if (exact != null) {
            basePrice = exact;
        } else if (!settings.fallbackLastTier() || settings.createTiers().isEmpty()) {
            basePrice = 0D;
        } else {
            basePrice = settings.createTiers().entrySet().stream()
                .max(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .orElse(0D);
        }
        return basePrice + (Math.max(0, chunks - 1) * settings.createPricePerExtraChunk());
    }

    private boolean requiresCustomCurrency(ChunkBounds bounds) {
        return bounds.area() > settings.expandVaultMaxChunks();
    }

    private boolean isAllowedShape(ChunkBounds bounds) {
        return !settings.rectangularOnly() || bounds.isValidRectangle();
    }

    private CreateCheckResult prepareAdminCreateClaim(Player admin, OfflinePlayer owner, String displayName, ChunkBounds bounds) {
        if (!isAllowedWorld(admin.getWorld().getName())) {
            return CreateCheckResult.denied(plugin.message("world-not-allowed"));
        }
        if (!isAllowedShape(bounds)) {
            return CreateCheckResult.denied(plugin.message("rectangle-only"));
        }
        if (bounds.area() > settings.maxChunksPerClaim()) {
            return CreateCheckResult.denied(render(plugin.message("too-many-chunks"), Map.of(
                "chunks", Integer.toString(bounds.area()),
                "limit", Integer.toString(settings.maxChunksPerClaim())
            )));
        }
        if (isInsideProtectedCenter(bounds)) {
            return CreateCheckResult.denied(plugin.message("inside-protected-center"));
        }

        String finalDisplayName = normalizeRequestedDisplayName(owner, displayName);
        if (finalDisplayName == null) {
            return CreateCheckResult.denied(render(plugin.message("display-name-exists"), Map.of("name", displayNameCandidate(owner.getUniqueId(), displayName))));
        }

        String internalName = generateInternalName(owner);
        Object area = toArea(admin.getWorld(), bounds);
        Object manager = getResidenceManager();
        String collision = residenceHook.checkAreaCollision(manager, area, null, owner.getUniqueId());
        if (collision != null) {
            return CreateCheckResult.denied(render(plugin.message("collision"), Map.of("target", collision)));
        }

        return new CreateCheckResult(true, null, finalDisplayName, internalName, admin.getWorld().getName(), bounds, 0D);
    }

    private void adminTransformClaim(Player actor, ManagedClaim claim, String directionInput, String amountInput, boolean expand) {
        Direction direction = Direction.parse(directionInput);
        if (direction == null) {
            actor.sendMessage(plugin.message("invalid-direction"));
            return;
        }
        Integer amount = parsePositiveInt(amountInput);
        if (amount == null) {
            actor.sendMessage(plugin.message("invalid-amount"));
            return;
        }

        ChunkBounds newBounds = expand ? claim.bounds().expand(direction, amount) : claim.bounds().contract(direction, amount);
        if (!isAllowedShape(newBounds)) {
            actor.sendMessage(plugin.message("rectangle-only"));
            return;
        }
        if (!expand && (newBounds.width() <= 0 || newBounds.depth() <= 0 || newBounds.area() < settings.minChunks())) {
            actor.sendMessage(plugin.message("cannot-contract-below-min"));
            return;
        }
        if (newBounds.area() > settings.maxChunksPerClaim()) {
            actor.sendMessage(render(plugin.message("too-many-chunks"), Map.of(
                "chunks", Integer.toString(newBounds.area()),
                "limit", Integer.toString(settings.maxChunksPerClaim())
            )));
            return;
        }

        World world = requireClaimWorld(actor, claim);
        if (world == null) {
            return;
        }

        Object manager = getResidenceManager();
        Object residence = residenceHook.getByName(manager, claim.residenceName());
        if (residence == null) {
            actor.sendMessage(plugin.message("residence-missing"));
            return;
        }

        Object area = toArea(world, newBounds);
        String collision = residenceHook.checkAreaCollision(manager, area, residence, claim.ownerUuid());
        if (collision != null) {
            actor.sendMessage(render(plugin.message("collision"), Map.of("target", collision)));
            return;
        }

        boolean updated = residenceHook.replaceArea(residence, actor, area, MAIN_AREA, true);
        if (!updated) {
            actor.sendMessage(plugin.message("residence-missing"));
            return;
        }

        int deltaChunks = expand ? newBounds.area() - claim.bounds().area() : claim.bounds().area() - newBounds.area();
        ManagedClaim updatedClaim = new ManagedClaim(
            claim.residenceName(),
            claim.displayName(),
            claim.ownerUuid(),
            claim.ownerName(),
            claim.worldName(),
            newBounds
        );
        dataStore.put(updatedClaim);
        dataStore.save();

        actor.sendMessage(render(plugin.message(expand ? "admin-expand-success" : "admin-contract-success"), Map.of(
            "name", updatedClaim.displayName(),
            "owner", updatedClaim.ownerName(),
            "delta", Integer.toString(deltaChunks),
            "chunks", Integer.toString(updatedClaim.bounds().area())
        )));
        auditLogService.log(actor, expand ? "ADMIN_EXPAND" : "ADMIN_CONTRACT",
            "claim=" + updatedClaim.residenceName() + " display=" + updatedClaim.displayName()
                + " owner=" + updatedClaim.ownerName() + " delta=" + deltaChunks + " chunks=" + updatedClaim.bounds().area());
    }

    private void deleteClaimInternal(Player actor, ManagedClaim claim, boolean adminAction) {
        Object manager = getResidenceManager();
        Object residence = residenceHook.getByName(manager, claim.residenceName());
        if (residence != null) {
            residenceHook.removeResidence(manager, actor, residence, true);
        }
        dataStore.remove(claim.residenceName());
        dataStore.save();
        actor.sendMessage(render(plugin.message(adminAction ? "admin-delete-success" : "delete-success"), Map.of(
            "owner", claim.ownerName(),
            "display", claim.displayName(),
            "name", claim.displayName()
        )));
        auditLogService.log(actor, adminAction ? "ADMIN_DELETE" : "DELETE", "claim=" + claim.residenceName()
            + " display=" + claim.displayName() + " owner=" + claim.ownerName());
    }

    private OfflinePlayer resolveOfflinePlayer(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Player online = plugin.getServer().getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer offline = plugin.getServer().getOfflinePlayer(name);
        if (offline == null || (!offline.hasPlayedBefore() && !offline.isOnline())) {
            return null;
        }
        return offline;
    }

    private String checkCurrencyAvailability(ExpandCost cost) {
        if (!cost.customCurrency()) {
            return null;
        }
        if (!settings.expandCustomCurrencyEnabled()) {
            return plugin.message("custom-currency-disabled");
        }
        if (!customCurrencyService.isAvailable(settings.expandCustomCurrencyId())) {
            return render(plugin.message("custom-currency-unavailable"), Map.of(
                "currency", cost.currencyDisplayName(),
                "currencyId", settings.expandCustomCurrencyId()
            ), cost.currencyDisplayName());
        }
        return null;
    }

    private boolean hasFunds(Player player, ExpandCost cost) {
        if (cost.customCurrency()) {
            return customCurrencyService.has(player.getUniqueId(), settings.expandCustomCurrencyId(), cost.price());
        }
        return economyService.has(player.getUniqueId(), cost.price());
    }

    private boolean withdrawFunds(Player player, ExpandCost cost, String reason) {
        if (cost.customCurrency()) {
            return customCurrencyService.withdraw(player.getUniqueId(), settings.expandCustomCurrencyId(), cost.price(), reason);
        }
        return economyService.withdraw(player.getUniqueId(), cost.price());
    }

    private boolean applyResidencePlayerFlag(ManagedClaim claim, String targetName, String flag, String state) {
        String command = "resadmin pset " + claim.residenceName() + " " + targetName + " " + flag + " " + state;
        return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    private boolean isValidPlayerName(Player sender, String playerName) {
        if (playerName == null || !playerName.matches("[A-Za-z0-9_]{3,16}")) {
            sender.sendMessage(plugin.message("invalid-player-name"));
            return false;
        }
        return true;
    }

    private String generateInternalName(Player player) {
        return generateInternalName(player.getUniqueId());
    }

    private String generateInternalName(OfflinePlayer player) {
        return generateInternalName(player.getUniqueId());
    }

    private String generateInternalName(UUID playerUuid) {
        String prefix = settings.internalNamePrefix().toLowerCase(Locale.ROOT);
        return prefix + "_" + playerUuid.toString().substring(0, 8) + "_" + System.currentTimeMillis();
    }

    private String normalizeRequestedDisplayName(Player player, String requestedDisplayName) {
        return normalizeRequestedDisplayName(player, requestedDisplayName, null);
    }

    private String normalizeRequestedDisplayName(OfflinePlayer player, String requestedDisplayName) {
        return normalizeRequestedDisplayName(player.getUniqueId(), requestedDisplayName, null);
    }

    private String normalizeRequestedDisplayName(Player player, String requestedDisplayName, String ignoredResidenceName) {
        String normalized = normalizeRequestedDisplayName(player.getUniqueId(), requestedDisplayName, ignoredResidenceName);
        if (normalized == null) {
            player.sendMessage(render(plugin.message("display-name-exists"), Map.of("name", displayNameCandidate(player.getUniqueId(), requestedDisplayName))));
        }
        return normalized;
    }

    private String normalizeRequestedDisplayName(UUID ownerUuid, String requestedDisplayName, String ignoredResidenceName) {
        String finalDisplayName = displayNameCandidate(ownerUuid, requestedDisplayName);

        boolean exists = getClaims(ownerUuid).stream()
            .filter(claim -> ignoredResidenceName == null || !claim.residenceName().equalsIgnoreCase(ignoredResidenceName))
            .map(ManagedClaim::displayName)
            .anyMatch(name -> name.equalsIgnoreCase(finalDisplayName));
        if (exists) {
            return null;
        }
        return finalDisplayName;
    }

    private String displayNameCandidate(UUID ownerUuid, String requestedDisplayName) {
        return requestedDisplayName == null || requestedDisplayName.isBlank()
            ? "领地" + (getClaims(ownerUuid).size() + 1)
            : requestedDisplayName.trim();
    }

    private ManagedClaim matchFirst(List<ManagedClaim> claims, Function<ManagedClaim, Boolean> matcher) {
        for (ManagedClaim claim : claims) {
            if (Boolean.TRUE.equals(matcher.apply(claim))) {
                return claim;
            }
        }
        return null;
    }

    private Object toArea(World world, ChunkBounds bounds) {
        return residenceHook.createArea(lowLocation(world, bounds), highLocation(world, bounds));
    }

    private Location lowLocation(World world, ChunkBounds bounds) {
        int minBlockX = bounds.minChunkX() << 4;
        int minBlockZ = bounds.minChunkZ() << 4;
        int minY = settings.fullHeight() ? world.getMinHeight() : world.getMinHeight();
        return new Location(world, minBlockX, minY, minBlockZ);
    }

    private Location highLocation(World world, ChunkBounds bounds) {
        int maxBlockX = (bounds.maxChunkX() << 4) + 15;
        int maxBlockZ = (bounds.maxChunkZ() << 4) + 15;
        int maxY = settings.fullHeight() ? world.getMaxHeight() - 1 : world.getMaxHeight() - 1;
        return new Location(world, maxBlockX, maxY, maxBlockZ);
    }

    private boolean isInsideProtectedCenter(ChunkBounds bounds) {
        int radius = settings.noClaimRadiusBlocks();
        if (radius <= 0) {
            return false;
        }
        int minBlockX = bounds.minChunkX() << 4;
        int minBlockZ = bounds.minChunkZ() << 4;
        int maxBlockX = (bounds.maxChunkX() << 4) + 15;
        int maxBlockZ = (bounds.maxChunkZ() << 4) + 15;
        double nearestX = clamp(settings.protectedCenterX(), minBlockX, maxBlockX);
        double nearestZ = clamp(settings.protectedCenterZ(), minBlockZ, maxBlockZ);
        double dx = nearestX - settings.protectedCenterX();
        double dz = nearestZ - settings.protectedCenterZ();
        return (dx * dx) + (dz * dz) <= (double) radius * radius;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private Integer parsePositiveInt(String input) {
        try {
            int value = Integer.parseInt(input);
            return value > 0 ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String render(String message, Map<String, String> replacements) {
        return render(message, replacements, economyService.currencyDisplayName());
    }

    private String render(String message, Map<String, String> replacements, String currencyDisplayName) {
        String output = plugin.message("prefix") + message;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            output = output.replace("%" + entry.getKey() + "%", Objects.toString(entry.getValue(), ""));
        }
        return output.replace("%currency%", currencyDisplayName);
    }

    private String money(String message, Map<String, String> replacements, double amount) {
        return money(message, replacements, amount, economyService.currencyDisplayName());
    }

    private String money(String message, Map<String, String> replacements, double amount, String currencyDisplayName) {
        return render(message, replacements, currencyDisplayName).replace("%price%", formatAmount(amount));
    }

    private String money(String message, double amount) {
        return money(message, Map.of(), amount);
    }

    private String money(String message, double amount, String currencyDisplayName) {
        return money(message, Map.of(), amount, currencyDisplayName);
    }

    private String formatAmount(double amount) {
        if (Math.floor(amount) == amount) {
            return Long.toString((long) amount);
        }
        return String.format(Locale.US, "%.2f", amount);
    }

    public record CreateCheckResult(
        boolean allowed,
        String message,
        String displayName,
        String internalName,
        String worldName,
        ChunkBounds bounds,
        double price
    ) {
        public static CreateCheckResult denied(String message) {
            return new CreateCheckResult(false, message, null, null, null, null, 0D);
        }

        public static CreateCheckResult allowed(String displayName, String internalName, String worldName, ChunkBounds bounds, double price) {
            return new CreateCheckResult(true, null, displayName, internalName, worldName, bounds, price);
        }
    }

    public record ExpandCost(
        ChunkBounds bounds,
        int deltaChunks,
        double price,
        String currencyDisplayName,
        boolean customCurrency
    ) {
    }
}
