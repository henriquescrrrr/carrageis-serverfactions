package pt.henrique.serverfactions.economy;

import pt.henrique.serverfactions.ServerFactions;
import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Economy provider that hooks into MultiBank via reflection.
 * MultiBank's API is expected to return CompletableFuture for async operations.
 */
public class MultiBankProvider implements EconomyProvider {

    private final ServerFactions plugin;
    private Object multiBankApi;
    private Method getBalanceMethod;
    private Method withdrawMethod;
    private Method hasMethod;
    private boolean available = false;

    public MultiBankProvider(ServerFactions plugin) {
        this.plugin = plugin;
        try {
            if (Bukkit.getPluginManager().getPlugin("MultiBank") != null) {
                Class<?> apiClass = Class.forName("pt.henrique.multibank.api.MultiBankApi");
                var rsp = Bukkit.getServicesManager().getRegistration(apiClass);
                if (rsp != null) {
                    multiBankApi = rsp.getProvider();
                    getBalanceMethod = apiClass.getMethod("getBalance", UUID.class);
                    withdrawMethod = apiClass.getMethod("withdraw", UUID.class, double.class);
                    hasMethod = apiClass.getMethod("has", UUID.class, double.class);
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

    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<Boolean> withdraw(UUID player, double amount) {
        if (!available) return CompletableFuture.completedFuture(false);
        try {
            Object result = withdrawMethod.invoke(multiBankApi, player, amount);
            if (result instanceof CompletableFuture<?>) {
                return ((CompletableFuture<Boolean>) result);
            }
            return CompletableFuture.completedFuture(result instanceof Boolean b && b);
        } catch (Exception e) {
            plugin.getLogger().warning("MultiBank withdraw failed: " + e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<Double> getBalance(UUID player) {
        if (!available) return CompletableFuture.completedFuture(0.0);
        try {
            Object result = getBalanceMethod.invoke(multiBankApi, player);
            if (result instanceof CompletableFuture<?>) {
                return ((CompletableFuture<Double>) result);
            }
            return CompletableFuture.completedFuture(
                    result instanceof Number n ? n.doubleValue() : 0.0
            );
        } catch (Exception e) {
            plugin.getLogger().warning("MultiBank getBalance failed: " + e.getMessage());
            return CompletableFuture.completedFuture(0.0);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<Boolean> has(UUID player, double amount) {
        if (!available) return CompletableFuture.completedFuture(false);
        try {
            Object result = hasMethod.invoke(multiBankApi, player, amount);
            if (result instanceof CompletableFuture<?>) {
                return ((CompletableFuture<Boolean>) result);
            }
            return CompletableFuture.completedFuture(result instanceof Boolean b && b);
        } catch (Exception e) {
            plugin.getLogger().warning("MultiBank has() failed: " + e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
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

