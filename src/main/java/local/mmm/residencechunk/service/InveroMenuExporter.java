package local.mmm.residencechunk.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import local.mmm.residencechunk.MMMResidenceChunkBridgePlugin;
import org.bukkit.plugin.Plugin;

public final class InveroMenuExporter {

    private InveroMenuExporter() {
    }

    public static void exportIfAvailable(MMMResidenceChunkBridgePlugin plugin) {
        Plugin invero = plugin.getServer().getPluginManager().getPlugin("Invero");
        if (invero == null) {
            return;
        }

        File workspace = new File(invero.getDataFolder(), "workspace");
        if (!workspace.exists() && !workspace.mkdirs()) {
            plugin.getLogger().warning("Unable to create Invero workspace directory for menu export.");
            return;
        }

        File output = new File(workspace, "MMM领地入口.yml");
        try (InputStream input = plugin.getResource("invero/MMM领地入口.yml")) {
            if (input == null) {
                plugin.getLogger().warning("Embedded Invero menu template not found.");
                return;
            }
            Files.copy(input, output.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("Exported Invero menu entry to " + output.getAbsolutePath());
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to export Invero menu entry: " + exception.getMessage());
        }
    }
}
