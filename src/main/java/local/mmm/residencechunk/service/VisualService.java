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
        taskRef[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                cancel(taskRef[0]);
                return;
            }
            drawBounds(player, bounds, player.getWorld(), color);
        }, 0L, 10L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> cancel(taskRef[0]), durationTicks);
        return taskRef[0];
    }

    public void drawBounds(Player player, ChunkBounds bounds, World world, Color color) {
        int step = Math.max(1, plugin.getConfig().getInt("visual.preview-step-blocks", 2));
        int minX = bounds.minChunkX() << 4;
        int minZ = bounds.minChunkZ() << 4;
        int maxX = (bounds.maxChunkX() << 4) + 15;
        int maxZ = (bounds.maxChunkZ() << 4) + 15;
        double y = Math.max(player.getLocation().getY(), world.getMinHeight() + 1);
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.2f);

        for (int x = minX; x <= maxX; x += step) {
            player.spawnParticle(Particle.DUST, x + 0.5, y, minZ + 0.5, 1, dust);
            player.spawnParticle(Particle.DUST, x + 0.5, y, maxZ + 0.5, 1, dust);
        }
        for (int z = minZ; z <= maxZ; z += step) {
            player.spawnParticle(Particle.DUST, minX + 0.5, y, z + 0.5, 1, dust);
            player.spawnParticle(Particle.DUST, maxX + 0.5, y, z + 0.5, 1, dust);
        }

        for (double cornerY = y; cornerY <= y + 3; cornerY += 1) {
            spawnCorner(player, minX, minZ, cornerY, dust);
            spawnCorner(player, maxX, minZ, cornerY, dust);
            spawnCorner(player, minX, maxZ, cornerY, dust);
            spawnCorner(player, maxX, maxZ, cornerY, dust);
        }
    }

    private void spawnCorner(Player player, int x, int z, double y, Particle.DustOptions dust) {
        player.spawnParticle(Particle.DUST, x + 0.5, y, z + 0.5, 1, dust);
    }

    private void cancel(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }
}
