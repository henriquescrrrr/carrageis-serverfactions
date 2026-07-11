package pt.henrique.serverfactions.economy;

import pt.henrique.serverfactions.ServerFactions;
import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Economy provider that hooks into MultiBank via reflection.
 *
 * MultiBank's real API is account-based and works in integer cents:
 * <pre>
 *   CompletableFuture&lt;Account&gt;           getPersonalAccount(UUID playerId)
 *   CompletableFuture&lt;Long&gt;              getBalanceCents(String accountId)
 *   CompletableFuture&lt;TransactionResult&gt; withdraw(String accountId, long cents, String reason, UUID actor)
 * </pre>
 * so every player-level operation resolves the player's personal account first and
 * converts between cents and the double amounts used by this plugin.
 */
public class MultiBankProvider implements EconomyProvider {

    private static final String WITHDRAW_REASON = "ServerFactions: faction creation cost";
    private static final String DEPOSIT_REASON = "ServerFactions: refund";

    private final ServerFactions plugin;
    private Object multiBankApi;
    private Method getPersonalAccountMethod;
    private Method getBalanceCentsMethod;
    private Method withdrawMethod;
    private Method depositMethod;
    private boolean available = false;

    public MultiBankProvider(ServerFactions plugin) {
        this.plugin = plugin;
        try {
            if (Bukkit.getPluginManager().getPlugin("MultiBank") != null) {
                Class<?> apiClass = Class.forName("pt.henrique.multibank.api.MultiBankApi");
                var rsp = Bukkit.getServicesManager().getRegistration(apiClass);
                if (rsp != null) {
                    multiBankApi = rsp.getProvider();
                    getPersonalAccountMethod = apiClass.getMethod("getPersonalAccount", UUID.class);
                    getBalanceCentsMethod = apiClass.getMethod("getBalanceCents", String.class);
                    withdrawMethod = apiClass.getMethod("withdraw",
                            String.class, long.class, String.class, UUID.class);
                    depositMethod = apiClass.getMethod("deposit",
                            String.class, long.class, String.class, UUID.class);
                    available = true;
                    plugin.getLogger().info("MultiBank economy provider loaded.");
                }
            }
        } catch (ClassNotFoundException e) {
            plugin.getLogger().info("MultiBank not found, skipping economy provider.");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to hook into MultiBank economy: " + e.getMessage());
        }
    }

    /** Resolve the player's personal account ID (async). */
    private CompletableFuture<String> getPersonalAccountId(UUID player) {
        try {
            Object result = getPersonalAccountMethod.invoke(multiBankApi, player);
            if (!(result instanceof CompletableFuture<?> future)) {
                return CompletableFuture.completedFuture(null);
            }
            return future.thenApply(account -> {
                if (account == null) return null;
                try {
                    Object id = account.getClass().getMethod("getAccountId").invoke(account);
                    return id instanceof String s ? s : null;
                } catch (Exception e) {
                    plugin.getLogger().warning("MultiBank account resolution failed: " + e.getMessage());
                    return null;
                }
            });
        } catch (Exception e) {
            plugin.getLogger().warning("MultiBank getPersonalAccount failed: " + e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public CompletableFuture<Boolean> withdraw(UUID player, double amount) {
        return transact(withdrawMethod, "withdraw", WITHDRAW_REASON, player, amount);
    }

    @Override
    public CompletableFuture<Boolean> deposit(UUID player, double amount) {
        return transact(depositMethod, "deposit", DEPOSIT_REASON, player, amount);
    }

    /** Run a cents-based transaction (withdraw/deposit) against the player's personal account. */
    private CompletableFuture<Boolean> transact(Method method, String opName, String reason,
                                                UUID player, double amount) {
        if (!available) return CompletableFuture.completedFuture(false);
        long cents = Math.round(amount * 100.0);
        if (cents <= 0) return CompletableFuture.completedFuture(true);
        return getPersonalAccountId(player).thenCompose(accountId -> {
            if (accountId == null) return CompletableFuture.completedFuture(false);
            try {
                Object result = method.invoke(multiBankApi, accountId, cents, reason, player);
                if (!(result instanceof CompletableFuture<?> future)) {
                    return CompletableFuture.completedFuture(false);
                }
                return future.thenApply(txResult -> {
                    if (txResult == null) return false;
                    try {
                        Object success = txResult.getClass().getMethod("isSuccess").invoke(txResult);
                        return success instanceof Boolean b && b;
                    } catch (Exception e) {
                        plugin.getLogger().warning("MultiBank " + opName + " result check failed: " + e.getMessage());
                        return false;
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().warning("MultiBank " + opName + " failed: " + e.getMessage());
                return CompletableFuture.completedFuture(false);
            }
        }).exceptionally(ex -> {
            plugin.getLogger().warning("MultiBank " + opName + " failed: " + ex.getMessage());
            return false;
        });
    }

    @Override
    public CompletableFuture<Double> getBalance(UUID player) {
        if (!available) return CompletableFuture.completedFuture(0.0);
        return getPersonalAccountId(player).thenCompose(accountId -> {
            if (accountId == null) return CompletableFuture.completedFuture(0.0);
            try {
                Object result = getBalanceCentsMethod.invoke(multiBankApi, accountId);
                if (!(result instanceof CompletableFuture<?> future)) {
                    return CompletableFuture.completedFuture(0.0);
                }
                return future.thenApply(cents ->
                        cents instanceof Number n ? n.longValue() / 100.0 : 0.0);
            } catch (Exception e) {
                plugin.getLogger().warning("MultiBank getBalance failed: " + e.getMessage());
                return CompletableFuture.completedFuture(0.0);
            }
        }).exceptionally(ex -> {
            plugin.getLogger().warning("MultiBank getBalance failed: " + ex.getMessage());
            return 0.0;
        });
    }

    @Override
    public CompletableFuture<Boolean> has(UUID player, double amount) {
        if (!available) return CompletableFuture.completedFuture(false);
        if (amount <= 0) return CompletableFuture.completedFuture(true);
        // Optimistic pre-check only; the authoritative gate is the withdraw itself,
        // which MultiBank refuses server-side on insufficient funds.
        return getBalance(player).thenApply(balance -> balance >= amount);
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public String getName() {
        return "MultiBank";
    }
}
