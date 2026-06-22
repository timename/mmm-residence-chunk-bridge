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
    int maxChunksPerClaim,
    boolean rectangularOnly,
    int noClaimRadiusBlocks,
    int protectedCenterX,
    int protectedCenterZ,
    boolean worldClaimRulesAdminBypass,
    Map<String, WorldClaimRule> worldClaimRules,
    int defaultMaxClaims,
    Map<String, Integer> permissionMaxClaims,
    Map<Integer, Double> createTiers,
    boolean fallbackLastTier,
    double createPricePerExtraChunk,
    double expandBasePrice,
    double expandPriceIncreasePerChunk,
    int expandVaultMaxChunks,
    boolean expandCustomCurrencyEnabled,
    String expandCustomCurrencyId,
    String expandCustomCurrencyDisplayName,
    boolean contractRefundEnabled,
    String selectionTool,
    boolean selectionRequireTool,
    int selectionTimeoutSeconds,
    int selectionPreviewPeriodTicks,
    int teleportDefaultDelaySeconds,
    Map<String, Integer> teleportPermissionDelays
) {

    public static PluginSettings fromConfig(FileConfiguration config) {
        Set<String> allowedWorlds = new HashSet<>(config.getStringList("allowed-worlds"));
        String currencyDisplayName = config.getString("currency.display-name", "货币");
        String internalNamePrefix = config.getString("claims.internal-name-prefix", "chunk");
        boolean fullHeight = config.getBoolean("claims.full-height", true);
        int minChunks = Math.max(1, config.getInt("claims.min-chunks", 1));
        int maxChunksPerClaim = Math.max(minChunks, config.getInt("claims.max-chunks-per-claim", 64));
        boolean rectangularOnly = config.getBoolean("claims.rectangular-only", true);
        int noClaimRadiusBlocks = Math.max(0, config.getInt("claims.no-claim-radius-blocks", 0));
        int protectedCenterX = config.getInt("claims.protected-center-x", 0);
        int protectedCenterZ = config.getInt("claims.protected-center-z", 0);
        boolean worldClaimRulesAdminBypass = config.getBoolean("world-claim-rules.admin-bypass", true);
        Map<String, WorldClaimRule> worldClaimRules = new HashMap<>();
        ConfigurationSection worldRulesSection = config.getConfigurationSection("world-claim-rules.worlds");
        if (worldRulesSection != null) {
            for (String worldName : worldRulesSection.getKeys(false)) {
                int minDistance = Math.max(0, worldRulesSection.getInt(worldName + ".min-distance-from-origin-xz", 0));
                int maxDistance = Math.max(0, worldRulesSection.getInt(worldName + ".max-distance-from-origin-xz", 0));
                worldClaimRules.put(worldName, new WorldClaimRule(minDistance, maxDistance));
            }
        }
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
        double createPricePerExtraChunk = Math.max(0D, config.getDouble("pricing.create.price-per-extra-chunk", 500D));
        double legacyExpandPricePerChunk = Math.max(0D, config.getDouble("pricing.expand.price-per-chunk", 500D));
        double expandBasePrice = Math.max(0D, config.getDouble("pricing.expand.progressive.base-price", legacyExpandPricePerChunk));
        double expandPriceIncreasePerChunk = Math.max(0D, config.getDouble("pricing.expand.progressive.price-increase-per-chunk", 200D));
        int legacyVaultMaxWidth = Math.max(1, config.getInt("pricing.expand.vault-max-width", 5));
        int legacyVaultMaxDepth = Math.max(1, config.getInt("pricing.expand.vault-max-depth", 5));
        int expandVaultMaxChunks = Math.max(1, config.getInt("pricing.expand.vault-max-chunks", legacyVaultMaxWidth * legacyVaultMaxDepth));
        boolean expandCustomCurrencyEnabled = config.getBoolean("pricing.expand.custom-currency.enabled", true);
        String expandCustomCurrencyId = config.getString("pricing.expand.custom-currency.id", "mengmeng_crystal");
        String expandCustomCurrencyDisplayName = config.getString("pricing.expand.custom-currency.display-name", "萌萌水晶");
        boolean contractRefundEnabled = config.getBoolean("pricing.contract.refund-enabled", false);
        String selectionTool = config.getString("selection.tool", "GOLDEN_SHOVEL");
        boolean selectionRequireTool = config.getBoolean("selection.require-tool", true);
        int selectionTimeoutSeconds = Math.max(15, config.getInt("selection.timeout-seconds", 120));
        int selectionPreviewPeriodTicks = Math.max(2, config.getInt("selection.preview-period-ticks", 10));
        int teleportDefaultDelaySeconds = Math.max(0, config.getInt("teleport.default-delay-seconds", 5));
        Map<String, Integer> teleportPermissionDelays = new HashMap<>();
        ConfigurationSection teleportDelaySection = config.getConfigurationSection("teleport.permission-delays");
        if (teleportDelaySection != null) {
            for (String key : teleportDelaySection.getKeys(false)) {
                teleportPermissionDelays.put(key, Math.max(0, teleportDelaySection.getInt(key)));
            }
        }

        return new PluginSettings(
            Collections.unmodifiableSet(allowedWorlds),
            currencyDisplayName,
            internalNamePrefix,
            fullHeight,
            minChunks,
            maxChunksPerClaim,
            rectangularOnly,
            noClaimRadiusBlocks,
            protectedCenterX,
            protectedCenterZ,
            worldClaimRulesAdminBypass,
            Collections.unmodifiableMap(worldClaimRules),
            defaultMaxClaims,
            Collections.unmodifiableMap(permissionMaxClaims),
            Collections.unmodifiableMap(createTiers),
            fallbackLastTier,
            createPricePerExtraChunk,
            expandBasePrice,
            expandPriceIncreasePerChunk,
            expandVaultMaxChunks,
            expandCustomCurrencyEnabled,
            expandCustomCurrencyId,
            expandCustomCurrencyDisplayName,
            contractRefundEnabled,
            selectionTool,
            selectionRequireTool,
            selectionTimeoutSeconds,
            selectionPreviewPeriodTicks,
            teleportDefaultDelaySeconds,
            Collections.unmodifiableMap(teleportPermissionDelays)
        );
    }

    public record WorldClaimRule(
        int minDistanceFromOriginXz,
        int maxDistanceFromOriginXz
    ) {
    }
}
