package local.mmm.residencechunk.service;

import java.util.UUID;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

public final class EconomyService {

    private final Economy economy;
    private final String currencyDisplayName;

    public EconomyService(Economy economy, String currencyDisplayName) {
        this.economy = economy;
        this.currencyDisplayName = currencyDisplayName;
    }

    public String currencyDisplayName() {
        return currencyDisplayName;
    }

    public boolean has(UUID playerUuid, double amount) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
        return economy.has(player, amount);
    }

    public double balance(UUID playerUuid) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
        return economy.getBalance(player);
    }

    public boolean withdraw(UUID playerUuid, double amount) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response.transactionSuccess();
    }
}
