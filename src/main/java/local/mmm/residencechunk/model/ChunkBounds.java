package local.mmm.residencechunk.model;

import java.util.Locale;
import org.bukkit.Chunk;

public record ChunkBounds(int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {

    public static ChunkBounds single(Chunk chunk) {
        return new ChunkBounds(chunk.getX(), chunk.getX(), chunk.getZ(), chunk.getZ());
    }

    public int width() {
        return (maxChunkX - minChunkX) + 1;
    }

    public int depth() {
        return (maxChunkZ - minChunkZ) + 1;
    }

    public int area() {
        return width() * depth();
    }

    public boolean isValidRectangle() {
        return minChunkX <= maxChunkX && minChunkZ <= maxChunkZ && width() > 0 && depth() > 0;
    }

    public ChunkBounds expand(Direction direction, int amount) {
        return switch (direction) {
            case NORTH -> new ChunkBounds(minChunkX, maxChunkX, minChunkZ - amount, maxChunkZ);
            case SOUTH -> new ChunkBounds(minChunkX, maxChunkX, minChunkZ, maxChunkZ + amount);
            case EAST -> new ChunkBounds(minChunkX, maxChunkX + amount, minChunkZ, maxChunkZ);
            case WEST -> new ChunkBounds(minChunkX - amount, maxChunkX, minChunkZ, maxChunkZ);
        };
    }

    public ChunkBounds contract(Direction direction, int amount) {
        return switch (direction) {
            case NORTH -> new ChunkBounds(minChunkX, maxChunkX, minChunkZ + amount, maxChunkZ);
            case SOUTH -> new ChunkBounds(minChunkX, maxChunkX, minChunkZ, maxChunkZ - amount);
            case EAST -> new ChunkBounds(minChunkX, maxChunkX - amount, minChunkZ, maxChunkZ);
            case WEST -> new ChunkBounds(minChunkX + amount, maxChunkX, minChunkZ, maxChunkZ);
        };
    }

    public enum Direction {
        NORTH,
        SOUTH,
        EAST,
        WEST;

        public static Direction parse(String input) {
            return switch (input.toLowerCase(Locale.ROOT)) {
                case "north", "n", "北", "向北" -> NORTH;
                case "south", "s", "南", "向南" -> SOUTH;
                case "east", "e", "东", "向东" -> EAST;
                case "west", "w", "西", "向西" -> WEST;
                default -> null;
            };
        }
    }
}
