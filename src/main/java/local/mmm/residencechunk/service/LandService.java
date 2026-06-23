package local.mmm.residencechunk.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import local.mmm.residencechunk.MMMResidenceChunkBridgePlugin;
import local.mmm.residencechunk.config.PluginSettings;
import local.mmm.residencechunk.config.PluginSettings.WorldClaimRule;
import local.mmm.residencechunk.model.ChunkBounds;
import local.mmm.residencechunk.model.ChunkBounds.Direction;
import local.mmm.residencechunk.model.ManagedClaim;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

public final class LandService implements Listener {

    private static final String MAIN_AREA = "main";

    private final MMMResidenceChunkBridgePlugin plugin;
    private PluginSettings settings;
    private final LandDataStore dataStore;
    private final EconomyService economyService;
    private final CustomCurrencyService customCurrencyService;
    private final ResidenceHook residenceHook;
    private final AuditLogService auditLogService;
    private final Map<UUID, PendingTeleport> pendingTeleports = new ConcurrentHashMap<>();

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
        for (String line : plugin.messageList("help")) {
            sender.sendMessage(line);
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
        String worldRuleMessage = checkWorldClaimRule(player.getWorld().getName(), bounds, false);
        if (worldRuleMessage != null) {
            return CreateCheckResult.denied(worldRuleMessage);
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
        String spacingMessage = checkClaimSpacing(player.getWorld().getName(), bounds, null);
        if (spacingMessage != null) {
            return CreateCheckResult.denied(spacingMessage);
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

        if (plugin.pluginConfig().getBoolean("claims.set-teleport-on-create", true)) {
            residenceHook.setTeleportLocation(residence, player, true);
        }

        ManagedClaim claim = new ManagedClaim(
            check.internalName(),
            check.displayName(),
            player.getUniqueId(),
            player.getName(),
            check.worldName(),
            check.bounds(),
            false
        );
        dataStore.put(claim);
        dataStore.save();
        applyResidenceMessages(residence, claim);

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
        String worldRuleMessage = checkWorldClaimRule(claim.worldName(), newBounds, false);
        if (worldRuleMessage != null) {
            player.sendMessage(worldRuleMessage);
            return;
        }
        String spacingMessage = checkClaimSpacing(claim.worldName(), newBounds, claim.residenceName());
        if (spacingMessage != null) {
            player.sendMessage(spacingMessage);
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
            newBounds,
            claim.publicTeleport()
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
        String worldRuleMessage = checkWorldClaimRule(claim.worldName(), newBounds, false);
        if (worldRuleMessage != null) {
            player.sendMessage(worldRuleMessage);
            return;
        }
        String spacingMessage = checkClaimSpacing(claim.worldName(), newBounds, claim.residenceName());
        if (spacingMessage != null) {
            player.sendMessage(spacingMessage);
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
            newBounds,
            claim.publicTeleport()
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

    public ResizeCheckResult prepareResizeClaim(Player player, String residenceName, ChunkBounds newBounds) {
        ManagedClaim claim = requireOwnedClaim(player, residenceName);
        if (claim == null) {
            return ResizeCheckResult.denied(plugin.message("residence-not-managed"));
        }
        return prepareResizeClaim(player, claim, newBounds);
    }

    public void resizeClaim(Player player, String residenceName, ChunkBounds newBounds) {
        ManagedClaim claim = requireOwnedClaim(player, residenceName);
        if (claim == null) {
            return;
        }
        ResizeCheckResult check = prepareResizeClaim(player, claim, newBounds);
        if (!check.allowed()) {
            if (check.message() != null && !check.message().isBlank()) {
                player.sendMessage(check.message());
            }
            return;
        }
        if (check.price() > 0 && !withdrawFunds(player, check.cost(), "mmm-land-resize:" + claim.residenceName())) {
            player.sendMessage(money(plugin.message("insufficient-funds"), check.price(), check.currencyDisplayName()));
            return;
        }
        boolean updated = residenceHook.replaceArea(check.residence(), player, check.area(), MAIN_AREA, true);
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
            newBounds,
            claim.publicTeleport()
        );
        dataStore.put(updatedClaim);
        dataStore.save();
        player.sendMessage(money(plugin.message("resize-success"), Map.of(
            "name", updatedClaim.displayName(),
            "delta", Integer.toString(check.deltaChunks()),
            "chunks", Integer.toString(updatedClaim.bounds().area())
        ), check.price(), check.currencyDisplayName()));
        auditLogService.log(player, "RESIZE", "claim=" + updatedClaim.residenceName() + " display=" + updatedClaim.displayName()
            + " delta=" + check.deltaChunks() + " chunks=" + updatedClaim.bounds().area()
            + " price=" + formatAmount(check.price()) + " currency=" + check.currencyDisplayName());
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

    public void teleportToClaim(Player player, String residenceName) {
        ManagedClaim claim = requireOwnedClaim(player, residenceName);
        if (claim == null) {
            return;
        }
        startTeleport(player, claim);
    }

    public void visitPublicClaim(Player visitor, String ownerName, String claimName) {
        List<ManagedClaim> publicClaims = publicClaimsByOwnerName(ownerName);
        if (publicClaims.isEmpty()) {
            visitor.sendMessage(render(plugin.message("visit-owner-not-found"), Map.of("owner", ownerName)));
            return;
        }
        ManagedClaim claim;
        if (claimName == null || claimName.isBlank()) {
            if (publicClaims.size() > 1) {
                visitor.sendMessage(render(plugin.message("visit-multiple-claims"), Map.of("owner", publicClaims.get(0).ownerName())));
                return;
            }
            claim = publicClaims.get(0);
        } else {
            claim = matchFirst(publicClaims, candidate -> candidate.displayName().equalsIgnoreCase(claimName)
                || candidate.residenceName().equalsIgnoreCase(claimName));
            if (claim == null) {
                visitor.sendMessage(render(plugin.message("visit-claim-not-found"), Map.of("owner", publicClaims.get(0).ownerName())));
                return;
            }
        }
        startTeleport(visitor, claim);
    }

    public void adminTeleportToClaim(Player admin, String ownerName, String claimName) {
        ManagedClaim claim = requireTargetClaim(admin, ownerName, claimName);
        if (claim == null) {
            return;
        }
        startTeleport(admin, claim);
        admin.sendMessage(render(plugin.message("admin-teleport-start"), Map.of(
            "owner", claim.ownerName(),
            "name", claim.displayName()
        )));
        auditLogService.log(admin, "ADMIN_TELEPORT", "claim=" + claim.residenceName()
            + " display=" + claim.displayName() + " owner=" + claim.ownerName());
    }

    public void setPublicTeleport(Player player, String residenceName, boolean enabled) {
        ManagedClaim claim = requireOwnedClaim(player, residenceName);
        if (claim == null) {
            return;
        }
        ManagedClaim updatedClaim = new ManagedClaim(
            claim.residenceName(),
            claim.displayName(),
            claim.ownerUuid(),
            claim.ownerName(),
            claim.worldName(),
            claim.bounds(),
            enabled
        );
        dataStore.put(updatedClaim);
        dataStore.save();
        player.sendMessage(render(plugin.message(enabled ? "public-teleport-enabled" : "public-teleport-disabled"), Map.of(
            "name", updatedClaim.displayName()
        )));
        auditLogService.log(player, enabled ? "PUBLIC_TELEPORT_ON" : "PUBLIC_TELEPORT_OFF",
            "claim=" + updatedClaim.residenceName() + " display=" + updatedClaim.displayName());
    }

    private void startTeleport(Player player, ManagedClaim claim) {
        Object residence = residenceHook.getByName(getResidenceManager(), claim.residenceName());
        if (residence == null) {
            player.sendMessage(plugin.message("residence-missing"));
            return;
        }
        int delaySeconds = teleportDelaySeconds(player);
        if (delaySeconds <= 0) {
            executeResidenceTeleport(player, claim);
            return;
        }
        cancelPendingTeleport(player, null);
        Location start = player.getLocation();
        long startMillis = System.currentTimeMillis();
        BossBar bossBar = Bukkit.createBossBar(
            plugin.color(plugin.message("teleport-bossbar")
                .replace("%name%", claim.displayName())
                .replace("%seconds%", Integer.toString(delaySeconds))),
            BarColor.GREEN,
            BarStyle.SOLID
        );
        bossBar.setProgress(1.0D);
        bossBar.addPlayer(player);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PendingTeleport removed = pendingTeleports.remove(player.getUniqueId());
            if (removed == null || !player.isOnline()) {
                return;
            }
            removed.cleanup();
            executeResidenceTeleport(player, claim);
        }, delaySeconds * 20L);
        BukkitTask updateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> updateTeleportBossBar(player.getUniqueId()), 0L, 20L);
        pendingTeleports.put(player.getUniqueId(), new PendingTeleport(claim.displayName(), claim.residenceName(),
            start.getWorld().getName(), start.getBlockX(), start.getBlockY(), start.getBlockZ(),
            delaySeconds, startMillis, task, updateTask, bossBar));
        player.sendMessage(render(plugin.message("teleport-warmup"), Map.of(
            "name", claim.displayName(),
            "seconds", Integer.toString(delaySeconds)
        )));
    }

    private void executeResidenceTeleport(Player player, ManagedClaim claim) {
        Object residence = residenceHook.getByName(getResidenceManager(), claim.residenceName());
        if (residence == null) {
            player.sendMessage(plugin.message("residence-missing"));
            return;
        }
        Location target = residenceHook.getTeleportLocation(residence, player, true);
        if (target == null || target.getWorld() == null) {
            player.sendMessage(render(plugin.message("teleport-failed"), Map.of("name", claim.displayName())));
            return;
        }
        player.teleport(target);
        player.sendMessage(render(plugin.message("teleport-start"), Map.of("name", claim.displayName())));
        auditLogService.log(player, "TELEPORT", "claim=" + claim.residenceName() + " display=" + claim.displayName());
    }

    public void setClaimTeleport(Player player, String residenceName) {
        ManagedClaim claim = requireOwnedClaim(player, residenceName);
        if (claim == null) {
            return;
        }
        if (!isPlayerInsideClaim(player, claim)) {
            player.sendMessage(render(plugin.message("set-teleport-not-inside"), Map.of("name", claim.displayName())));
            return;
        }
        Object residence = residenceHook.getByName(getResidenceManager(), claim.residenceName());
        if (residence == null) {
            player.sendMessage(plugin.message("residence-missing"));
            return;
        }
        residenceHook.setTeleportLocation(residence, player, true);
        player.sendMessage(render(plugin.message("set-teleport-success"), Map.of("name", claim.displayName())));
        Location location = player.getLocation();
        auditLogService.log(player, "SET_TELEPORT", "claim=" + claim.residenceName() + " display=" + claim.displayName()
            + " world=" + player.getWorld().getName() + " x=" + location.getBlockX() + " y=" + location.getBlockY() + " z=" + location.getBlockZ());
    }

    public void cancelPendingTeleport(Player player, String messagePath) {
        PendingTeleport removed = pendingTeleports.remove(player.getUniqueId());
        if (removed == null) {
            return;
        }
        removed.cleanup();
        if (messagePath != null) {
            player.sendMessage(render(plugin.message(messagePath), Map.of("name", removed.displayName())));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        PendingTeleport pending = pendingTeleports.get(event.getPlayer().getUniqueId());
        if (pending == null || event.getTo() == null) {
            return;
        }
        Location to = event.getTo();
        if (pending.worldName().equals(to.getWorld().getName())
            && pending.blockX() == to.getBlockX()
            && pending.blockY() == to.getBlockY()
            && pending.blockZ() == to.getBlockZ()) {
            return;
        }
        cancelPendingTeleport(event.getPlayer(), "teleport-cancelled-move");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cancelPendingTeleport(event.getPlayer(), null);
    }

    private void updateTeleportBossBar(UUID playerUuid) {
        PendingTeleport pending = pendingTeleports.get(playerUuid);
        Player player = Bukkit.getPlayer(playerUuid);
        if (pending == null || player == null || !player.isOnline()) {
            if (pending != null) {
                pending.cleanup();
                pendingTeleports.remove(playerUuid);
            }
            return;
        }
        long elapsedMillis = Math.max(0L, System.currentTimeMillis() - pending.startMillis());
        double elapsedSeconds = elapsedMillis / 1000.0D;
        double remainingSeconds = Math.max(0D, pending.delaySeconds() - elapsedSeconds);
        double progress = Math.max(0D, Math.min(1D, remainingSeconds / pending.delaySeconds()));
        int shownSeconds = (int) Math.ceil(remainingSeconds);
        pending.bossBar().setTitle(plugin.color(plugin.message("teleport-bossbar")
            .replace("%name%", pending.displayName())
            .replace("%seconds%", Integer.toString(Math.max(1, shownSeconds)))));
        pending.bossBar().setProgress(progress);
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
            claim.bounds(),
            claim.publicTeleport()
        );
        dataStore.put(updatedClaim);
        dataStore.save();
        Object residence = residenceHook.getByName(getResidenceManager(), updatedClaim.residenceName());
        if (residence != null) {
            applyResidenceMessages(residence, updatedClaim);
        }
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

    public List<String> publicTeleportOwnerNames() {
        Set<String> ownerNames = new LinkedHashSet<>();
        getAllClaims().stream()
            .filter(ManagedClaim::publicTeleport)
            .sorted(Comparator.comparing(ManagedClaim::ownerName, String.CASE_INSENSITIVE_ORDER))
            .forEach(claim -> ownerNames.add(claim.ownerName()));
        return new ArrayList<>(ownerNames);
    }

    public List<String> publicTeleportClaimNames(String ownerName) {
        return publicClaimsByOwnerName(ownerName).stream()
            .map(ManagedClaim::displayName)
            .toList();
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
        if (residence != null && plugin.pluginConfig().getBoolean("claims.set-teleport-on-create", true)) {
            residenceHook.setTeleportLocation(residence, admin, true);
        }

        ManagedClaim claim = new ManagedClaim(
            check.internalName(),
            check.displayName(),
            owner.getUniqueId(),
            owner.getName(),
            admin.getWorld().getName(),
            bounds,
            false
        );
        dataStore.put(claim);
        dataStore.save();
        if (residence != null) {
            applyResidenceMessages(residence, claim);
        }
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
        return settings.expandBasePrice();
    }

    public double getExpandPriceIncreasePerChunk() {
        return settings.expandPriceIncreasePerChunk();
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
        double price = calculateExpansionPrice(claim.bounds().area(), deltaChunks);
        String currency = customCurrency
            ? customCurrencyService.displayName(settings.expandCustomCurrencyId(), settings.expandCustomCurrencyDisplayName())
            : economyService.currencyDisplayName();
        return new ExpandCost(newBounds, deltaChunks, price, currency, customCurrency);
    }

    public ResizeCheckResult previewResizeCost(Player player, String residenceName, ChunkBounds newBounds) {
        ManagedClaim claim = resolveOwnedClaim(player, residenceName);
        if (claim == null) {
            return ResizeCheckResult.denied(plugin.message("residence-not-managed"));
        }
        return prepareResizeClaim(player, claim, newBounds);
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

    private List<ManagedClaim> publicClaimsByOwnerName(String ownerName) {
        String lowered = ownerName.toLowerCase(Locale.ROOT);
        return getAllClaims().stream()
            .filter(ManagedClaim::publicTeleport)
            .filter(claim -> claim.ownerName().equalsIgnoreCase(ownerName)
                || claim.ownerName().toLowerCase(Locale.ROOT).startsWith(lowered))
            .sorted(Comparator.comparing(ManagedClaim::displayName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    private Object getResidenceManager() {
        return residenceHook.getResidenceManager();
    }

    public void syncResidenceMessages() {
        Object manager = getResidenceManager();
        int updated = 0;
        for (ManagedClaim claim : getAllClaims()) {
            Object residence = residenceHook.getByName(manager, claim.residenceName());
            if (residence == null) {
                continue;
            }
            applyResidenceMessages(residence, claim);
            updated++;
        }
        if (updated > 0) {
            plugin.getLogger().info("Synced Chinese enter/leave messages for " + updated + " managed residences.");
        }
    }

    private void applyResidenceMessages(Object residence, ManagedClaim claim) {
        residenceHook.setEnterMessage(residence, residenceMessage("residence-enter-message", claim));
        residenceHook.setLeaveMessage(residence, residenceMessage("residence-leave-message", claim));
    }

    private String residenceMessage(String path, ManagedClaim claim) {
        return plugin.message(path)
            .replace("%name%", claim.displayName())
            .replace("%internal%", claim.residenceName())
            .replace("%ownerName%", claim.ownerName());
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

    private String checkClaimSpacing(String worldName, ChunkBounds bounds, String ignoredResidenceName) {
        int spacing = settings.minClaimSpacingChunks();
        if (spacing <= 0) {
            return null;
        }

        String ignoredKey = ignoredResidenceName == null ? null : ignoredResidenceName.toLowerCase(Locale.ROOT);
        for (ManagedClaim other : dataStore.allClaims()) {
            if (!other.worldName().equals(worldName)) {
                continue;
            }
            if (ignoredKey != null && other.residenceName().toLowerCase(Locale.ROOT).equals(ignoredKey)) {
                continue;
            }
            if (isInsideExpandedBounds(bounds, other.bounds(), spacing)) {
                return render(plugin.message("too-close-to-claim"), Map.of(
                    "target", other.displayName(),
                    "distance", Integer.toString(spacing)
                ));
            }
        }
        return null;
    }

    private boolean isInsideExpandedBounds(ChunkBounds candidate, ChunkBounds existing, int spacing) {
        return candidate.maxChunkX() >= existing.minChunkX() - spacing
            && candidate.minChunkX() <= existing.maxChunkX() + spacing
            && candidate.maxChunkZ() >= existing.minChunkZ() - spacing
            && candidate.minChunkZ() <= existing.maxChunkZ() + spacing;
    }

    private String checkWorldClaimRule(String worldName, ChunkBounds bounds, boolean adminAction) {
        if (!isAllowedWorld(worldName)) {
            return plugin.message("world-not-allowed");
        }
        if (adminAction && settings.worldClaimRulesAdminBypass()) {
            return null;
        }

        WorldClaimRule rule = settings.worldClaimRules().get(worldName);
        if (rule == null) {
            return null;
        }

        if (rule.minDistanceFromOriginXz() > 0) {
            long minDistanceSquared = minDistanceSquaredFromOrigin(bounds);
            long requiredSquared = square(rule.minDistanceFromOriginXz());
            if (minDistanceSquared < requiredSquared) {
                return render(plugin.message("too-close-to-origin"), Map.of(
                    "distance", Integer.toString(rule.minDistanceFromOriginXz()),
                    "world", worldName
                ));
            }
        }

        if (rule.maxDistanceFromOriginXz() > 0) {
            long maxDistanceSquared = maxDistanceSquaredFromOrigin(bounds);
            long allowedSquared = square(rule.maxDistanceFromOriginXz());
            if (maxDistanceSquared > allowedSquared) {
                return render(plugin.message("too-far-from-origin"), Map.of(
                    "distance", Integer.toString(rule.maxDistanceFromOriginXz()),
                    "world", worldName
                ));
            }
        }

        return null;
    }

    private long minDistanceSquaredFromOrigin(ChunkBounds bounds) {
        long minBlockX = (long) bounds.minChunkX() << 4;
        long maxBlockX = ((long) bounds.maxChunkX() << 4) + 15L;
        long minBlockZ = (long) bounds.minChunkZ() << 4;
        long maxBlockZ = ((long) bounds.maxChunkZ() << 4) + 15L;
        long nearestX = nearestDistanceToZero(minBlockX, maxBlockX);
        long nearestZ = nearestDistanceToZero(minBlockZ, maxBlockZ);
        return (nearestX * nearestX) + (nearestZ * nearestZ);
    }

    private long maxDistanceSquaredFromOrigin(ChunkBounds bounds) {
        long minBlockX = (long) bounds.minChunkX() << 4;
        long maxBlockX = ((long) bounds.maxChunkX() << 4) + 15L;
        long minBlockZ = (long) bounds.minChunkZ() << 4;
        long maxBlockZ = ((long) bounds.maxChunkZ() << 4) + 15L;
        long farthestX = Math.max(Math.abs(minBlockX), Math.abs(maxBlockX));
        long farthestZ = Math.max(Math.abs(minBlockZ), Math.abs(maxBlockZ));
        return (farthestX * farthestX) + (farthestZ * farthestZ);
    }

    private long nearestDistanceToZero(long min, long max) {
        if (min <= 0L && max >= 0L) {
            return 0L;
        }
        return Math.min(Math.abs(min), Math.abs(max));
    }

    private long square(int value) {
        long longValue = value;
        return longValue * longValue;
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
        String worldRuleMessage = checkWorldClaimRule(admin.getWorld().getName(), bounds, true);
        if (worldRuleMessage != null) {
            return CreateCheckResult.denied(worldRuleMessage);
        }
        if (isInsideProtectedCenter(bounds)) {
            return CreateCheckResult.denied(plugin.message("inside-protected-center"));
        }
        String spacingMessage = checkClaimSpacing(admin.getWorld().getName(), bounds, null);
        if (spacingMessage != null) {
            return CreateCheckResult.denied(spacingMessage);
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

    private ResizeCheckResult prepareResizeClaim(Player player, ManagedClaim claim, ChunkBounds newBounds) {
        if (!player.getWorld().getName().equals(claim.worldName())) {
            return ResizeCheckResult.denied(plugin.message("select-wrong-world"));
        }
        if (!isAllowedShape(newBounds)) {
            return ResizeCheckResult.denied(plugin.message("rectangle-only"));
        }
        if (newBounds.area() < settings.minChunks()) {
            return ResizeCheckResult.denied(plugin.message("cannot-contract-below-min"));
        }
        if (newBounds.area() > settings.maxChunksPerClaim()) {
            return ResizeCheckResult.denied(render(plugin.message("too-many-chunks"), Map.of(
                "chunks", Integer.toString(newBounds.area()),
                "limit", Integer.toString(settings.maxChunksPerClaim())
            )));
        }
        String worldRuleMessage = checkWorldClaimRule(claim.worldName(), newBounds, false);
        if (worldRuleMessage != null) {
            return ResizeCheckResult.denied(worldRuleMessage);
        }
        if (isInsideProtectedCenter(newBounds)) {
            return ResizeCheckResult.denied(plugin.message("inside-protected-center"));
        }
        String spacingMessage = checkClaimSpacing(claim.worldName(), newBounds, claim.residenceName());
        if (spacingMessage != null) {
            return ResizeCheckResult.denied(spacingMessage);
        }

        World world = requireClaimWorld(player, claim);
        if (world == null) {
            return ResizeCheckResult.denied(plugin.message("residence-missing"));
        }
        Object manager = getResidenceManager();
        Object residence = residenceHook.getByName(manager, claim.residenceName());
        if (residence == null) {
            return ResizeCheckResult.denied(plugin.message("residence-missing"));
        }
        Object area = toArea(world, newBounds);
        String collision = residenceHook.checkAreaCollision(manager, area, residence, player.getUniqueId());
        if (collision != null) {
            return ResizeCheckResult.denied(render(plugin.message("collision"), Map.of("target", collision)));
        }

        int deltaChunks = newBounds.area() - claim.bounds().area();
        double price = 0D;
        String currency = economyService.currencyDisplayName();
        boolean customCurrency = false;
        if (deltaChunks > 0) {
            customCurrency = requiresCustomCurrency(newBounds);
            price = calculateExpansionPrice(claim.bounds().area(), deltaChunks);
            currency = customCurrency
                ? customCurrencyService.displayName(settings.expandCustomCurrencyId(), settings.expandCustomCurrencyDisplayName())
                : economyService.currencyDisplayName();
        }
        ExpandCost cost = new ExpandCost(newBounds, Math.max(0, deltaChunks), price, currency, customCurrency);
        String availabilityMessage = checkCurrencyAvailability(cost);
        if (availabilityMessage != null) {
            return ResizeCheckResult.denied(availabilityMessage);
        }
        if (price > 0 && !hasFunds(player, cost)) {
            return ResizeCheckResult.denied(money(plugin.message("insufficient-funds"), price, currency));
        }
        return ResizeCheckResult.allowed(newBounds, area, residence, deltaChunks, cost);
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
        String worldRuleMessage = checkWorldClaimRule(claim.worldName(), newBounds, true);
        if (worldRuleMessage != null) {
            actor.sendMessage(worldRuleMessage);
            return;
        }
        String spacingMessage = checkClaimSpacing(claim.worldName(), newBounds, claim.residenceName());
        if (spacingMessage != null) {
            actor.sendMessage(spacingMessage);
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
            newBounds,
            claim.publicTeleport()
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

    private double calculateExpansionPrice(int currentArea, int deltaChunks) {
        if (deltaChunks <= 0) {
            return 0D;
        }
        int paidChunksBefore = Math.max(0, currentArea - settings.minChunks());
        double total = 0D;
        for (int i = 0; i < deltaChunks; i++) {
            total += settings.expandBasePrice() + ((paidChunksBefore + i) * settings.expandPriceIncreasePerChunk());
        }
        return total;
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

    private boolean isPlayerInsideClaim(Player player, ManagedClaim claim) {
        if (!player.getWorld().getName().equals(claim.worldName())) {
            return false;
        }
        int chunkX = player.getChunk().getX();
        int chunkZ = player.getChunk().getZ();
        return chunkX >= claim.bounds().minChunkX()
            && chunkX <= claim.bounds().maxChunkX()
            && chunkZ >= claim.bounds().minChunkZ()
            && chunkZ <= claim.bounds().maxChunkZ();
    }

    private int teleportDelaySeconds(Player player) {
        int delay = settings.teleportDefaultDelaySeconds();
        for (Map.Entry<String, Integer> entry : settings.teleportPermissionDelays().entrySet()) {
            if (player.hasPermission(entry.getKey())) {
                delay = Math.min(delay, entry.getValue());
            }
        }
        return delay;
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

    public record ResizeCheckResult(
        boolean allowed,
        String message,
        ChunkBounds bounds,
        Object area,
        Object residence,
        int deltaChunks,
        ExpandCost cost
    ) {
        public static ResizeCheckResult denied(String message) {
            return new ResizeCheckResult(false, message, null, null, null, 0, null);
        }

        public static ResizeCheckResult allowed(ChunkBounds bounds, Object area, Object residence, int deltaChunks, ExpandCost cost) {
            return new ResizeCheckResult(true, null, bounds, area, residence, deltaChunks, cost);
        }

        public double price() {
            return cost == null ? 0D : cost.price();
        }

        public String currencyDisplayName() {
            return cost == null ? "" : cost.currencyDisplayName();
        }
    }

    private record PendingTeleport(
        String displayName,
        String residenceName,
        String worldName,
        int blockX,
        int blockY,
        int blockZ,
        int delaySeconds,
        long startMillis,
        BukkitTask task,
        BukkitTask updateTask,
        BossBar bossBar
    ) {
        private void cleanup() {
            task.cancel();
            updateTask.cancel();
            bossBar.removeAll();
        }
    }
}
