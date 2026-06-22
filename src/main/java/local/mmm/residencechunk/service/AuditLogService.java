package local.mmm.residencechunk.service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import local.mmm.residencechunk.MMMResidenceChunkBridgePlugin;
import org.bukkit.entity.Player;

public final class AuditLogService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MMMResidenceChunkBridgePlugin plugin;
    private final File file;

    public AuditLogService(MMMResidenceChunkBridgePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "operations.log");
    }

    public void log(Player player, String action, String detail) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        String line = "[" + FORMATTER.format(LocalDateTime.now()) + "] "
            + action
            + " player=" + player.getName()
            + " uuid=" + player.getUniqueId()
            + " " + detail
            + System.lineSeparator();
        try (FileWriter writer = new FileWriter(file, true)) {
            writer.write(line);
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to write operation log: " + exception.getMessage());
        }
    }
}
