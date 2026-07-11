package pt.henrique.serverfactions.economy;

import pt.henrique.serverfactions.ServerFactions;
import pt.henrique.serverfactions.config.ConfigManager;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Manages the active economy provider.
 * Selects provider based on config priority (MultiBank → Vault).
 */
public class EconomyManager {

    private final ServerFactions plugin;
    private final ConfigManager configManager;
    private EconomyProvider activeProvider;

    public EconomyManager(ServerFactions plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        setupProvider();
    }

    /**
     * Re-select the provider after a config reload (the economy section is
     * documented as reloadable via /fac admin reload).
     */
    public void reload() {
        activeProvider = null;
        setupProvider();
    }

    private void setupProvider() {
        List<String> priority = configManager.getEconomyPriority();

        for (String providerName : priority) {
            EconomyProvider provider = switch (providerName) {
                case "MultiBank" -> new MultiBankProvider(plugin);
                case "Vault" -> new VaultProvider(plugin);
                default -> null;
            };

            if (provider != null && provider.isAvailable()) {
                activeProvider = provider;
                plugin.getLogger().info("Using economy provider: " + provider.getName());
                return;
            }
        }

        plugin.getLogger().warning("No economy provider found! Faction creation cost will be disabled.");
    }

    /**
     * Withdraw an amount from a player. Returns true if no economy is set up or amount <= 0.
     */
    public CompletableFuture<Boolean> withdraw(UUID player, double amount) {
        if (activeProvider == null || amount <= 0) return CompletableFuture.completedFuture(true);
        return activeProvider.withdraw(player, amount);
    }

    /**
     * Deposit an amount into a player's account (used to refund a withdrawn cost).
     * Returns true if no economy is set up or amount <= 0.
     */
    public CompletableFuture<Boolean> deposit(UUID player, double amount) {
        if (activeProvider == null || amount <= 0) return CompletableFuture.completedFuture(true);
        return activeProvider.deposit(player, amount);
    }

    /**
     * Get a player's balance. Returns 0.0 if no economy is set up.
     */
    public CompletableFuture<Double> getBalance(UUID player) {
        if (activeProvider == null) return CompletableFuture.completedFuture(0.0);
        return activeProvider.getBalance(player);
    }

    /**
     * Check if a player has at least the specified amount. Returns true if no economy or amount <= 0.
     */
    public CompletableFuture<Boolean> has(UUID player, double amount) {
        if (activeProvider == null || amount <= 0) return CompletableFuture.completedFuture(true);
        return activeProvider.has(player, amount);
    }

    public boolean isAvailable() {
        return activeProvider != null;
    }

    public String getActiveProviderName() {
        return activeProvider != null ? activeProvider.getName() : "None";
    }
}

