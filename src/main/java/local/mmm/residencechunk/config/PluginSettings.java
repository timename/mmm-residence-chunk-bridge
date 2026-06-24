package local.mmm.residencechunk.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public record PluginSettings(
    String storageType,
    MysqlStorageSettings mysqlStorage,
    Set<String> allowedWorlds,
    String currencyDisplayName,
    String internalNamePrefix,
    boolean fullHeight,
    int minChunks,
    int maxChunksPerClaim,
    int minClaimSpacingChunks,
    boolean rectangularOnly,
    int noClaimRadiusBlocks,
    int protectedCenterX,
    int protectedCenterZ,
    boolean worldClaimRulesAdminBypass,
    Map<String, WorldClaimRule> worldClaimRules,
    int defaultMaxClaims,
    Map<String, Integer> permissionMaxClaims,
    Map<Integer, Double> createTiers,
    Map<Integer, CreatePriceTier> createPriceTiers,
    boolean fallbackLastTier,
    double createPricePerExtraChunk,
    double expandBasePrice,
    double expandPriceIncreasePerChunk,
    int expandVaultMaxChunks,
    double expandCustomBasePrice,
    double expandCustomPriceIncreasePerChunk,
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
        String storageType = config.getString("storage.type", "yaml").toLowerCase();
        String mysqlHost = config.getString("storage.mysql.host", "127.0.0.1");
        int mysqlPort = Math.max(1, config.getInt("storage.mysql.port", 3306));
        String mysqlDatabase = config.getString("storage.mysql.database", "minecraft");
        String mysqlJdbcUrlOverride = config.getString("storage.mysql.jdbc-url-override", "");
        String legacyJdbcUrl = config.getString("storage.mysql.jdbc-url", "");
        String mysqlJdbcUrl = !isBlank(mysqlJdbcUrlOverride)
            ? mysqlJdbcUrlOverride
            : (!isBlank(legacyJdbcUrl) ? legacyJdbcUrl : buildMysqlJdbcUrl(mysqlHost, mysqlPort, mysqlDatabase));
        String legacyTablePrefix = config.getString("storage.mysql.table-prefix", "mmm_land_");
        String mysqlTable = config.getString("storage.mysql.table", legacyTablePrefix + "claims");
        MysqlStorageSettings mysqlStorage = new MysqlStorageSettings(
            mysqlJdbcUrl,
            mysqlHost,
            mysqlPort,
            mysqlDatabase,
            config.getString("storage.mysql.username", "root"),
            config.getString("storage.mysql.password", ""),
            mysqlTable,
            config.getBoolean("storage.mysql.migrate-from-yaml-if-empty", true)
        );
        Set<String> allowedWorlds = new HashSet<>(config.getStringList("allowed-worlds"));
        String currencyDisplayName = config.getString("currency.display-name", "货币");
        String internalNamePrefix = config.getString("claims.internal-name-prefix", "chunk");
        boolean fullHeight = config.getBoolean("claims.full-height", true);
        int minChunks = Math.max(1, config.getInt("claims.min-chunks", 1));
        int maxChunksPerClaim = Math.max(minChunks, config.getInt("claims.max-chunks-per-claim", 64));
        int minClaimSpacingChunks = Math.max(0, config.getInt("claims.min-spacing-chunks", 1));
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
        int defaultMaxClaims = Math.max(0, config.getInt("limits.default-max-claims", 6));

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


        Map<Integer, CreatePriceTier> createPriceTiers = new HashMap<>();
        ConfigurationSection priceTierSection = config.getConfigurationSection("pricing.create.currency-tiers");
        if (priceTierSection != null) {
            for (String key : priceTierSection.getKeys(false)) {
                try {
                    int tierIndex = Integer.parseInt(key);
                    String currency = priceTierSection.getString(key + ".currency", "vault");
                    double amount = Math.max(0D, priceTierSection.getDouble(key + ".amount", 0D));
                    createPriceTiers.put(tierIndex, new CreatePriceTier(currency, amount));
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
        double expandCustomBasePrice = Math.max(0D, config.getDouble("pricing.expand.custom-currency.progressive.base-price", 10D));
        double expandCustomPriceIncreasePerChunk = Math.max(0D, config.getDouble("pricing.expand.custom-currency.progressive.price-increase-per-chunk", 10D));
        boolean expandCustomCurrencyEnabled = config.getBoolean("pricing.expand.custom-currency.enabled", true);
        String expandCustomCurrencyId = config.getString("pricing.expand.custom-currency.id", "mengmeng_shell");
        String expandCustomCurrencyDisplayName = config.getString("pricing.expand.custom-currency.display-name", "萌萌贝壳");
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
            storageType,
            mysqlStorage,
            Collections.unmodifiableSet(allowedWorlds),
            currencyDisplayName,
            internalNamePrefix,
            fullHeight,
            minChunks,
            maxChunksPerClaim,
            minClaimSpacingChunks,
            rectangularOnly,
            noClaimRadiusBlocks,
            protectedCenterX,
            protectedCenterZ,
            worldClaimRulesAdminBypass,
            Collections.unmodifiableMap(worldClaimRules),
            defaultMaxClaims,
            Collections.unmodifiableMap(permissionMaxClaims),
            Collections.unmodifiableMap(createTiers),
            Collections.unmodifiableMap(createPriceTiers),
            fallbackLastTier,
            createPricePerExtraChunk,
            expandBasePrice,
            expandPriceIncreasePerChunk,
            expandVaultMaxChunks,
            expandCustomBasePrice,
            expandCustomPriceIncreasePerChunk,
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

    private static String buildMysqlJdbcUrl(String host, int port, String database) {
        return "jdbc:mysql://" + host + ":" + port + "/" + database
            + "?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record CreatePriceTier(
        String currency,
        double amount
    ) {
        public boolean customCurrency() {
            return "custom".equalsIgnoreCase(currency) || "shell".equalsIgnoreCase(currency) || "mengmeng_shell".equalsIgnoreCase(currency);
        }
    }
    public record WorldClaimRule(
        int minDistanceFromOriginXz,
        int maxDistanceFromOriginXz
    ) {
    }

    public record MysqlStorageSettings(
        String jdbcUrl,
        String host,
        int port,
        String database,
        String username,
        String password,
        String table,
        boolean migrateFromYamlIfEmpty
    ) {
    }
}
