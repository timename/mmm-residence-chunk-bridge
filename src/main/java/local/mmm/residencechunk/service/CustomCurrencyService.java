package local.mmm.residencechunk.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import local.mmm.vaultsync.CurrencyDefinition;
import local.mmm.vaultsync.api.BalanceMutationResult;
import local.mmm.vaultsync.api.VaultSyncCurrencyService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class CustomCurrencyService {

    private static final int TIMEOUT_SECONDS = 3;

    public boolean isAvailable(String currencyId) {
        VaultSyncCurrencyService service = service();
        return service != null
            && service.canAcceptEconomicOperations()
            && currencyId != null
            && !currencyId.isBlank()
            && service.isKnownCurrency(currencyId);
    }

    public String displayName(String currencyId, String fallback) {
        VaultSyncCurrencyService service = service();
        if (service == null || currencyId == null || currencyId.isBlank()) {
            return fallback;
        }
        CurrencyDefinition currency = service.findCurrency(currencyId).orElse(null);
        if (currency == null) {
            return fallback;
        }
        String label = currency.displayLabel();
        return label == null || label.isBlank() ? fallback : label;
    }

    public boolean has(UUID playerUuid, String currencyId, double amount) {
        if (amount <= 0D) {
            return true;
        }
        VaultSyncCurrencyService service = service();
        if (service == null || !service.canAcceptEconomicOperations()) {
            return false;
        }
        try {
            BigDecimal balance = service.getBalanceAsync(playerUuid, currencyId)
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return balance.compareTo(toAmount(amount)) >= 0;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException exception) {
            return false;
        }
    }

    public boolean withdraw(UUID playerUuid, String currencyId, double amount, String reason) {
        if (amount <= 0D) {
            return true;
        }
        VaultSyncCurrencyService service = service();
        if (service == null || !service.canAcceptEconomicOperations()) {
            return false;
        }
        try {
            BalanceMutationResult result = service.removeBalanceAsync(playerUuid, currencyId, toAmount(amount), reason)
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return result.success();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException exception) {
            return false;
        }
    }

    private BigDecimal toAmount(double amount) {
        return BigDecimal.valueOf(amount).setScale(4, RoundingMode.HALF_UP);
    }

    private VaultSyncCurrencyService service() {
        RegisteredServiceProvider<VaultSyncCurrencyService> registration =
            Bukkit.getServicesManager().getRegistration(VaultSyncCurrencyService.class);
        return registration == null ? null : registration.getProvider();
    }
}
