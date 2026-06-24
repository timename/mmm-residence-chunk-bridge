package local.mmm.residencechunk.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import local.mmm.residencechunk.MMMResidenceChunkBridgePlugin;
import local.mmm.residencechunk.model.ChunkBounds;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

public final class SelectionService implements Listener {

    private final MMMResidenceChunkBridgePlugin plugin;
    private final LandService landService;
    private final VisualService visualService;
    private final Map<UUID, SelectionSession> sessions = new ConcurrentHashMap<>();

    public SelectionService(MMMResidenceChunkBridgePlugin plugin, LandService landService, VisualService visualService) {
        this.plugin = plugin;
        this.landService = landService;
        this.visualService = visualService;
    }

    public void startSelection(Player player, String displayName) {
        cancelSelection(player, null);
        SelectionSession session = new SelectionSession(SelectionMode.CREATE, player.getWorld().getName(), displayName, null);
        startSession(player, session);
        player.sendMessage(plugin.message("select-start"));
        player.sendMessage(plugin.message("select-instruction"));
    }

    public void startResizeSelection(Player player, String residenceName) {
        cancelSelection(player, null);
        var claim = landService.resolveOwnedClaim(player, residenceName);
        if (claim == null) {
            return;
        }
        if (!player.getWorld().getName().equals(claim.worldName())) {
            player.sendMessage(plugin.message("select-wrong-world"));
            return;
        }
        SelectionSession session = new SelectionSession(SelectionMode.RESIZE, claim.worldName(), claim.displayName(), claim.residenceName());
        session.start(claim.bounds().minChunkX(), claim.bounds().minChunkZ());
        session.end(claim.bounds().maxChunkX(), claim.bounds().maxChunkZ());
        startSession(player, session);
        player.sendMessage(plugin.message("resize-select-start").replace("%name%", claim.displayName()));
        player.sendMessage(plugin.message("resize-select-instruction"));
    }

    private void startSession(Player player, SelectionSession session) {
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tickPreview(player.getUniqueId()), 0L, plugin.settings().selectionPreviewPeriodTicks());
        BukkitTask timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> timeoutSelection(player.getUniqueId()), plugin.settings().selectionTimeoutSeconds() * 20L);
        session.previewTask(task);
        session.timeoutTask(timeoutTask);
        sessions.put(player.getUniqueId(), session);
        player.closeInventory();
    }

    public boolean hasSelection(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public void confirmSelection(Player player) {
        SelectionSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            player.sendMessage(plugin.message("select-none"));
            return;
        }
        ChunkBounds bounds = session.bounds();
        if (bounds == null) {
            player.sendMessage(plugin.message("select-incomplete"));
            return;
        }
        if (!player.getWorld().getName().equals(session.worldName())) {
            cancelSelection(player, "select-cancelled-world");
            return;
        }

        if (session.mode() == SelectionMode.RESIZE) {
            LandService.ResizeCheckResult check = refreshResizeCheck(player, session);
            if (!check.allowed()) {
                if (check.message() != null && !check.message().isBlank()) {
                    player.sendMessage(check.message());
                }
                return;
            }
            sessions.remove(player.getUniqueId());
            session.cancelTask();
            landService.resizeClaim(player, session.claimName(), bounds);
            return;
        }

        LandService.CreateCheckResult check = refreshSessionCheck(player, session);
        if (!check.allowed()) {
            if (check.message() != null && !check.message().isBlank()) {
                player.sendMessage(check.message());
            }
            return;
        }
        sessions.remove(player.getUniqueId());
        session.cancelTask();
        landService.createPreparedClaim(player, check);
    }

    public void cancelSelection(Player player, String messagePath) {
        SelectionSession removed = sessions.remove(player.getUniqueId());
        if (removed != null) {
            removed.cancelTask();
            if (messagePath != null) {
                player.sendMessage(plugin.message(messagePath));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        SelectionSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!hasSelectionTool(player)) {
            player.sendMessage(plugin.message("select-tool-required")
                .replace("%tool%", plugin.settings().selectionTool()));
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || block.getWorld() == null) {
            return;
        }
        event.setCancelled(true);

        if (!block.getWorld().getName().equals(session.worldName())) {
            player.sendMessage(plugin.message("select-wrong-world"));
            return;
        }

        int chunkX = block.getChunk().getX();
        int chunkZ = block.getChunk().getZ();
        if (action == Action.LEFT_CLICK_BLOCK) {
            session.start(chunkX, chunkZ);
            player.sendMessage(plugin.message("select-start-point")
                .replace("%x%", Integer.toString(chunkX))
                .replace("%z%", Integer.toString(chunkZ)));
        } else {
            session.end(chunkX, chunkZ);
            player.sendMessage(plugin.message("select-end-point")
                .replace("%x%", Integer.toString(chunkX))
                .replace("%z%", Integer.toString(chunkZ)));
        }
        if (session.mode() == SelectionMode.RESIZE) {
            refreshResizeCheck(player, session);
        } else {
            refreshSessionCheck(player, session);
        }
        sendSelectionSummary(player, session);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!sessions.containsKey(event.getPlayer().getUniqueId())) {
            return;
        }
        String message = event.getMessage().trim();
        if (!"确认".equalsIgnoreCase(message) && !"confirm".equalsIgnoreCase(message)
            && !"取消".equalsIgnoreCase(message) && !"cancel".equalsIgnoreCase(message)) {
            return;
        }

        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if ("确认".equalsIgnoreCase(message) || "confirm".equalsIgnoreCase(message)) {
                confirmSelection(event.getPlayer());
            } else {
                cancelSelection(event.getPlayer(), "select-cancelled");
            }
        });
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        cancelSelection(event.getPlayer(), "select-cancelled-world");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelSelection(event.getPlayer(), null);
    }

    private void tickPreview(UUID playerUuid) {
        Player player = Bukkit.getPlayer(playerUuid);
        SelectionSession session = sessions.get(playerUuid);
        if (player == null || !player.isOnline() || session == null) {
            if (session != null) {
                session.cancelTask();
                sessions.remove(playerUuid);
            }
            return;
        }
        ChunkBounds bounds = session.bounds();
        if (bounds == null) {
            drawCurrentChunk(player);
            return;
        }
        if (!player.getWorld().getName().equals(session.worldName())) {
            cancelSelection(player, "select-cancelled-world");
            return;
        }

        boolean allowed;
        if (session.mode() == SelectionMode.RESIZE) {
            LandService.ResizeCheckResult check = session.lastResizeCheck();
            if (check == null) {
                check = refreshResizeCheck(player, session);
            }
            allowed = check.allowed();
        } else {
            LandService.CreateCheckResult check = session.lastCheck();
            if (check == null) {
                check = refreshSessionCheck(player, session);
            }
            allowed = check.allowed();
        }
        Color color = allowed ? Color.LIME : Color.RED;
        drawCurrentChunk(player);
        visualService.drawBounds(player, bounds, player.getWorld(), color);
    }

    private void drawCurrentChunk(Player player) {
        if (!plugin.pluginConfig().getBoolean("visual.current-chunk-enabled", true)) {
            return;
        }
        visualService.drawCurrentChunk(player);
    }

    private void sendSelectionSummary(Player player, SelectionSession session) {
        ChunkBounds bounds = session.bounds();
        if (bounds == null) {
            return;
        }
        if (session.mode() == SelectionMode.RESIZE) {
            sendResizeSummary(player, session, bounds);
            return;
        }
        LandService.CreateCheckResult check = session.lastCheck();
        if (check == null) {
            check = refreshSessionCheck(player, session);
        }
        String message = plugin.message(check.allowed() ? "select-summary" : "select-summary-invalid")
            .replace("%chunks%", Integer.toString(bounds.area()))
            .replace("%minX%", Integer.toString(bounds.minChunkX()))
            .replace("%minZ%", Integer.toString(bounds.minChunkZ()))
            .replace("%maxX%", Integer.toString(bounds.maxChunkX()))
            .replace("%maxZ%", Integer.toString(bounds.maxChunkZ()))
            .replace("%price%", check.summary())
            .replace("%currency%", "");
        player.sendMessage(message);
        if (!check.allowed() && check.message() != null && !check.message().isBlank()) {
            player.sendMessage(check.message());
        }
    }

    private LandService.CreateCheckResult refreshSessionCheck(Player player, SelectionSession session) {
        ChunkBounds bounds = session.bounds();
        if (bounds == null) {
            session.lastCheck(null);
            return null;
        }
        LandService.CreateCheckResult check = landService.prepareCreateClaim(player, session.displayName(), bounds);
        session.lastCheck(check);
        return check;
    }

    private LandService.ResizeCheckResult refreshResizeCheck(Player player, SelectionSession session) {
        ChunkBounds bounds = session.bounds();
        if (bounds == null) {
            session.lastResizeCheck(null);
            return null;
        }
        LandService.ResizeCheckResult check = landService.previewResizeCost(player, session.claimName(), bounds);
        session.lastResizeCheck(check);
        return check;
    }

    private void sendResizeSummary(Player player, SelectionSession session, ChunkBounds bounds) {
        LandService.ResizeCheckResult check = session.lastResizeCheck();
        if (check == null) {
            check = refreshResizeCheck(player, session);
        }
        String message = plugin.message(check.allowed() ? "resize-select-summary" : "resize-select-summary-invalid")
            .replace("%name%", session.displayName())
            .replace("%chunks%", Integer.toString(bounds.area()))
            .replace("%minX%", Integer.toString(bounds.minChunkX()))
            .replace("%minZ%", Integer.toString(bounds.minChunkZ()))
            .replace("%maxX%", Integer.toString(bounds.maxChunkX()))
            .replace("%maxZ%", Integer.toString(bounds.maxChunkZ()))
            .replace("%delta%", Integer.toString(check.deltaChunks()))
            .replace("%price%", check.summary())
            .replace("%currency%", "");
        player.sendMessage(message);
        if (!check.allowed() && check.message() != null && !check.message().isBlank()) {
            player.sendMessage(check.message());
        }
    }

    private boolean hasSelectionTool(Player player) {
        if (!plugin.settings().selectionRequireTool()) {
            return true;
        }
        Material material = Material.matchMaterial(plugin.settings().selectionTool());
        if (material == null) {
            return true;
        }
        return player.getInventory().getItemInMainHand().getType() == material
            || player.getInventory().getItemInOffHand().getType() == material;
    }

    private void timeoutSelection(UUID playerUuid) {
        SelectionSession session = sessions.remove(playerUuid);
        if (session == null) {
            return;
        }
        session.cancelTasks();
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            player.sendMessage(plugin.message("select-timeout"));
        }
    }

    private String formatPrice(double amount) {
        if (Math.floor(amount) == amount) {
            return Long.toString((long) amount);
        }
        return String.format(java.util.Locale.US, "%.2f", amount);
    }

    private static final class SelectionSession {
        private final SelectionMode mode;
        private final String worldName;
        private final String displayName;
        private final String claimName;
        private Integer startChunkX;
        private Integer startChunkZ;
        private Integer endChunkX;
        private Integer endChunkZ;
        private BukkitTask previewTask;
        private BukkitTask timeoutTask;
        private LandService.CreateCheckResult lastCheck;
        private LandService.ResizeCheckResult lastResizeCheck;

        private SelectionSession(SelectionMode mode, String worldName, String displayName, String claimName) {
            this.mode = mode;
            this.worldName = worldName;
            this.displayName = displayName;
            this.claimName = claimName;
        }

        private SelectionMode mode() {
            return mode;
        }

        private String worldName() {
            return worldName;
        }

        private String displayName() {
            return displayName;
        }

        private String claimName() {
            return claimName;
        }

        private void start(int chunkX, int chunkZ) {
            this.startChunkX = chunkX;
            this.startChunkZ = chunkZ;
        }

        private void end(int chunkX, int chunkZ) {
            this.endChunkX = chunkX;
            this.endChunkZ = chunkZ;
        }

        private void previewTask(BukkitTask previewTask) {
            this.previewTask = previewTask;
        }

        private void timeoutTask(BukkitTask timeoutTask) {
            this.timeoutTask = timeoutTask;
        }

        private LandService.CreateCheckResult lastCheck() {
            return lastCheck;
        }

        private void lastCheck(LandService.CreateCheckResult lastCheck) {
            this.lastCheck = lastCheck;
        }

        private LandService.ResizeCheckResult lastResizeCheck() {
            return lastResizeCheck;
        }

        private void lastResizeCheck(LandService.ResizeCheckResult lastResizeCheck) {
            this.lastResizeCheck = lastResizeCheck;
        }

        private ChunkBounds bounds() {
            if (startChunkX == null || startChunkZ == null || endChunkX == null || endChunkZ == null) {
                return null;
            }
            return new ChunkBounds(
                Math.min(startChunkX, endChunkX),
                Math.max(startChunkX, endChunkX),
                Math.min(startChunkZ, endChunkZ),
                Math.max(startChunkZ, endChunkZ)
            );
        }

        private void cancelTask() {
            cancelTasks();
        }

        private void cancelTasks() {
            if (previewTask != null) {
                previewTask.cancel();
                previewTask = null;
            }
            if (timeoutTask != null) {
                timeoutTask.cancel();
                timeoutTask = null;
            }
        }
    }

    private enum SelectionMode {
        CREATE,
        RESIZE
    }
}
