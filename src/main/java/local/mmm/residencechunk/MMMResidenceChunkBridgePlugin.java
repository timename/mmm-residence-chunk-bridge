package local.mmm.residencechunk;

import java.io.File;
import java.util.Objects;
import java.util.logging.Level;
import local.mmm.residencechunk.command.LandCommand;
import local.mmm.residencechunk.config.PluginSettings;
import local.mmm.residencechunk.service.EconomyService;
import local.mmm.residencechunk.service.GuiService;
import local.mmm.residencechunk.service.InveroMenuExporter;
import local.mmm.residencechunk.service.LandDataStore;
import local.mmm.residencechunk.service.LandService;
import local.mmm.residencechunk.service.ResidenceHook;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class MMMResidenceChunkBridgePlugin extends JavaPlugin {

    private PluginSettings settings;
    private LandDataStore dataStore;
    private EconomyService economyService;
    private LandService landService;
    private GuiService guiService;
    private ResidenceHook residenceHook;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        settings = PluginSettings.fromConfig(getConfig());
        dataStore = new LandDataStore(new File(getDataFolder(), "claims.yml"));
        dataStore.load();

        Economy vaultEconomy = setupEconomy();
        if (vaultEconomy == null) {
            throw new IllegalStateException("Vault economy provider not found.");
        }
        Plugin residencePlugin = Bukkit.getPluginManager().getPlugin("Residence");
        if (residencePlugin == null) {
            throw new IllegalStateException("Residence plugin not found.");
        }
        residenceHook = new ResidenceHook(residencePlugin);

        economyService = new EconomyService(vaultEconomy, settings.currencyDisplayName());
        landService = new LandService(this, settings, dataStore, economyService, residenceHook);
        guiService = new GuiService(this, landService);

        PluginCommand command = getCommand("mmmland");
        if (command == null) {
            throw new IllegalStateException("Command mmmland is not defined in plugin.yml");
        }

        LandCommand landCommand = new LandCommand(this, landService, guiService);
        command.setExecutor(landCommand);
        command.setTabCompleter(landCommand);
        Bukkit.getPluginManager().registerEvents(guiService, this);
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

    public String message(String path) {
        String raw = getConfig().getString("messages." + path, path);
        return color(Objects.requireNonNullElse(raw, path));
    }

    public String color(String input) {
        return input.replace('&', '§');
    }

    private Economy setupEconomy() {
        RegisteredServiceProvider<Economy> provider =
            Bukkit.getServicesManager().getRegistration(Economy.class);
        return provider == null ? null : provider.getProvider();
    }
}
