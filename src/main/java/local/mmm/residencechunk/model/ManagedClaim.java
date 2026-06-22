package local.mmm.residencechunk.model;

import java.util.UUID;

public record ManagedClaim(
    String residenceName,
    String displayName,
    UUID ownerUuid,
    String ownerName,
    String worldName,
    ChunkBounds bounds,
    boolean publicTeleport
) {
}
