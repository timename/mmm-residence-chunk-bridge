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

public final class GuiService implements Listener {

    private static final int[] AMOUNTS = {1, 2, 4, 8};
    private static final int[] CONTENT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    private final MMMResidenceChunkBridgePlugin plugin;
    private final LandService landService;
    private final VisualService visualService;
    private final NamespacedKey claimKey;
    private final NamespacedKey actionKey;
    private final Map<UUID, PendingCreation> pendingCreations = new ConcurrentHashMap<>();
    private final Map<UUID, PendingTransform> pendingTransforms = new ConcurrentHashMap<>();
    private final Map<UUID, PendingMemberAction> pendingMemberActions = new ConcurrentHashMap<>();

    public GuiService(MMMResidenceChunkBridgePlugin plugin, LandService landService, VisualService visualService) {
        this.plugin = plugin;
        this.landService = landService;
        this.visualService = visualService;
        this.claimKey = new NamespacedKey(plugin, "claim_name");
        this.actionKey = new NamespacedKey(plugin, "claim_action");
    }

    public void openMainMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(new MainMenuHolder(), 54,
            plugin.message("gui.main-title"));
        fillBackground(inventory);
        inventory.setItem(20, createItem(Material.GRASS_BLOCK, "&a创建当前区块领地",
            "&7在你当前所在区块创建整列领地",
            "&7下一块领地价格: &e" + formatPrice(landService.previewCreatePrice(player)) + " " + plugin.settings().currencyDisplayName(),
            "&b点击后关闭菜单并显示高亮边界",
            "&b随后在聊天栏输入“确认”完成创建"));
        inventory.setItem(22, createItem(Material.GOLDEN_SHOVEL, "&a可视化选区圈地",
            "&7手持配置工具后选择区块",
            "&7左键方块选择起点区块",
            "&7右键方块选择终点区块",
            "&b选好后输入“确认”完成创建"));
        inventory.setItem(24, createItem(Material.MAP, "&b我的领地",
            "&7查看、预览、扩张、缩小和删除领地",
            "&7点击打开领地列表"));
        inventory.setItem(30, createItem(Material.GOLD_INGOT, "&6价格说明",
            "&7扩建首块: &e" + formatPrice(landService.getExpandPricePerChunk()) + " " + plugin.settings().currencyDisplayName(),
            "&7每块递增: &e" + formatPrice(landService.getExpandPriceIncreasePerChunk()),
            "&7领地超过 &e" + plugin.settings().expandVaultMaxChunks()
                + " 个区块 &7后使用: &e" + landService.getCustomExpandCurrencyDisplayName(),
            "&7多区块创建会按额外区块加价",
            "&7缩小不会返还货币"));
        inventory.setItem(32, createItem(Material.REDSTONE, "&e取消当前选区",
            "&7退出正在进行的可视化圈地"));
        inventory.setItem(49, createItem(Material.BARRIER, "&c关闭菜单"));
        player.openInventory(inventory);
    }

    public void openClaimsMenu(Player player) {
        List<ManagedClaim> claims = landService.getClaims(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(new ClaimsListHolder(), 54,
            plugin.message("gui.list-title"));
        fillBackground(inventory);
        int index = 0;
        for (ManagedClaim claim : claims) {
            if (index >= CONTENT_SLOTS.length) {
                break;
            }
            ItemStack item = createItem(Material.OAK_SIGN, "&e" + claim.displayName(),
                "&7内部名: &f" + claim.residenceName(),
                "&7世界: &f" + claim.worldName(),
                "&7区块: &f" + claim.bounds().minChunkX() + "," + claim.bounds().minChunkZ()
                    + " -> " + claim.bounds().maxChunkX() + "," + claim.bounds().maxChunkZ(),
                "&7面积: &f" + claim.bounds().area() + " 个区块",
                "&7点击进入操作菜单");
            item.editMeta(meta -> meta.getPersistentDataContainer().set(claimKey, PersistentDataType.STRING, claim.residenceName()));
            inventory.setItem(CONTENT_SLOTS[index++], item);
        }
        inventory.setItem(49, createItem(Material.BARRIER, "&c返回主菜单"));
        player.openInventory(inventory);
    }

    public void openClaimDetailMenu(Player player, ManagedClaim claim) {
        Inventory inventory = Bukkit.createInventory(new ClaimDetailHolder(claim.residenceName()), 54,
            plugin.message("gui.detail-title"));
        fillBackground(inventory);
        inventory.setItem(13, createItem(Material.BOOK, "&e" + claim.displayName(),
            "&7内部名: &f" + claim.residenceName(),
            "&7世界: &f" + claim.worldName(),
            "&7区块面积: &f" + claim.bounds().area()));

        inventory.setItem(19, actionItem(Material.ENDER_PEARL, "&a传送到领地", "teleport::" + claim.displayName(),
            "&7传送到该领地的 Residence 传送点"));
        inventory.setItem(21, actionItem(Material.COMPASS, "&e设置传送点", "set_teleport::" + claim.displayName(),
            "&7必须站在该领地范围内",
            "&7会把当前位置设为领地传送点"));

        inventory.setItem(28, actionItem(Material.ENDER_EYE, "&b预览边界", "preview::" + claim.displayName()));
        inventory.setItem(29, actionItem(Material.GOLDEN_SHOVEL, "&a工具调整边界", "resize_select::" + claim.displayName(),
            "&7使用圈地工具重新选择矩形边界",
            "&7新增区块会按扩建规则收费"));
        inventory.setItem(30, actionItem(Material.LIME_CONCRETE, "&a扩张领地", "open_expand::" + claim.displayName()));
        inventory.setItem(32, actionItem(Material.ORANGE_CONCRETE, "&6缩小领地", "open_contract::" + claim.displayName()));
        inventory.setItem(33, actionItem(Material.PLAYER_HEAD, "&d成员权限", "open_members::" + claim.displayName(),
            "&7信任玩家、取消信任",
            "&7禁止进入、解除禁止"));

        inventory.setItem(39, actionItem(Material.NAME_TAG, "&b重命名提示",
            "noop::" + claim.displayName(),
            "&7请使用命令:",
            "&e/mmmland rename " + claim.displayName() + " 新名字"));
        inventory.setItem(41, actionItem(Material.LAVA_BUCKET, "&4删除领地", "open_delete::" + claim.displayName(),
            "&c此操作会删除托管记录和 Residence 领地"));
        inventory.setItem(49, createItem(Material.BARRIER, "&c返回列表"));
        player.openInventory(inventory);
    }

    public void openMemberMenu(Player player, ManagedClaim claim) {
        Inventory inventory = Bukkit.createInventory(new MemberHolder(claim.residenceName()), 54,
            plugin.message("gui.member-title"));
        fillBackground(inventory);
        inventory.setItem(13, createItem(Material.BOOK, "&d" + claim.displayName(),
            "&7选择操作后，在聊天栏输入玩家名",
            "&7输入“取消”可退出"));
        inventory.setItem(20, actionItem(Material.LIME_DYE, "&a信任玩家", "member:trust:" + claim.displayName(),
            "&7授予建造、使用、开箱、移动、传送权限"));
        inventory.setItem(22, actionItem(Material.GRAY_DYE, "&e取消信任", "member:untrust:" + claim.displayName(),
            "&7移除信任权限模板"));
        inventory.setItem(24, actionItem(Material.REDSTONE, "&c禁止进入", "member:deny:" + claim.displayName(),
            "&7禁止该玩家移动进入和传送进入"));
        inventory.setItem(31, actionItem(Material.GLOWSTONE_DUST, "&b解除禁止", "member:undeny:" + claim.displayName(),
            "&7移除禁止进入相关限制"));
        inventory.setItem(49, createItem(Material.BARRIER, "&c返回上一页"));
        player.openInventory(inventory);
    }

    public void openDirectionMenu(Player player, ManagedClaim claim, Mode mode) {
        Inventory inventory = Bukkit.createInventory(new DirectionHolder(claim.residenceName(), mode), 54,
            plugin.message("gui.direction-title"));
        fillBackground(inventory);
        inventory.setItem(13, createItem(Material.BOOK, mode == Mode.EXPAND ? "&a选择扩张方向" : "&6选择缩小方向",
            "&7当前领地: &f" + claim.displayName()));
        inventory.setItem(20, actionItem(Material.ARROW, "&e向北", "pick:" + mode.name().toLowerCase(Locale.ROOT) + ":北:" + claim.displayName()));
        inventory.setItem(22, actionItem(Material.ARROW, "&e向西", "pick:" + mode.name().toLowerCase(Locale.ROOT) + ":西:" + claim.displayName()));
        inventory.setItem(24, actionItem(Material.ARROW, "&e向东", "pick:" + mode.name().toLowerCase(Locale.ROOT) + ":东:" + claim.displayName()));
        inventory.setItem(31, actionItem(Material.ARROW, "&e向南", "pick:" + mode.name().toLowerCase(Locale.ROOT) + ":南:" + claim.displayName()));
        inventory.setItem(49, createItem(Material.BARRIER, "&c返回上一页"));
        player.openInventory(inventory);
    }

    public void openAmountMenu(Player player, ManagedClaim claim, Mode mode, String direction) {
        Inventory inventory = Bukkit.createInventory(new AmountHolder(claim.residenceName(), mode, direction), 54,
            plugin.message("gui.amount-title"));
        fillBackground(inventory);
        inventory.setItem(13, createItem(Material.BOOK, mode == Mode.EXPAND ? "&a选择扩张区块数" : "&6选择缩小区块数",
            "&7方向: &f" + direction,
            mode == Mode.EXPAND
                ? "&7扩建后超过 " + plugin.settings().expandVaultMaxChunks() + " 个区块切换扩建货币"
                : "&7缩小不会返还货币"));
        for (int i = 0; i < AMOUNTS.length; i++) {
            int amount = AMOUNTS[i];
            Material material = mode == Mode.EXPAND ? Material.GREEN_STAINED_GLASS : Material.ORANGE_STAINED_GLASS;
            LandService.ExpandCost cost = landService.previewExpandCost(claim, direction, amount);
            String lore = mode == Mode.EXPAND
                ? "&7预计费用: &e" + formatPrice(cost.price()) + " " + cost.currencyDisplayName()
                : "&7本操作不会返还货币";
            inventory.setItem(20 + (i * 2), actionItem(material, "&f" + amount + " 个区块",
                "apply:" + mode.name().toLowerCase(Locale.ROOT) + ":" + direction + ":" + claim.displayName() + ":" + amount, lore));
        }
        inventory.setItem(49, createItem(Material.BARRIER, "&c返回上一页"));
        player.openInventory(inventory);
    }

    public void openDeleteConfirmMenu(Player player, ManagedClaim claim) {
        Inventory inventory = Bukkit.createInventory(new DeleteConfirmHolder(claim.residenceName()), 54,
            plugin.message("gui.delete-confirm-title"));
        fillBackground(inventory);
        inventory.setItem(21, actionItem(Material.RED_CONCRETE, "&4确认删除",
            "delete::" + claim.displayName(),
            "&7领地: &f" + claim.displayName(),
            "&c此操作不可恢复"));
        inventory.setItem(23, createItem(Material.GREEN_CONCRETE, "&a取消并返回"));
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
            if (event.getRawSlot() == 49) {
                openClaimsMenu(player);
                return;
            }
            routeAction(player, event.getCurrentItem(), detailHolder.residenceName());
            return;
        }
        if (holder instanceof DirectionHolder directionHolder) {
            if (event.getRawSlot() == 49) {
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
            if (event.getRawSlot() == 49) {
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
            if (event.getRawSlot() == 23) {
                openClaimDetailMenu(player, claim);
                return;
            }
            routeAction(player, event.getCurrentItem(), deleteHolder.residenceName());
        }
        if (holder instanceof MemberHolder memberHolder) {
            if (event.getRawSlot() == 49) {
                ManagedClaim claim = landService.resolveOwnedClaim(player, memberHolder.residenceName());
                if (claim != null) {
                    openClaimDetailMenu(player, claim);
                }
                return;
            }
            routeAction(player, event.getCurrentItem(), memberHolder.residenceName());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPendingChat(AsyncPlayerChatEvent event) {
        PendingCreation pending = pendingCreations.get(event.getPlayer().getUniqueId());
        PendingTransform transform = pendingTransforms.get(event.getPlayer().getUniqueId());
        PendingMemberAction memberAction = pendingMemberActions.get(event.getPlayer().getUniqueId());
        if (pending == null && transform == null && memberAction == null) {
            return;
        }

        event.setCancelled(true);
        String message = event.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (pendingCreations.containsKey(event.getPlayer().getUniqueId())) {
                handlePendingCreateChat(event.getPlayer(), message);
            } else if (pendingTransforms.containsKey(event.getPlayer().getUniqueId())) {
                handlePendingTransformChat(event.getPlayer(), message);
            } else {
                handlePendingMemberChat(event.getPlayer(), message);
            }
        });
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
        cancelPendingTransform(event.getPlayer(), null);
        pendingMemberActions.remove(event.getPlayer().getUniqueId());
    }

    private void handleMainMenuClick(Player player, int rawSlot) {
        switch (rawSlot) {
            case 20 -> startCreateConfirmation(player);
            case 22 -> player.performCommand("mmmland select");
            case 24 -> openClaimsMenu(player);
            case 32 -> player.performCommand("mmmland cancel");
            case 49 -> player.closeInventory();
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
            case "open_members" -> {
                ManagedClaim claim = landService.resolveOwnedClaim(player, fallbackResidenceName);
                if (claim == null) {
                    return;
                }
                openMemberMenu(player, claim);
            }
            case "resize_select" -> {
                player.performCommand("mmmland resize " + fallbackResidenceName);
            }
            case "teleport" -> {
                player.closeInventory();
                player.performCommand("mmmland tp " + fallbackResidenceName);
            }
            case "set_teleport" -> {
                player.closeInventory();
                player.performCommand("mmmland sethome " + fallbackResidenceName);
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
                    startTransformConfirmation(player, previewClaim, mode, direction, amount);
                }
            }
            case "delete" -> {
                if (parts.length < 3) {
                    return;
                }
                String claimName = parts[2];
                landService.deleteClaim(player, claimName);
                Bukkit.getScheduler().runTask(plugin, () -> openClaimsMenu(player));
            }
            case "member" -> {
                if (parts.length < 3) {
                    return;
                }
                startMemberAction(player, parts[2], MemberAction.fromKey(parts[1]));
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
        cancelPendingTransform(player, null);
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
        landService.createClaim(player, pending.displayName(), pending.bounds());
    }

    private void cancelPendingCreation(Player player, String messagePath) {
        PendingCreation removed = pendingCreations.remove(player.getUniqueId());
        if (removed != null && messagePath != null) {
            player.sendMessage(plugin.message(messagePath));
        }
    }

    private void startTransformConfirmation(Player player, ManagedClaim claim, Mode mode, String direction, String amount) {
        cancelPendingCreation(player, null);
        cancelPendingTransform(player, null);
        int parsedAmount = Integer.parseInt(amount);
        ChunkBounds newBounds = landService.previewBounds(claim, mode == Mode.EXPAND, direction, parsedAmount);
        previewBounds(player, newBounds, claim.worldName());
        pendingTransforms.put(player.getUniqueId(), new PendingTransform(claim.displayName(), mode, direction, amount));
        player.closeInventory();
        int delta = mode == Mode.EXPAND ? newBounds.area() - claim.bounds().area() : claim.bounds().area() - newBounds.area();
        LandService.ExpandCost expandCost = mode == Mode.EXPAND ? landService.previewExpandCost(claim, direction, parsedAmount) : null;
        String messagePath = mode == Mode.EXPAND ? "transform-preview-expand" : "transform-preview-contract";
        player.sendMessage(plugin.message(messagePath)
            .replace("%name%", claim.displayName())
            .replace("%delta%", Integer.toString(delta))
            .replace("%price%", formatPrice(expandCost == null ? delta * landService.getExpandPricePerChunk() : expandCost.price()))
            .replace("%currency%", expandCost == null ? plugin.settings().currencyDisplayName() : expandCost.currencyDisplayName()));
        player.sendMessage(plugin.message("transform-confirm-instruction"));
    }

    private void handlePendingTransformChat(Player player, String message) {
        PendingTransform pending = pendingTransforms.get(player.getUniqueId());
        if (pending == null) {
            return;
        }
        if ("取消".equalsIgnoreCase(message) || "cancel".equalsIgnoreCase(message)) {
            cancelPendingTransform(player, "transform-cancelled");
            return;
        }
        if (!"确认".equalsIgnoreCase(message) && !"confirm".equalsIgnoreCase(message)) {
            player.sendMessage(plugin.message("create-confirm-invalid"));
            return;
        }
        pendingTransforms.remove(player.getUniqueId());
        if (pending.mode() == Mode.EXPAND) {
            landService.expandClaim(player, pending.claimName(), pending.direction(), pending.amount());
        } else {
            landService.contractClaim(player, pending.claimName(), pending.direction(), pending.amount());
        }
        ManagedClaim refreshed = landService.resolveOwnedClaim(player, pending.claimName());
        if (refreshed != null) {
            openClaimDetailMenu(player, refreshed);
        } else {
            openClaimsMenu(player);
        }
    }

    private void cancelPendingTransform(Player player, String messagePath) {
        PendingTransform removed = pendingTransforms.remove(player.getUniqueId());
        if (removed != null && messagePath != null) {
            player.sendMessage(plugin.message(messagePath));
        }
    }

    private void startMemberAction(Player player, String claimName, MemberAction action) {
        if (action == null) {
            return;
        }
        ManagedClaim claim = landService.resolveOwnedClaim(player, claimName);
        if (claim == null) {
            return;
        }
        cancelPendingCreation(player, null);
        cancelPendingTransform(player, null);
        pendingMemberActions.put(player.getUniqueId(), new PendingMemberAction(claim.displayName(), action));
        player.closeInventory();
        player.sendMessage(plugin.message("member-input-player")
            .replace("%action%", action.displayName())
            .replace("%name%", claim.displayName()));
    }

    private void handlePendingMemberChat(Player player, String message) {
        PendingMemberAction pending = pendingMemberActions.get(player.getUniqueId());
        if (pending == null) {
            return;
        }
        if ("取消".equalsIgnoreCase(message) || "cancel".equalsIgnoreCase(message)) {
            pendingMemberActions.remove(player.getUniqueId());
            player.sendMessage(plugin.message("member-action-cancelled"));
            return;
        }
        pendingMemberActions.remove(player.getUniqueId());
        switch (pending.action()) {
            case TRUST -> landService.trustPlayer(player, pending.claimName(), message);
            case UNTRUST -> landService.untrustPlayer(player, pending.claimName(), message);
            case DENY -> landService.denyPlayer(player, pending.claimName(), message);
            case UNDENY -> landService.undenyPlayer(player, pending.claimName(), message);
        }
        ManagedClaim refreshed = landService.resolveOwnedClaim(player, pending.claimName());
        if (refreshed != null) {
            openMemberMenu(player, refreshed);
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
        ItemStack border = createPane(Material.BLUE_STAINED_GLASS_PANE);
        ItemStack inner = createPane(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        ItemStack accent = createPane(Material.MAGENTA_STAINED_GLASS_PANE);

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, inner.clone());
        }

        int rows = inventory.getSize() / 9;
        for (int col = 0; col < 9; col++) {
            inventory.setItem(col, border.clone());
            inventory.setItem((rows - 1) * 9 + col, border.clone());
        }
        for (int row = 0; row < rows; row++) {
            inventory.setItem(row * 9, border.clone());
            inventory.setItem(row * 9 + 8, border.clone());
        }

        inventory.setItem(0, accent.clone());
        inventory.setItem(8, accent.clone());
        inventory.setItem((rows - 1) * 9, accent.clone());
        inventory.setItem((rows - 1) * 9 + 8, accent.clone());
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
        int durationTicks = Math.max(20, plugin.getConfig().getInt("visual.preview-duration-ticks", 200));
        visualService.previewForDuration(player, bounds, worldName, Color.AQUA, durationTicks);
    }

    private String formatPrice(double amount) {
        if (Math.floor(amount) == amount) {
            return Long.toString((long) amount);
        }
        return String.format(Locale.US, "%.2f", amount);
    }

    private sealed interface ManagedHolder extends InventoryHolder permits MainMenuHolder, ClaimsListHolder,
        ClaimDetailHolder, DirectionHolder, AmountHolder, DeleteConfirmHolder, MemberHolder {
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

    private record MemberHolder(String residenceName) implements ManagedHolder {
    }

    private record PendingCreation(UUID playerUuid, String displayName, String worldName, ChunkBounds bounds, double price) {
    }

    private record PendingTransform(String claimName, Mode mode, String direction, String amount) {
    }

    private record PendingMemberAction(String claimName, MemberAction action) {
    }

    public enum Mode {
        EXPAND,
        CONTRACT
    }

    private enum MemberAction {
        TRUST("信任玩家"),
        UNTRUST("取消信任"),
        DENY("禁止进入"),
        UNDENY("解除禁止");

        private final String displayName;

        MemberAction(String displayName) {
            this.displayName = displayName;
        }

        private String displayName() {
            return displayName;
        }

        private static MemberAction fromKey(String key) {
            return switch (key.toLowerCase(Locale.ROOT)) {
                case "trust" -> TRUST;
                case "untrust" -> UNTRUST;
                case "deny" -> DENY;
                case "undeny" -> UNDENY;
                default -> null;
            };
        }
    }
}
