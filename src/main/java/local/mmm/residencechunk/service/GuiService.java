package local.mmm.residencechunk.service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import local.mmm.residencechunk.MMMResidenceChunkBridgePlugin;
import local.mmm.residencechunk.model.ChunkBounds;
import local.mmm.residencechunk.model.ManagedClaim;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

public final class GuiService implements Listener {

    private static final int[] AMOUNTS = {1, 2, 4, 8};

    private final MMMResidenceChunkBridgePlugin plugin;
    private final LandService landService;
    private final NamespacedKey claimKey;
    private final NamespacedKey actionKey;
    private final Map<UUID, PendingCreation> pendingCreations = new ConcurrentHashMap<>();

    public GuiService(MMMResidenceChunkBridgePlugin plugin, LandService landService) {
        this.plugin = plugin;
        this.landService = landService;
        this.claimKey = new NamespacedKey(plugin, "claim_name");
        this.actionKey = new NamespacedKey(plugin, "claim_action");
    }

    public void openMainMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(new MainMenuHolder(), 27,
            plugin.color(plugin.getConfig().getString("messages.gui.main-title", "&8领地菜单")));
        fillBackground(inventory);
        inventory.setItem(11, createItem(Material.GRASS_BLOCK, "&a创建当前区块领地",
            "&7在你当前所在区块创建整列领地",
            "&7下一块领地价格: &e" + formatPrice(landService.previewCreatePrice(player)) + " " + plugin.settings().currencyDisplayName(),
            "&b点击后关闭菜单，显示粒子范围",
            "&b随后在聊天栏输入“确认”完成创建"));
        inventory.setItem(13, createItem(Material.MAP, "&b我的领地",
            "&7查看你的领地列表",
            "&7点击打开"));
        inventory.setItem(15, createItem(Material.GOLD_INGOT, "&6价格说明",
            "&7扩张单价: &e" + formatPrice(landService.getExpandPricePerChunk()) + " " + plugin.settings().currencyDisplayName(),
            "&7缩小不会返还货币"));
        player.openInventory(inventory);
    }

    public void openClaimsMenu(Player player) {
        List<ManagedClaim> claims = landService.getClaims(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(new ClaimsListHolder(), 54,
            plugin.color(plugin.getConfig().getString("messages.gui.list-title", "&8我的领地")));
        fillBackground(inventory);
        int slot = 0;
        for (ManagedClaim claim : claims) {
            if (slot >= 45) {
                break;
            }
            ItemStack item = createItem(Material.OAK_SIGN, "&e" + claim.displayName(),
                "&7内部名: &f" + claim.residenceName(),
                "&7世界: &f" + claim.worldName(),
                "&7区块: &f" + claim.bounds().minChunkX() + "," + claim.bounds().minChunkZ()
                    + " -> " + claim.bounds().maxChunkX() + "," + claim.bounds().maxChunkZ(),
                "&7点击进入操作菜单");
            item.editMeta(meta -> meta.getPersistentDataContainer().set(claimKey, PersistentDataType.STRING, claim.residenceName()));
            inventory.setItem(slot++, item);
        }
        inventory.setItem(49, createItem(Material.BARRIER, "&c返回主菜单"));
        player.openInventory(inventory);
    }

    public void openClaimDetailMenu(Player player, ManagedClaim claim) {
        Inventory inventory = Bukkit.createInventory(new ClaimDetailHolder(claim.residenceName()), 27,
            plugin.color(plugin.getConfig().getString("messages.gui.detail-title", "&8领地操作")));
        fillBackground(inventory);
        inventory.setItem(4, createItem(Material.BOOK, "&e" + claim.displayName(),
            "&7内部名: &f" + claim.residenceName(),
            "&7世界: &f" + claim.worldName(),
            "&7区块面积: &f" + claim.bounds().area()));
        inventory.setItem(8, actionItem(Material.ENDER_EYE, "&b预览领地边界", "preview::" + claim.displayName()));
        inventory.setItem(10, actionItem(Material.LIME_CONCRETE, "&a扩张领地", "open_expand::" + claim.displayName()));
        inventory.setItem(12, actionItem(Material.NAME_TAG, "&b重命名提示",
            "noop::" + claim.displayName(),
            "&7请使用命令:",
            "&e/mmmland rename " + claim.displayName() + " 新名字"));
        inventory.setItem(14, actionItem(Material.ORANGE_CONCRETE, "&6缩小领地", "open_contract::" + claim.displayName()));
        inventory.setItem(16, actionItem(Material.LAVA_BUCKET, "&4删除领地", "open_delete::" + claim.displayName()));
        inventory.setItem(18, createItem(Material.BARRIER, "&c返回列表"));
        player.openInventory(inventory);
    }

    public void openDirectionMenu(Player player, ManagedClaim claim, Mode mode) {
        Inventory inventory = Bukkit.createInventory(new DirectionHolder(claim.residenceName(), mode), 27,
            plugin.color(plugin.getConfig().getString("messages.gui.direction-title", "&8选择方向")));
        fillBackground(inventory);
        inventory.setItem(4, createItem(Material.BOOK, mode == Mode.EXPAND ? "&a选择扩张方向" : "&6选择缩小方向",
            "&7当前领地: &f" + claim.displayName()));
        inventory.setItem(10, actionItem(Material.ARROW, "&e北", "pick:" + mode.name().toLowerCase(Locale.ROOT) + ":north:" + claim.displayName()));
        inventory.setItem(12, actionItem(Material.ARROW, "&e西", "pick:" + mode.name().toLowerCase(Locale.ROOT) + ":west:" + claim.displayName()));
        inventory.setItem(14, actionItem(Material.ARROW, "&e东", "pick:" + mode.name().toLowerCase(Locale.ROOT) + ":east:" + claim.displayName()));
        inventory.setItem(16, actionItem(Material.ARROW, "&e南", "pick:" + mode.name().toLowerCase(Locale.ROOT) + ":south:" + claim.displayName()));
        inventory.setItem(22, createItem(Material.BARRIER, "&c返回上一页"));
        player.openInventory(inventory);
    }

    public void openAmountMenu(Player player, ManagedClaim claim, Mode mode, String direction) {
        Inventory inventory = Bukkit.createInventory(new AmountHolder(claim.residenceName(), mode, direction), 27,
            plugin.color(plugin.getConfig().getString("messages.gui.amount-title", "&8选择区块数")));
        fillBackground(inventory);
        inventory.setItem(4, createItem(Material.BOOK, mode == Mode.EXPAND ? "&a选择扩张区块数" : "&6选择缩小区块数",
            "&7方向: &f" + direction,
            mode == Mode.EXPAND
                ? "&7单价: &e" + formatPrice(landService.getExpandPricePerChunk()) + " " + plugin.settings().currencyDisplayName()
                : "&7缩小不会返还货币"));
        for (int i = 0; i < AMOUNTS.length; i++) {
            int amount = AMOUNTS[i];
            Material material = mode == Mode.EXPAND ? Material.GREEN_STAINED_GLASS : Material.ORANGE_STAINED_GLASS;
            String lore = mode == Mode.EXPAND
                ? "&7预计费用: &e" + formatPrice(amount * landService.getExpandPricePerChunk()) + " " + plugin.settings().currencyDisplayName()
                : "&7本操作不会返还货币";
            inventory.setItem(10 + (i * 2), actionItem(material, "&f" + amount + " 区块",
                "apply:" + mode.name().toLowerCase(Locale.ROOT) + ":" + direction + ":" + claim.displayName() + ":" + amount, lore));
        }
        inventory.setItem(22, createItem(Material.BARRIER, "&c返回上一页"));
        player.openInventory(inventory);
    }

    public void openDeleteConfirmMenu(Player player, ManagedClaim claim) {
        Inventory inventory = Bukkit.createInventory(new DeleteConfirmHolder(claim.residenceName()), 27,
            plugin.color(plugin.getConfig().getString("messages.gui.delete-confirm-title", "&8确认删除")));
        fillBackground(inventory);
        inventory.setItem(11, actionItem(Material.RED_CONCRETE, "&4确认删除",
            "delete::" + claim.displayName(),
            "&7领地: &f" + claim.displayName(),
            "&c此操作不可恢复"));
        inventory.setItem(15, createItem(Material.GREEN_CONCRETE, "&a取消并返回"));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof ManagedHolder)) {
            return;
        }

        event.setCancelled(true);
        if (event.getCurrentItem() == null || event.getCurrentItem().getType().isAir()) {
            return;
        }

        if (holder instanceof MainMenuHolder) {
            handleMainMenuClick(player, event.getRawSlot());
            return;
        }
        if (holder instanceof ClaimsListHolder) {
            handleClaimsListClick(player, event.getCurrentItem(), event.getRawSlot());
            return;
        }
        if (holder instanceof ClaimDetailHolder detailHolder) {
            if (event.getRawSlot() == 18) {
                openClaimsMenu(player);
                return;
            }
            routeAction(player, event.getCurrentItem(), detailHolder.residenceName());
            return;
        }
        if (holder instanceof DirectionHolder directionHolder) {
            if (event.getRawSlot() == 22) {
                ManagedClaim claim = landService.resolveOwnedClaim(player, directionHolder.residenceName());
                if (claim != null) {
                    openClaimDetailMenu(player, claim);
                }
                return;
            }
            routeAction(player, event.getCurrentItem(), directionHolder.residenceName());
            return;
        }
        if (holder instanceof AmountHolder amountHolder) {
            if (event.getRawSlot() == 22) {
                ManagedClaim claim = landService.resolveOwnedClaim(player, amountHolder.residenceName());
                if (claim != null) {
                    openDirectionMenu(player, claim, amountHolder.mode());
                }
                return;
            }
            routeAction(player, event.getCurrentItem(), amountHolder.residenceName());
            return;
        }
        if (holder instanceof DeleteConfirmHolder deleteHolder) {
            ManagedClaim claim = landService.resolveOwnedClaim(player, deleteHolder.residenceName());
            if (claim == null) {
                return;
            }
            if (event.getRawSlot() == 15) {
                openClaimDetailMenu(player, claim);
                return;
            }
            routeAction(player, event.getCurrentItem(), deleteHolder.residenceName());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPendingCreateChat(AsyncPlayerChatEvent event) {
        PendingCreation pending = pendingCreations.get(event.getPlayer().getUniqueId());
        if (pending == null) {
            return;
        }

        event.setCancelled(true);
        String message = event.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> handlePendingCreateChat(event.getPlayer(), message));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        PendingCreation pending = pendingCreations.get(event.getPlayer().getUniqueId());
        if (pending == null || event.getTo() == null) {
            return;
        }
        if (sameBlock(event.getFrom(), event.getTo())) {
            return;
        }
        if (isInsideBounds(event.getTo(), pending.worldName(), pending.bounds())) {
            return;
        }
        cancelPendingCreation(event.getPlayer(), "create-cancelled-leave");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelPendingCreation(event.getPlayer(), null);
    }

    private void handleMainMenuClick(Player player, int rawSlot) {
        switch (rawSlot) {
            case 11 -> startCreateConfirmation(player);
            case 13 -> openClaimsMenu(player);
            default -> {
            }
        }
    }

    private void handleClaimsListClick(Player player, ItemStack item, int rawSlot) {
        if (rawSlot == 49) {
            openMainMenu(player);
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer data = meta.getPersistentDataContainer();
        String residenceName = data.get(claimKey, PersistentDataType.STRING);
        if (residenceName == null || residenceName.isBlank()) {
            return;
        }
        ManagedClaim claim = landService.resolveOwnedClaim(player, residenceName);
        if (claim != null) {
            openClaimDetailMenu(player, claim);
        }
    }

    private void routeAction(Player player, ItemStack item, String fallbackResidenceName) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        String encodedAction = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (encodedAction == null || encodedAction.isBlank()) {
            return;
        }

        String[] parts = encodedAction.split(":", 5);
        String action = parts[0];
        switch (action) {
            case "noop" -> {
            }
            case "preview" -> {
                ManagedClaim claim = landService.resolveOwnedClaim(player, fallbackResidenceName);
                if (claim != null) {
                    previewBounds(player, claim.bounds(), claim.worldName());
                    player.closeInventory();
                }
            }
            case "open_expand", "open_contract" -> {
                ManagedClaim claim = landService.resolveOwnedClaim(player, fallbackResidenceName);
                if (claim == null) {
                    return;
                }
                openDirectionMenu(player, claim, "open_expand".equals(action) ? Mode.EXPAND : Mode.CONTRACT);
            }
            case "open_delete" -> {
                ManagedClaim claim = landService.resolveOwnedClaim(player, fallbackResidenceName);
                if (claim == null) {
                    return;
                }
                openDeleteConfirmMenu(player, claim);
            }
            case "pick" -> {
                if (parts.length < 4) {
                    return;
                }
                ManagedClaim claim = landService.resolveOwnedClaim(player, parts[3]);
                if (claim == null) {
                    return;
                }
                openAmountMenu(player, claim, Mode.valueOf(parts[1].toUpperCase(Locale.ROOT)), parts[2]);
            }
            case "apply" -> {
                if (parts.length < 5) {
                    return;
                }
                Mode mode = Mode.valueOf(parts[1].toUpperCase(Locale.ROOT));
                String direction = parts[2];
                String claimName = parts[3];
                String amount = parts[4];
                ManagedClaim previewClaim = landService.resolveOwnedClaim(player, claimName);
                if (previewClaim != null) {
                    previewBounds(player, landService.previewBounds(previewClaim, mode == Mode.EXPAND, direction, Integer.parseInt(amount)), previewClaim.worldName());
                }
                if (mode == Mode.EXPAND) {
                    landService.expandClaim(player, claimName, direction, amount);
                } else {
                    landService.contractClaim(player, claimName, direction, amount);
                }
                Bukkit.getScheduler().runTask(plugin, () -> {
                    ManagedClaim refreshed = landService.resolveOwnedClaim(player, claimName);
                    if (refreshed != null) {
                        openClaimDetailMenu(player, refreshed);
                    } else {
                        openClaimsMenu(player);
                    }
                });
            }
            case "delete" -> {
                if (parts.length < 3) {
                    return;
                }
                String claimName = parts[2];
                landService.deleteClaim(player, claimName);
                Bukkit.getScheduler().runTask(plugin, () -> openClaimsMenu(player));
            }
            default -> {
            }
        }
    }

    private void startCreateConfirmation(Player player) {
        LandService.CreateCheckResult check = landService.prepareCreateClaim(player, null);
        if (!check.allowed()) {
            player.sendMessage(check.message());
            return;
        }

        cancelPendingCreation(player, null);
        PendingCreation pending = new PendingCreation(player.getUniqueId(), check.displayName(), check.worldName(), check.bounds(), check.price());
        pendingCreations.put(player.getUniqueId(), pending);

        player.closeInventory();
        previewBounds(player, check.bounds(), check.worldName());
        player.sendMessage(plugin.message("create-preview")
            .replace("%name%", check.displayName())
            .replace("%price%", formatPrice(check.price()))
            .replace("%currency%", plugin.settings().currencyDisplayName()));
        player.sendMessage(plugin.message("create-confirm-instruction"));
    }

    private void handlePendingCreateChat(Player player, String message) {
        PendingCreation pending = pendingCreations.get(player.getUniqueId());
        if (pending == null) {
            return;
        }

        if ("取消".equalsIgnoreCase(message) || "cancel".equalsIgnoreCase(message)) {
            cancelPendingCreation(player, "create-cancelled");
            return;
        }
        if (!"确认".equalsIgnoreCase(message) && !"confirm".equalsIgnoreCase(message)) {
            player.sendMessage(plugin.message("create-confirm-invalid"));
            return;
        }
        if (!isInsideBounds(player.getLocation(), pending.worldName(), pending.bounds())) {
            cancelPendingCreation(player, "create-cancelled-leave");
            return;
        }

        pendingCreations.remove(player.getUniqueId());
        landService.createClaim(player, pending.displayName());
    }

    private void cancelPendingCreation(Player player, String messagePath) {
        PendingCreation removed = pendingCreations.remove(player.getUniqueId());
        if (removed != null && messagePath != null) {
            player.sendMessage(plugin.message(messagePath));
        }
    }

    private boolean isInsideBounds(Location location, String worldName, ChunkBounds bounds) {
        if (location == null || location.getWorld() == null || !location.getWorld().getName().equals(worldName)) {
            return false;
        }
        int chunkX = location.getChunk().getX();
        int chunkZ = location.getChunk().getZ();
        return chunkX >= bounds.minChunkX()
            && chunkX <= bounds.maxChunkX()
            && chunkZ >= bounds.minChunkZ()
            && chunkZ <= bounds.maxChunkZ();
    }

    private boolean sameBlock(Location from, Location to) {
        return from.getWorld() == to.getWorld()
            && from.getBlockX() == to.getBlockX()
            && from.getBlockY() == to.getBlockY()
            && from.getBlockZ() == to.getBlockZ();
    }

    private ItemStack actionItem(Material material, String name, String encodedAction, String... loreLines) {
        ItemStack item = createItem(material, name, loreLines);
        item.editMeta(meta -> meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, encodedAction));
        return item;
    }

    private ItemStack createItem(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.setDisplayName(plugin.color(name));
            if (loreLines.length > 0) {
                meta.setLore(Arrays.stream(loreLines).map(plugin::color).toList());
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        });
        return item;
    }

    private void fillBackground(Inventory inventory) {
        ItemStack outer = createPane(Material.BLUE_STAINED_GLASS_PANE);
        ItemStack inner = createPane(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        ItemStack accent = createPane(Material.CYAN_STAINED_GLASS_PANE);

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, outer.clone());
        }

        int rows = inventory.getSize() / 9;
        for (int row = 1; row < rows - 1; row++) {
            for (int col = 1; col < 8; col++) {
                inventory.setItem(row * 9 + col, inner.clone());
            }
        }

        for (int col = 2; col <= 6; col++) {
            inventory.setItem(1 * 9 + col, accent.clone());
            inventory.setItem((rows - 2) * 9 + col, accent.clone());
        }
    }

    private ItemStack createPane(Material material) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.setDisplayName(" ");
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        });
        return item;
    }

    private void previewBounds(Player player, ChunkBounds bounds, String worldName) {
        if (!player.getWorld().getName().equals(worldName)) {
            return;
        }
        int durationTicks = Math.max(20, plugin.getConfig().getInt("visual.preview-duration-ticks", 80));
        int step = Math.max(1, plugin.getConfig().getInt("visual.preview-step-blocks", 2));
        Particle particle = Particle.valueOf(plugin.getConfig().getString("visual.preview-particle", "END_ROD"));
        int minX = bounds.minChunkX() << 4;
        int minZ = bounds.minChunkZ() << 4;
        int maxX = (bounds.maxChunkX() << 4) + 15;
        int maxZ = (bounds.maxChunkZ() << 4) + 15;
        double y = Math.max(player.getLocation().getY(), player.getWorld().getMinHeight() + 1);
        BukkitTask[] taskRef = new BukkitTask[1];
        taskRef[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                if (taskRef[0] != null) {
                    taskRef[0].cancel();
                }
                return;
            }
            for (int x = minX; x <= maxX; x += step) {
                player.spawnParticle(particle, x + 0.5, y, minZ + 0.5, 1, 0, 0, 0, 0);
                player.spawnParticle(particle, x + 0.5, y, maxZ + 0.5, 1, 0, 0, 0, 0);
            }
            for (int z = minZ; z <= maxZ; z += step) {
                player.spawnParticle(particle, minX + 0.5, y, z + 0.5, 1, 0, 0, 0, 0);
                player.spawnParticle(particle, maxX + 0.5, y, z + 0.5, 1, 0, 0, 0, 0);
            }
            Particle.DustOptions dust = new Particle.DustOptions(Color.AQUA, 1.4f);
            for (double cornerY = y; cornerY <= y + 3; cornerY += 1) {
                player.spawnParticle(Particle.DUST, minX + 0.5, cornerY, minZ + 0.5, 1, dust);
                player.spawnParticle(Particle.DUST, maxX + 0.5, cornerY, minZ + 0.5, 1, dust);
                player.spawnParticle(Particle.DUST, minX + 0.5, cornerY, maxZ + 0.5, 1, dust);
                player.spawnParticle(Particle.DUST, maxX + 0.5, cornerY, maxZ + 0.5, 1, dust);
            }
        }, 0L, 10L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (taskRef[0] != null) {
                taskRef[0].cancel();
            }
        }, durationTicks);
    }

    private String formatPrice(double amount) {
        if (Math.floor(amount) == amount) {
            return Long.toString((long) amount);
        }
        return String.format(Locale.US, "%.2f", amount);
    }

    private sealed interface ManagedHolder extends InventoryHolder permits MainMenuHolder, ClaimsListHolder,
        ClaimDetailHolder, DirectionHolder, AmountHolder, DeleteConfirmHolder {
        @Override
        default Inventory getInventory() {
            return null;
        }
    }

    private static final class MainMenuHolder implements ManagedHolder {
    }

    private static final class ClaimsListHolder implements ManagedHolder {
    }

    private record ClaimDetailHolder(String residenceName) implements ManagedHolder {
    }

    private record DirectionHolder(String residenceName, Mode mode) implements ManagedHolder {
    }

    private record AmountHolder(String residenceName, Mode mode, String direction) implements ManagedHolder {
    }

    private record DeleteConfirmHolder(String residenceName) implements ManagedHolder {
    }

    private record PendingCreation(UUID playerUuid, String displayName, String worldName, ChunkBounds bounds, double price) {
    }

    public enum Mode {
        EXPAND,
        CONTRACT
    }
}
