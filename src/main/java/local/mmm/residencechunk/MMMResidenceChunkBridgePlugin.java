package local.mmm.residencechunk;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;
import local.mmm.residencechunk.command.LandCommand;
import local.mmm.residencechunk.config.ConfigComments;
import local.mmm.residencechunk.config.PluginSettings;
import local.mmm.residencechunk.service.AuditLogService;
import local.mmm.residencechunk.service.CustomCurrencyService;
import local.mmm.residencechunk.service.EconomyService;
import local.mmm.residencechunk.service.GuiService;
import local.mmm.residencechunk.service.InveroMenuExporter;
import local.mmm.residencechunk.service.LandDataStore;
import local.mmm.residencechunk.service.LandService;
import local.mmm.residencechunk.service.ResidenceHook;
import local.mmm.residencechunk.service.SelectionService;
import local.mmm.residencechunk.service.VisualService;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class MMMResidenceChunkBridgePlugin extends JavaPlugin {

    private PluginSettings settings;
    private LandDataStore dataStore;
    private EconomyService economyService;
    private LandService landService;
    private AuditLogService auditLogService;
    private GuiService guiService;
    private SelectionService selectionService;
    private VisualService visualService;
    private ResidenceHook residenceHook;
    private FileConfiguration language;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveLanguageFile();
        reloadConfig();
        applyConfigComments();
        reloadLanguage();
        settings = PluginSettings.fromConfig(getConfig());
        dataStore = new LandDataStore(new File(getDataFolder(), "claims.yml"));
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
        InveroMenuExporter.exportIfAvailable(this);
        getLogger().info("MMMResidenceChunkBridge enabled.");
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

    public void reloadPluginConfig() {
        reloadConfig();
        applyConfigComments();
        saveLanguageFile();
        reloadLanguage();
        settings = PluginSettings.fromConfig(getConfig());
        if (landService != null) {
            landService.reloadSettings(settings);
        }
    }

    public String message(String path) {
        String raw = language == null ? null : language.getString(path);
        if (raw == null) {
            raw = getConfig().getString("messages." + path, path);
        }
        return color(Objects.requireNonNullElse(raw, path));
    }

    public java.util.List<String> messageList(String path) {
        java.util.List<String> raw = language == null ? java.util.List.of() : language.getStringList(path);
        if (raw.isEmpty()) {
            raw = getConfig().getStringList("messages." + path);
        }
        return raw.stream().map(this::color).toList();
    }

    public String color(String input) {
        return input.replace('&', '\u00A7');
    }

    private void saveLanguageFile() {
        File languageFile = new File(getDataFolder(), "lang/zh_CN.yml");
        if (!languageFile.exists()) {
            saveResource("lang/zh_CN.yml", false);
        }
    }

    private void reloadLanguage() {
        language = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "lang/zh_CN.yml"));
    }

    private void applyConfigComments() {
        ConfigComments.apply(getConfig());
        try {
            getConfig().save(new File(getDataFolder(), "config.yml"));
        } catch (IOException exception) {
            getLogger().log(Level.WARNING, "Failed to save commented config.yml", exception);
        }
        reloadConfig();
    }

    private Economy setupEconomy() {
        RegisteredServiceProvider<Economy> provider =
            Bukkit.getServicesManager().getRegistration(Economy.class);
        return provider == null ? null : provider.getProvider();
    }
}
