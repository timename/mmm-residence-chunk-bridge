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
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class LandService {

    private static final String MAIN_AREA = "main";

    private final MMMResidenceChunkBridgePlugin plugin;
    private final PluginSettings settings;
    private final LandDataStore dataStore;
    private final EconomyService economyService;
    private final ResidenceHook residenceHook;

    public LandService(
        MMMResidenceChunkBridgePlugin plugin,
        PluginSettings settings,
        LandDataStore dataStore,
        EconomyService economyService,
        ResidenceHook residenceHook
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.dataStore = dataStore;
        this.economyService = economyService;
        this.residenceHook = residenceHook;
    }

    public List<ManagedClaim> getClaims(UUID ownerUuid) {
        return dataStore.findOwnedBy(ownerUuid).stream()
            .sorted(Comparator.comparing(ManagedClaim::residenceName))
            .toList();
    }

    public void sendHelp(CommandSender sender) {
        for (String line : plugin.getConfig().getStringList("messages.help")) {
            sender.sendMessage(plugin.color(line));
        }
    }

    public CreateCheckResult prepareCreateClaim(Player player, String displayName) {
        if (!isAllowedWorld(player.getWorld().getName())) {
            return CreateCheckResult.denied(plugin.message("world-not-allowed"));
        }

        int currentClaims = getClaims(player.getUniqueId()).size();
        int maxClaims = getMaxClaims(player);
        if (currentClaims >= maxClaims) {
            return CreateCheckResult.denied(render(plugin.message("too-many-claims"), Map.of("limit", Integer.toString(maxClaims))));
        }

        int nextOrdinal = currentClaims + 1;
        double price = getCreatePrice(nextOrdinal);
        if (price > 0 && !economyService.has(player.getUniqueId(), price)) {
            return CreateCheckResult.denied(money(plugin.message("insufficient-funds"), price));
        }

        String finalDisplayName = normalizeRequestedDisplayName(player, displayName);
        if (finalDisplayName == null) {
            return CreateCheckResult.denied(null);
        }

        ChunkBounds bounds = ChunkBounds.single(player.getChunk());
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
        CreateCheckResult check = prepareCreateClaim(player, displayName);
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

        ChunkBounds newBounds = claim.bounds().expand(direction, amount);
        int deltaChunks = newBounds.area() - claim.bounds().area();
        double price = deltaChunks * settings.expandPricePerChunk();

        if (price > 0 && !economyService.has(player.getUniqueId(), price)) {
            player.sendMessage(money(plugin.message("insufficient-funds"), price));
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

        if (price > 0 && !economyService.withdraw(player.getUniqueId(), price)) {
            player.sendMessage(money(plugin.message("insufficient-funds"), price));
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
            price
        ));
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
        return getCreatePrice(getClaims(player.getUniqueId()).size() + 1);
    }

    public double getExpandPricePerChunk() {
        return settings.expandPricePerChunk();
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

    private double getCreatePrice(int ordinal) {
        Double exact = settings.createTiers().get(ordinal);
        if (exact != null) {
            return exact;
        }
        if (!settings.fallbackLastTier() || settings.createTiers().isEmpty()) {
            return 0D;
        }
        return settings.createTiers().entrySet().stream()
            .max(Map.Entry.comparingByKey())
            .map(Map.Entry::getValue)
            .orElse(0D);
    }

    private String generateInternalName(Player player) {
        String prefix = settings.internalNamePrefix().toLowerCase(Locale.ROOT);
        return prefix + "_" + player.getUniqueId().toString().substring(0, 8) + "_" + System.currentTimeMillis();
    }

    private String normalizeRequestedDisplayName(Player player, String requestedDisplayName) {
        return normalizeRequestedDisplayName(player, requestedDisplayName, null);
    }

    private String normalizeRequestedDisplayName(Player player, String requestedDisplayName, String ignoredResidenceName) {
        String finalDisplayName = requestedDisplayName == null || requestedDisplayName.isBlank()
            ? "领地" + (getClaims(player.getUniqueId()).size() + 1)
            : requestedDisplayName.trim();

        boolean exists = getClaims(player.getUniqueId()).stream()
            .filter(claim -> ignoredResidenceName == null || !claim.residenceName().equalsIgnoreCase(ignoredResidenceName))
            .map(ManagedClaim::displayName)
            .anyMatch(name -> name.equalsIgnoreCase(finalDisplayName));
        if (exists) {
            player.sendMessage(render(plugin.message("display-name-exists"), Map.of("name", finalDisplayName)));
            return null;
        }
        return finalDisplayName;
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
        String output = plugin.message("prefix") + message;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            output = output.replace("%" + entry.getKey() + "%", Objects.toString(entry.getValue(), ""));
        }
        return output.replace("%currency%", economyService.currencyDisplayName());
    }

    private String money(String message, Map<String, String> replacements, double amount) {
        return render(message, replacements).replace("%price%", formatAmount(amount));
    }

    private String money(String message, double amount) {
        return money(message, Map.of(), amount);
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
}
