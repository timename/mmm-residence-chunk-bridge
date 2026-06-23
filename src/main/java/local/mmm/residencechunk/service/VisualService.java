package local.mmm.residencechunk.service;

import local.mmm.residencechunk.MMMResidenceChunkBridgePlugin;
import local.mmm.residencechunk.model.ChunkBounds;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class VisualService {

    private final MMMResidenceChunkBridgePlugin plugin;

    public VisualService(MMMResidenceChunkBridgePlugin plugin) {
        this.plugin = plugin;
    }

    public BukkitTask previewForDuration(Player player, ChunkBounds bounds, String worldName, Color color, long durationTicks) {
        if (!player.getWorld().getName().equals(worldName)) {
            return null;
        }
        BukkitTask[] taskRef = new BukkitTask[1];
        long periodTicks = Math.max(2L, plugin.pluginConfig().getLong("visual.preview-period-ticks", 6L));
        taskRef[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                cancel(taskRef[0]);
                return;
            }
            drawBounds(player, bounds, player.getWorld(), color);
        }, 0L, periodTicks);
        Bukkit.getScheduler().runTaskLater(plugin, () -> cancel(taskRef[0]), durationTicks);
        return taskRef[0];
    }

    public void drawBounds(Player player, ChunkBounds bounds, World world, Color color) {
        drawBounds(player, bounds, world, color, "visual.selection");
    }

    public void drawCurrentChunk(Player player) {
        drawBounds(player, ChunkBounds.single(player.getChunk()), player.getWorld(), configuredColor("visual.current-chunk.color", Color.YELLOW), "visual.current-chunk");
    }

    private void drawBounds(Player player, ChunkBounds bounds, World world, Color color, String configPath) {
        int step = Math.max(1, plugin.pluginConfig().getInt(configPath + ".step-blocks",
            plugin.pluginConfig().getInt("visual.preview-step-blocks", 1)));
        int cornerHeight = Math.max(1, plugin.pluginConfig().getInt(configPath + ".corner-height",
            plugin.pluginConfig().getInt("visual.preview-corner-height", 8)));
        float dustSize = (float) Math.max(0.2D, plugin.pluginConfig().getDouble(configPath + ".dust-size",
            plugin.pluginConfig().getDouble("visual.preview-dust-size", 1.6D)));
        boolean accentEnabled = plugin.pluginConfig().getBoolean(configPath + ".accent-enabled", true);
        Particle accentParticle = configuredParticle(configPath + ".accent-particle", Particle.END_ROD);
        int minX = bounds.minChunkX() << 4;
        int minZ = bounds.minChunkZ() << 4;
        int maxX = (bounds.maxChunkX() << 4) + 15;
        int maxZ = (bounds.maxChunkZ() << 4) + 15;
        double y = Math.max(player.getLocation().getY(), world.getMinHeight() + 1);
        Particle.DustOptions dust = new Particle.DustOptions(color, dustSize);

        for (int x = minX; x <= maxX; x += step) {
            player.spawnParticle(Particle.DUST, x + 0.5, y, minZ + 0.5, 1, dust);
            player.spawnParticle(Particle.DUST, x + 0.5, y, maxZ + 0.5, 1, dust);
            spawnAccent(player, accentEnabled, accentParticle, x + 0.5, y + 0.2, minZ + 0.5);
            spawnAccent(player, accentEnabled, accentParticle, x + 0.5, y + 0.2, maxZ + 0.5);
        }
        for (int z = minZ; z <= maxZ; z += step) {
            player.spawnParticle(Particle.DUST, minX + 0.5, y, z + 0.5, 1, dust);
            player.spawnParticle(Particle.DUST, maxX + 0.5, y, z + 0.5, 1, dust);
            spawnAccent(player, accentEnabled, accentParticle, minX + 0.5, y + 0.2, z + 0.5);
            spawnAccent(player, accentEnabled, accentParticle, maxX + 0.5, y + 0.2, z + 0.5);
        }

        for (double cornerY = y; cornerY <= y + cornerHeight; cornerY += 1) {
            spawnCorner(player, minX, minZ, cornerY, dust, accentEnabled, accentParticle);
            spawnCorner(player, maxX, minZ, cornerY, dust, accentEnabled, accentParticle);
            spawnCorner(player, minX, maxZ, cornerY, dust, accentEnabled, accentParticle);
            spawnCorner(player, maxX, maxZ, cornerY, dust, accentEnabled, accentParticle);
        }
    }

    private void spawnCorner(Player player, int x, int z, double y, Particle.DustOptions dust, boolean accentEnabled, Particle accentParticle) {
        player.spawnParticle(Particle.DUST, x + 0.5, y, z + 0.5, 1, dust);
        spawnAccent(player, accentEnabled, accentParticle, x + 0.5, y, z + 0.5);
    }

    private void spawnAccent(Player player, boolean enabled, Particle particle, double x, double y, double z) {
        if (enabled) {
            player.spawnParticle(particle, x, y, z, 1, 0, 0, 0, 0);
        }
    }

    private Color configuredColor(String path, Color fallback) {
        String raw = plugin.pluginConfig().getString(path, null);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String[] parts = raw.split(",");
        if (parts.length != 3) {
            return fallback;
        }
        try {
            return Color.fromRGB(
                clampColor(Integer.parseInt(parts[0].trim())),
                clampColor(Integer.parseInt(parts[1].trim())),
                clampColor(Integer.parseInt(parts[2].trim()))
            );
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private Particle configuredParticle(String path, Particle fallback) {
        String raw = plugin.pluginConfig().getString(path, fallback.name());
        try {
            return Particle.valueOf(raw.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private void cancel(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }
}
