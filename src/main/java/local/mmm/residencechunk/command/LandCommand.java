package local.mmm.residencechunk.command;

import java.util.ArrayList;
import java.util.List;
import local.mmm.residencechunk.MMMResidenceChunkBridgePlugin;
import local.mmm.residencechunk.service.GuiService;
import local.mmm.residencechunk.service.LandService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class LandCommand implements CommandExecutor, TabCompleter {

    private final MMMResidenceChunkBridgePlugin plugin;
    private final LandService landService;
    private final GuiService guiService;

    public LandCommand(MMMResidenceChunkBridgePlugin plugin, LandService landService, GuiService guiService) {
        this.plugin = plugin;
        this.landService = landService;
        this.guiService = guiService;
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
            suggestions.add("help");
            return filter(suggestions, args[0]);
        }
        if (args.length == 2 && ("expand".equalsIgnoreCase(args[0]) || "contract".equalsIgnoreCase(args[0]) || "delete".equalsIgnoreCase(args[0]) || "rename".equalsIgnoreCase(args[0]))) {
            return filter(landService.ownedClaimNames(player), args[1]);
        }
        if (args.length == 3 && ("expand".equalsIgnoreCase(args[0]) || "contract".equalsIgnoreCase(args[0]))) {
            suggestions.add("north");
            suggestions.add("south");
            suggestions.add("east");
            suggestions.add("west");
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
}
