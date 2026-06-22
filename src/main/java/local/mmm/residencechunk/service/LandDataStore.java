package local.mmm.residencechunk.service;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import local.mmm.residencechunk.model.ChunkBounds;
import local.mmm.residencechunk.model.ManagedClaim;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class LandDataStore {

    private final File file;
    private final Map<String, ManagedClaim> claims = new LinkedHashMap<>();

    public LandDataStore(File file) {
        this.file = file;
    }

    public void load() {
        claims.clear();
        if (!file.exists()) {
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("claims");
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection claimSection = section.getConfigurationSection(key);
            if (claimSection == null) {
                continue;
            }
            ManagedClaim claim = new ManagedClaim(
                key,
                claimSection.getString("display-name", key),
                UUID.fromString(claimSection.getString("owner-uuid")),
                claimSection.getString("owner-name", ""),
                claimSection.getString("world"),
                new ChunkBounds(
                    claimSection.getInt("min-chunk-x"),
                    claimSection.getInt("max-chunk-x"),
                    claimSection.getInt("min-chunk-z"),
                    claimSection.getInt("max-chunk-z")
                )
            );
            claims.put(key.toLowerCase(), claim);
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (ManagedClaim claim : claims.values()) {
            String path = "claims." + claim.residenceName();
            yaml.set(path + ".display-name", claim.displayName());
            yaml.set(path + ".owner-uuid", claim.ownerUuid().toString());
            yaml.set(path + ".owner-name", claim.ownerName());
            yaml.set(path + ".world", claim.worldName());
            yaml.set(path + ".min-chunk-x", claim.bounds().minChunkX());
            yaml.set(path + ".max-chunk-x", claim.bounds().maxChunkX());
            yaml.set(path + ".min-chunk-z", claim.bounds().minChunkZ());
            yaml.set(path + ".max-chunk-z", claim.bounds().maxChunkZ());
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            yaml.save(file);
        } catch (IOException exception) {
            throw new RuntimeException("Failed to save " + file.getName(), exception);
        }
    }

    public Collection<ManagedClaim> allClaims() {
        return Collections.unmodifiableCollection(claims.values());
    }

    public ManagedClaim find(String residenceName) {
        return claims.get(residenceName.toLowerCase());
    }

    public List<ManagedClaim> findOwnedBy(UUID ownerUuid) {
        return claims.values().stream()
            .filter(claim -> claim.ownerUuid().equals(ownerUuid))
            .toList();
    }

    public void put(ManagedClaim claim) {
        claims.put(claim.residenceName().toLowerCase(), claim);
    }

    public void remove(String residenceName) {
        claims.remove(residenceName.toLowerCase());
    }
}
