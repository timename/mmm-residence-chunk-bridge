package local.mmm.residencechunk.command;

import java.util.ArrayList;
import java.util.List;
import local.mmm.residencechunk.MMMResidenceChunkBridgePlugin;
import local.mmm.residencechunk.service.GuiService;
import local.mmm.residencechunk.service.LandService;
import local.mmm.residencechunk.service.SelectionService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class LandCommand implements CommandExecutor, TabCompleter {

    private final MMMResidenceChunkBridgePlugin plugin;
    private final LandService landService;
    private final GuiService guiService;
    private final SelectionService selectionService;

    public LandCommand(MMMResidenceChunkBridgePlugin plugin, LandService landService, GuiService guiService, SelectionService selectionService) {
        this.plugin = plugin;
        this.landService = landService;
        this.guiService = guiService;
        this.selectionService = selectionService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("player-only"));
            return true;
        }
        if (!player.hasPermission("mmmland.use")) {
            player.sendMessage(plugin.message("no-permission"));
            return true;
        }
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            landService.sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> {
                String displayName = args.length >= 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : null;
                landService.createClaim(player, displayName);
                return true;
            }
            case "list" -> {
                landService.listClaims(player);
                return true;
            }
            case "rename" -> {
                if (args.length < 3) {
                    player.sendMessage(plugin.message("rename-usage"));
                    return true;
                }
                String oldName = args[1];
                String newName = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
                landService.renameClaim(player, oldName, newName);
                return true;
            }
            case "menu" -> {
                guiService.openMainMenu(player);
                return true;
            }
            case "select" -> {
                String displayName = args.length >= 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : null;
                selectionService.startSelection(player, displayName);
                return true;
            }
            case "confirm" -> {
                selectionService.confirmSelection(player);
                return true;
            }
            case "cancel" -> {
                selectionService.cancelSelection(player, "select-cancelled");
                return true;
            }
            case "reload" -> {
                if (!player.hasPermission("mmmland.admin")) {
                    player.sendMessage(plugin.message("no-permission"));
                    return true;
                }
                plugin.reloadPluginConfig();
                player.sendMessage(plugin.message("reload-success"));
                return true;
            }
            case "admin" -> {
                if (!player.hasPermission("mmmland.admin")) {
                    player.sendMessage(plugin.message("no-permission"));
                    return true;
                }
                handleAdmin(player, args);
                return true;
            }
            case "expand" -> {
                if (args.length < 4) {
                    player.sendMessage(plugin.message("expand-usage"));
                    return true;
                }
                landService.expandClaim(player, args[1], args[2], args[3]);
                return true;
            }
            case "contract" -> {
                if (args.length < 4) {
                    player.sendMessage(plugin.message("contract-usage"));
                    return true;
                }
                landService.contractClaim(player, args[1], args[2], args[3]);
                return true;
            }
            case "delete" -> {
                if (args.length < 2) {
                    player.sendMessage(plugin.message("residence-missing"));
                    return true;
                }
                landService.deleteClaim(player, args[1]);
                return true;
            }
            default -> {
                landService.sendHelp(player);
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (!(sender instanceof Player player)) {
            return suggestions;
        }
        if (args.length == 1) {
            suggestions.add("create");
            suggestions.add("list");
            suggestions.add("rename");
            suggestions.add("expand");
            suggestions.add("contract");
            suggestions.add("delete");
            suggestions.add("menu");
            suggestions.add("select");
            suggestions.add("confirm");
            suggestions.add("cancel");
            suggestions.add("reload");
            suggestions.add("admin");
            suggestions.add("help");
            return filter(suggestions, args[0]);
        }
        if (args.length == 2 && "admin".equalsIgnoreCase(args[0])) {
            suggestions.add("list");
            suggestions.add("check");
            suggestions.add("clean");
            suggestions.add("create");
            suggestions.add("expand");
            suggestions.add("contract");
            suggestions.add("delete");
            return filter(suggestions, args[1]);
        }
        if (args.length == 3 && "admin".equalsIgnoreCase(args[0]) && ("create".equalsIgnoreCase(args[1]) || "expand".equalsIgnoreCase(args[1])
            || "contract".equalsIgnoreCase(args[1]) || "delete".equalsIgnoreCase(args[1]))) {
            return filter(org.bukkit.Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
        }
        if (args.length == 4 && "admin".equalsIgnoreCase(args[0]) && ("expand".equalsIgnoreCase(args[1]) || "contract".equalsIgnoreCase(args[1])
            || "delete".equalsIgnoreCase(args[1]))) {
            return filter(landService.ownedClaimNames(args[2]), args[3]);
        }
        if (args.length == 5 && "admin".equalsIgnoreCase(args[0]) && ("expand".equalsIgnoreCase(args[1]) || "contract".equalsIgnoreCase(args[1]))) {
            suggestions.add("北");
            suggestions.add("南");
            suggestions.add("东");
            suggestions.add("西");
            return filter(suggestions, args[4]);
        }
        if (args.length == 6 && "admin".equalsIgnoreCase(args[0]) && ("expand".equalsIgnoreCase(args[1]) || "contract".equalsIgnoreCase(args[1]))) {
            suggestions.add("1");
            suggestions.add("2");
            suggestions.add("3");
            suggestions.add("4");
            return filter(suggestions, args[5]);
        }
        if (args.length == 3 && "admin".equalsIgnoreCase(args[0]) && "forcedelete".equalsIgnoreCase(args[1])) {
            return filter(landService.getAllClaims().stream().map(claim -> claim.residenceName()).toList(), args[2]);
        }
        if (args.length == 2 && ("expand".equalsIgnoreCase(args[0]) || "contract".equalsIgnoreCase(args[0]) || "delete".equalsIgnoreCase(args[0]) || "rename".equalsIgnoreCase(args[0]))) {
            return filter(landService.ownedClaimNames(player), args[1]);
        }
        if (args.length == 3 && ("expand".equalsIgnoreCase(args[0]) || "contract".equalsIgnoreCase(args[0]))) {
            suggestions.add("北");
            suggestions.add("南");
            suggestions.add("东");
            suggestions.add("西");
            return filter(suggestions, args[2]);
        }
        if (args.length == 4 && ("expand".equalsIgnoreCase(args[0]) || "contract".equalsIgnoreCase(args[0]))) {
            suggestions.add("1");
            suggestions.add("2");
            suggestions.add("3");
            suggestions.add("4");
            return filter(suggestions, args[3]);
        }
        return suggestions;
    }

    private List<String> filter(List<String> input, String prefix) {
        String lowered = prefix.toLowerCase();
        return input.stream()
            .filter(entry -> entry.toLowerCase().startsWith(lowered))
            .toList();
    }

    private void handleAdmin(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.message("admin-usage"));
            return;
        }
        switch (args[1].toLowerCase()) {
            case "list" -> landService.adminListClaims(player);
            case "check" -> landService.adminCheckClaims(player);
            case "clean" -> landService.adminCleanClaims(player);
            case "create" -> {
                if (args.length < 3) {
                    player.sendMessage(plugin.message("admin-create-usage"));
                    return;
                }
                landService.adminCreateClaim(player, args[2], adminCreateDisplayName(args));
            }
            case "expand" -> {
                if (args.length < 6) {
                    player.sendMessage(plugin.message("admin-expand-usage"));
                    return;
                }
                landService.adminExpandClaim(player, args[2], args[3], args[4], args[5]);
            }
            case "contract" -> {
                if (args.length < 6) {
                    player.sendMessage(plugin.message("admin-contract-usage"));
                    return;
                }
                landService.adminContractClaim(player, args[2], args[3], args[4], args[5]);
            }
            case "delete" -> {
                if (args.length < 3) {
                    player.sendMessage(plugin.message("admin-delete-usage"));
                    return;
                }
                if (args.length >= 4) {
                    landService.adminDeletePlayerClaim(player, args[2], args[3]);
                } else {
                    landService.adminDeleteClaim(player, args[2]);
                }
            }
            case "forcedelete" -> {
                if (args.length < 3) {
                    player.sendMessage(plugin.message("admin-force-delete-usage"));
                    return;
                }
                landService.adminDeleteClaim(player, args[2]);
            }
            default -> player.sendMessage(plugin.message("admin-usage"));
        }
    }

    private String adminCreateDisplayName(String[] args) {
        return args.length >= 4 ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)) : null;
    }
}
