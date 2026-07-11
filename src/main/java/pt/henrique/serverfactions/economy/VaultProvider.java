package pt.henrique.serverfactions.economy;

import pt.henrique.serverfactions.ServerFactions;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Economy provider that wraps Vault's synchronous Economy API into async CompletableFutures.
 */
public class VaultProvider implements EconomyProvider {

    private final ServerFactions plugin;
    private Economy economy;
    private boolean available = false;

    public VaultProvider(ServerFactions plugin) {
        this.plugin = plugin;
        try {
            if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
                RegisteredServiceProvider<Economy> rsp =
                        Bukkit.getServicesManager().getRegistration(Economy.class);
                if (rsp != null) {
                    economy = rsp.getProvider();
                    available = true;
                    plugin.getLogger().info("Vault economy provider loaded.");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to hook into Vault: " + e.getMessage());
        }
    }

    @Override
    public CompletableFuture<Boolean> withdraw(UUID playerUuid, double amount) {
        if (!available) return CompletableFuture.completedFuture(false);
        return CompletableFuture.supplyAsync(() -> {
            OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
            EconomyResponse response = economy.withdrawPlayer(player, amount);
            return response.transactionSuccess();
        });
    }

    @Override
    public CompletableFuture<Boolean> deposit(UUID playerUuid, double amount) {
        if (!available) return CompletableFuture.completedFuture(false);
        return CompletableFuture.supplyAsync(() -> {
            OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
            EconomyResponse response = economy.depositPlayer(player, amount);
            return response.transactionSuccess();
        });
    }

    @Override
    public CompletableFuture<Double> getBalance(UUID playerUuid) {
        if (!available) return CompletableFuture.completedFuture(0.0);
        return CompletableFuture.supplyAsync(() -> {
            OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
            return economy.getBalance(player);
        });
    }

    @Override
    public CompletableFuture<Boolean> has(UUID playerUuid, double amount) {
        if (!available) return CompletableFuture.completedFuture(false);
        return CompletableFuture.supplyAsync(() -> {
            OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
            return economy.has(player, amount);
        });
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public String getName() {
        return "Vault";
    }
}

