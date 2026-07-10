package pt.henrique.serverfactions.economy;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Async economy provider interface.
 * All methods return CompletableFuture — never block.
 */
public interface EconomyProvider {

    /**
     * Withdraw an amount from a player's account.
     *
     * @return true if withdrawal succeeded
     */
    CompletableFuture<Boolean> withdraw(UUID player, double amount);

    /**
     * Get a player's balance.
     */
    CompletableFuture<Double> getBalance(UUID player);

    /**
     * Check if a player has at least the specified amount.
     */
    CompletableFuture<Boolean> has(UUID player, double amount);

    /**
     * Whether this provider is available and ready.
     */
    boolean isAvailable();

    /**
     * Display name of this provider (e.g., "MultiBank", "Vault").
     */
    String getName();
}

