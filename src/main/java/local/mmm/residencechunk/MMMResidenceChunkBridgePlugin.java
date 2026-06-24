package local.mmm.residencechunk;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import local.mmm.residencechunk.command.LandCommand;
import local.mmm.residencechunk.config.ConfigComments;
import local.mmm.residencechunk.config.PluginSettings;
import local.mmm.residencechunk.service.AuditLogService;
import local.mmm.residencechunk.service.CustomCurrencyService;
import local.mmm.residencechunk.service.EconomyService;
import local.mmm.residencechunk.service.GuiService;
import local.mmm.residencechunk.service.LandDataStore;
import local.mmm.residencechunk.service.LandService;
import local.mmm.residencechunk.service.MysqlLandDataStore;
import local.mmm.residencechunk.service.ResidenceHook;
import local.mmm.residencechunk.service.SelectionService;
import local.mmm.residencechunk.service.VisualService;
import local.mmm.residencechunk.service.YamlLandDataStore;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class MMMResidenceChunkBridgePlugin extends JavaPlugin {

    private static final String DATA_FOLDER_NAME = "MMMResidenceChunkBridge";

    private File customDataFolder;
    private FileConfiguration config;
    private PluginSettings settings;
    private LandDataStore dataStore;
    private EconomyService economyService;
    private LandService landService;
    private AuditLogService auditLogService;
    private GuiService guiService;
    private SelectionService selectionService;
    private VisualService visualService;
    private boolean consoleSyncLogEnabled;
    private ResidenceHook residenceHook;
    private FileConfiguration language;

    @Override
    public void onEnable() {
        migrateLegacyDataFolder();
        saveDefaultPluginConfig();
        saveLanguageFile();
        reloadPluginConfigFile();
        applyConfigComments();
        reloadLanguage();
        settings = PluginSettings.fromConfig(pluginConfig());
        consoleSyncLogEnabled = pluginConfig().getBoolean("console-sync-log-enabled", false);
        dataStore = createDataStore(settings);
        dataStore.load();

        Economy vaultEconomy = setupEconomy();
        if (vaultEconomy == null) {
            throw new IllegalStateException("Vault economy provider not found.");
        }
        Plugin residencePlugin = Bukkit.getPluginManager().getPlugin("Residence");
        if (residencePlugin == null || !residencePlugin.isEnabled()) {
            throw new IllegalStateException("Residence plugin is not enabled.");
        }
        residenceHook = new ResidenceHook(residencePlugin);

        economyService = new EconomyService(vaultEconomy, settings.currencyDisplayName());
        auditLogService = new AuditLogService(this);
        landService = new LandService(this, settings, dataStore, economyService, new CustomCurrencyService(), residenceHook, auditLogService);
        landService.syncResidenceMessages();
        landService.syncPublicTeleportFlags();
        visualService = new VisualService(this);
        guiService = new GuiService(this, landService, visualService);
        selectionService = new SelectionService(this, landService, visualService);

        PluginCommand command = getCommand("mmmland");
        if (command == null) {
            throw new IllegalStateException("Command mmmland is not defined in plugin.yml");
        }

        LandCommand landCommand = new LandCommand(this, landService, guiService, selectionService);
        command.setExecutor(landCommand);
        command.setTabCompleter(landCommand);
        Bukkit.getPluginManager().registerEvents(landService, this);
        Bukkit.getPluginManager().registerEvents(guiService, this);
        Bukkit.getPluginManager().registerEvents(selectionService, this);
        getLogger().info("mmm-residence-chunk-bridge enabled.");
    }

    private void migrateLegacyDataFolder() {
        File targetFolder = pluginDataFolder();
        File parentFolder = targetFolder.getParentFile();
        if (parentFolder == null) {
            return;
        }

        if (targetFolder.exists()) {
            return;
        }

        for (String legacyName : List.of("mmm-residence-chunk-bridge", "mmmResidenceChunkBridge")) {
            File legacyFolder = new File(parentFolder, legacyName);
            if (!legacyFolder.exists() || legacyFolder.equals(targetFolder)) {
                continue;
            }
            if (legacyFolder.renameTo(targetFolder)) {
                getLogger().info("Migrated legacy data folder from " + legacyName + " to " + DATA_FOLDER_NAME + ".");
            } else {
                getLogger().warning("Unable to migrate legacy data folder. Please rename plugins/" + legacyName
                    + " to plugins/" + DATA_FOLDER_NAME + " manually.");
            }
            return;
        }
    }

    @Override
    public void onDisable() {
        if (dataStore != null) {
            try {
                dataStore.save();
            } catch (RuntimeException exception) {
                getLogger().log(Level.SEVERE, "Failed to save claims.yml", exception);
            }
        }
    }

    public PluginSettings settings() {
        return settings;
    }

    public GuiService guiService() {
        return guiService;
    }

    public ResidenceHook residenceHook() {
        return residenceHook;
    }

    public boolean isConsoleSyncLogEnabled() {
        return consoleSyncLogEnabled;
    }
    public void reloadPluginConfig() {
        reloadPluginConfigFile();
        applyConfigComments();
        saveLanguageFile();
        reloadLanguage();
        settings = PluginSettings.fromConfig(pluginConfig());
        consoleSyncLogEnabled = pluginConfig().getBoolean("console-sync-log-enabled", false);
        if (landService != null) {
            landService.reloadSettings(settings);
            landService.syncResidenceMessages();
            landService.syncPublicTeleportFlags();
        }
    }

    public File pluginDataFolder() {
        if (customDataFolder == null) {
            File defaultParent = getDataFolder().getParentFile();
            customDataFolder = new File(defaultParent == null ? new File("plugins") : defaultParent, DATA_FOLDER_NAME);
        }
        return customDataFolder;
    }

    public FileConfiguration pluginConfig() {
        if (config == null) {
            reloadPluginConfigFile();
        }
        return config;
    }

    private LandDataStore createDataStore(PluginSettings settings) {
        File claimsFile = new File(pluginDataFolder(), "claims.yml");
        YamlLandDataStore yamlStore = new YamlLandDataStore(claimsFile);
        if ("mysql".equalsIgnoreCase(settings.storageType())) {
            getLogger().info("Using MySQL claim storage.");
            return new MysqlLandDataStore(settings.mysqlStorage(), yamlStore);
        }
        getLogger().info("Using YAML claim storage.");
        return yamlStore;
    }

    public String message(String path) {
        String raw = language == null ? null : language.getString(path);
        if (raw == null) {
            raw = pluginConfig().getString("messages." + path, path);
        }
        return color(Objects.requireNonNullElse(raw, path));
    }

    public java.util.List<String> messageList(String path) {
        java.util.List<String> raw = language == null ? java.util.List.of() : language.getStringList(path);
        if (raw.isEmpty()) {
            raw = pluginConfig().getStringList("messages." + path);
        }
        return raw.stream().map(this::color).toList();
    }

    public String color(String input) {
        return input.replace('&', '\u00A7');
    }

    private void saveDefaultPluginConfig() {
        File configFile = new File(pluginDataFolder(), "config.yml");
        if (!configFile.exists()) {
            copyResource("config.yml", configFile);
            return;
        }
        mergeMissingConfigKeys(configFile);
    }

    private void mergeMissingConfigKeys(File configFile) {
        try (InputStream input = getResource("config.yml")) {
            if (input == null) {
                throw new IllegalStateException("Missing embedded resource config.yml");
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
            YamlConfiguration current = YamlConfiguration.loadConfiguration(configFile);
            boolean legacyCreatePricing = !current.contains("pricing.create.currency-tiers");
            boolean changed = false;
            for (String key : defaults.getKeys(true)) {
                if (!defaults.isConfigurationSection(key) && !current.contains(key)) {
                    current.set(key, defaults.get(key));
                    changed = true;
                }
            }
            if (legacyCreatePricing && current.getInt("limits.default-max-claims", 4) == 4) {
                current.set("limits.default-max-claims", 6);
                current.set("limits.permission-max-claims.mmmland.limit.5", 5);
                current.set("limits.permission-max-claims.mmmland.limit.6", 6);
                changed = true;
            }
            if (isPreviousCreatePricingDefaults(current) || isOldCreatePricingDefaults(current)) {
                current.set("limits.default-max-claims", 6);
                current.set("limits.permission-max-claims.mmmland.limit.5", 5);
                current.set("limits.permission-max-claims.mmmland.limit.6", 6);
                current.set("pricing.create.currency-tiers.3.currency", "vault");
                current.set("pricing.create.currency-tiers.3.amount", 5000);
                current.set("pricing.create.currency-tiers.4.currency", "custom");
                current.set("pricing.create.currency-tiers.4.amount", 100);
                current.set("pricing.create.currency-tiers.5.currency", "custom");
                current.set("pricing.create.currency-tiers.5.amount", 500);
                current.set("pricing.create.currency-tiers.6.currency", "custom");
                current.set("pricing.create.currency-tiers.6.amount", 1000);
                changed = true;
            }
            if (changed) {
                current.save(configFile);
            }
        } catch (IOException exception) {
            getLogger().log(Level.WARNING, "Failed to merge missing config keys into config.yml", exception);
        }
    }

    private void reloadPluginConfigFile() {
        config = YamlConfiguration.loadConfiguration(new File(pluginDataFolder(), "config.yml"));
    }

    private void saveLanguageFile() {
        File languageFile = new File(pluginDataFolder(), "lang/zh_CN.yml");
        if (!languageFile.exists()) {
            copyResource("lang/zh_CN.yml", languageFile);
            return;
        }
        mergeMissingLanguageKeys(languageFile);
    }

    private void mergeMissingLanguageKeys(File languageFile) {
        try (InputStream input = getResource("lang/zh_CN.yml")) {
            if (input == null) {
                throw new IllegalStateException("Missing embedded resource lang/zh_CN.yml");
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
            YamlConfiguration current = YamlConfiguration.loadConfiguration(languageFile);
            boolean changed = false;
            for (String key : defaults.getKeys(true)) {
                if (!defaults.isConfigurationSection(key) && !current.contains(key)) {
                    current.set(key, defaults.get(key));
                    changed = true;
                }
            }
            if (changed) {
                current.save(languageFile);
            }
        } catch (IOException exception) {
            getLogger().log(Level.WARNING, "Failed to merge missing language keys into zh_CN.yml", exception);
        }
    }



    private boolean isPreviousCreatePricingDefaults(YamlConfiguration config) {
        return config.getInt("limits.default-max-claims", 0) == 5
            && "vault".equalsIgnoreCase(config.getString("pricing.create.currency-tiers.3.currency", ""))
            && config.getDouble("pricing.create.currency-tiers.3.amount", -1D) == 5000D
            && "custom".equalsIgnoreCase(config.getString("pricing.create.currency-tiers.4.currency", ""))
            && config.getDouble("pricing.create.currency-tiers.4.amount", -1D) == 100D
            && "custom".equalsIgnoreCase(config.getString("pricing.create.currency-tiers.5.currency", ""))
            && config.getDouble("pricing.create.currency-tiers.5.amount", -1D) == 500D
            && !config.contains("pricing.create.currency-tiers.6");
    }
    private boolean isOldCreatePricingDefaults(YamlConfiguration config) {
        return config.getInt("limits.default-max-claims", 0) == 6
            && "vault".equalsIgnoreCase(config.getString("pricing.create.currency-tiers.3.currency", ""))
            && config.getDouble("pricing.create.currency-tiers.3.amount", -1D) == 2500D
            && "vault".equalsIgnoreCase(config.getString("pricing.create.currency-tiers.4.currency", ""))
            && config.getDouble("pricing.create.currency-tiers.4.amount", -1D) == 5000D
            && "custom".equalsIgnoreCase(config.getString("pricing.create.currency-tiers.5.currency", ""))
            && config.getDouble("pricing.create.currency-tiers.5.amount", -1D) == 100D
            && "custom".equalsIgnoreCase(config.getString("pricing.create.currency-tiers.6.currency", ""))
            && config.getDouble("pricing.create.currency-tiers.6.amount", -1D) == 200D;
    }
    private void reloadLanguage() {
        language = YamlConfiguration.loadConfiguration(new File(pluginDataFolder(), "lang/zh_CN.yml"));
    }

    private void applyConfigComments() {
        ConfigComments.apply(pluginConfig());
        try {
            pluginConfig().save(new File(pluginDataFolder(), "config.yml"));
        } catch (IOException exception) {
            getLogger().log(Level.WARNING, "Failed to save commented config.yml", exception);
        }
        reloadPluginConfigFile();
    }

    private void copyResource(String resourcePath, File outputFile) {
        File parentFile = outputFile.getParentFile();
        if (parentFile != null && !parentFile.exists() && !parentFile.mkdirs()) {
            throw new IllegalStateException("Unable to create directory " + parentFile.getAbsolutePath());
        }
        try (InputStream input = getResource(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("Missing embedded resource " + resourcePath);
            }
            java.nio.file.Files.copy(input, outputFile.toPath());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to save resource " + resourcePath, exception);
        }
    }

    private Economy setupEconomy() {
        RegisteredServiceProvider<Economy> provider =
            Bukkit.getServicesManager().getRegistration(Economy.class);
        return provider == null ? null : provider.getProvider();
    }
}
