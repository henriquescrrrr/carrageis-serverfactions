package pt.henrique.serverfactions.hook;

import pt.henrique.serverfactions.ServerFactions;
import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Hook into MultiBank for creating and managing faction group bank accounts.
 * Uses reflection so the plugin compiles without MultiBank on classpath.
 * Calls MultiBankApi.createGroupAccount() and inviteMember() via reflection.
 */
public class MultiBankHook {

    private final ServerFactions plugin;
    private Object multiBankApi;
    private Method createGroupAccountMethod;
    private Method inviteMemberMethod;
    private boolean available = false;

    public MultiBankHook(ServerFactions plugin) {
        this.plugin = plugin;
        try {
            if (Bukkit.getPluginManager().getPlugin("MultiBank") != null) {
                Class<?> apiClass = Class.forName("pt.henrique.multibank.api.MultiBankApi");
                var rsp = Bukkit.getServicesManager().getRegistration(apiClass);
                if (rsp != null) {
                    multiBankApi = rsp.getProvider();
                    // MultiBankApi.createGroupAccount(UUID owner, String name) → CompletableFuture<String>
                    createGroupAccountMethod = apiClass.getMethod("createGroupAccount", UUID.class, String.class);
                    // MultiBankApi.inviteMember(String accountId, UUID inviter, UUID member) → CompletableFuture<Void>
                    inviteMemberMethod = apiClass.getMethod("inviteMember", String.class, UUID.class, UUID.class);
                    available = true;
                    plugin.getLogger().info("MultiBank hook loaded for faction bank accounts.");
                }
            }
        } catch (ClassNotFoundException e) {
            plugin.getLogger().info("MultiBank not found, faction bank accounts disabled.");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to hook into MultiBank: " + e.getMessage());
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Create a group bank account for a faction.
     *
     * @param accountName display name for the account (e.g., "Faction: The Iron Wolves")
     * @param ownerUuid   the faction leader's UUID
     * @return CompletableFuture with the created account ID, or null on failure
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<String> createFactionBankAccount(String accountName, UUID ownerUuid) {
        if (!available) return CompletableFuture.completedFuture(null);
        try {
            Object result = createGroupAccountMethod.invoke(multiBankApi, ownerUuid, accountName);
            if (result instanceof CompletableFuture<?>) {
                return ((CompletableFuture<String>) result);
            }
            return CompletableFuture.completedFuture(result instanceof String s ? s : null);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create faction bank account: " + e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Invite a member to the faction's bank account.
     *
     * @param accountId   the bank account ID
     * @param inviterUuid the player triggering the invite (typically the faction leader)
     * @param memberUuid  the member to invite
     * @return CompletableFuture<Void> completes when the invite is processed
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<Void> inviteMemberToAccount(String accountId, UUID inviterUuid, UUID memberUuid) {
        if (!available) return CompletableFuture.completedFuture(null);
        try {
            Object result = inviteMemberMethod.invoke(multiBankApi, accountId, inviterUuid, memberUuid);
            if (result instanceof CompletableFuture<?>) {
                return ((CompletableFuture<Void>) result);
            }
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to invite member to bank account: " + e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }
}

