package local.mmm.residencechunk.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public record PluginSettings(
    Set<String> allowedWorlds,
    String currencyDisplayName,
    String internalNamePrefix,
    boolean fullHeight,
    int minChunks,
    int noClaimRadiusBlocks,
    int protectedCenterX,
    int protectedCenterZ,
    int defaultMaxClaims,
    Map<String, Integer> permissionMaxClaims,
    Map<Integer, Double> createTiers,
    boolean fallbackLastTier,
    double expandPricePerChunk,
    boolean contractRefundEnabled
) {

    public static PluginSettings fromConfig(FileConfiguration config) {
        Set<String> allowedWorlds = new HashSet<>(config.getStringList("allowed-worlds"));
        String currencyDisplayName = config.getString("currency.display-name", "货币");
        String internalNamePrefix = config.getString("claims.internal-name-prefix", "chunk");
        boolean fullHeight = config.getBoolean("claims.full-height", true);
        int minChunks = Math.max(1, config.getInt("claims.min-chunks", 1));
        int noClaimRadiusBlocks = Math.max(0, config.getInt("claims.no-claim-radius-blocks", 0));
        int protectedCenterX = config.getInt("claims.protected-center-x", 0);
        int protectedCenterZ = config.getInt("claims.protected-center-z", 0);
        int defaultMaxClaims = Math.max(0, config.getInt("limits.default-max-claims", 4));

        Map<String, Integer> permissionMaxClaims = new HashMap<>();
        ConfigurationSection limitSection = config.getConfigurationSection("limits.permission-max-claims");
        if (limitSection != null) {
            for (String key : limitSection.getKeys(false)) {
                permissionMaxClaims.put(key, Math.max(0, limitSection.getInt(key)));
            }
        }

        Map<Integer, Double> createTiers = new HashMap<>();
        ConfigurationSection tierSection = config.getConfigurationSection("pricing.create.tiers");
        if (tierSection != null) {
            for (String key : tierSection.getKeys(false)) {
                try {
                    int tierIndex = Integer.parseInt(key);
                    createTiers.put(tierIndex, Math.max(0D, tierSection.getDouble(key)));
                } catch (NumberFormatException ignored) {
                    // Ignore invalid keys.
                }
            }
        }

        boolean fallbackLastTier = config.getBoolean("pricing.create.fallback-last-tier", true);
        double expandPricePerChunk = Math.max(0D, config.getDouble("pricing.expand.price-per-chunk", 0D));
        boolean contractRefundEnabled = config.getBoolean("pricing.contract.refund-enabled", false);

        return new PluginSettings(
            Collections.unmodifiableSet(allowedWorlds),
            currencyDisplayName,
            internalNamePrefix,
            fullHeight,
            minChunks,
            noClaimRadiusBlocks,
            protectedCenterX,
            protectedCenterZ,
            defaultMaxClaims,
            Collections.unmodifiableMap(permissionMaxClaims),
            Collections.unmodifiableMap(createTiers),
            fallbackLastTier,
            expandPricePerChunk,
            contractRefundEnabled
        );
    }
}
