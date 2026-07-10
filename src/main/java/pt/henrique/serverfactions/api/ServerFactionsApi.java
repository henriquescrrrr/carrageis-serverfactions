package pt.henrique.serverfactions.api;

import java.util.List;
import java.util.UUID;

/**
 * Public API for ServerFactions.
 * Other plugins (MultiBank, ServerBounties, LandClaim) can access this
 * via Bukkit's ServicesManager:
 * <pre>
 * RegisteredServiceProvider&lt;ServerFactionsApi&gt; rsp =
 *     Bukkit.getServicesManager().getRegistration(ServerFactionsApi.class);
 * if (rsp != null) {
 *     ServerFactionsApi api = rsp.getProvider();
 * }
 * </pre>
 */
public interface ServerFactionsApi {

    // === Queries ===

    /**
     * Check if two players are in the same faction.
     */
    boolean areInSameFaction(UUID player1, UUID player2);

    /**
     * Get the faction ID for a player. Returns null if not in a faction.
     */
    String getFactionId(UUID player);

    /**
     * Get the display name of a faction.
     */
    String getFactionName(String factionId);

    /**
     * Get the short tag of a faction.
     */
    String getFactionTag(String factionId);

    /**
     * Get the leader's UUID of a faction.
     */
    UUID getFactionLeader(String factionId);

    /**
     * Get all member UUIDs of a faction.
     */
    List<UUID> getFactionMembers(String factionId);

    /**
     * Check if a player is in any faction.
     */
    boolean isInFaction(UUID player);

    // === MultiBank integration ===

    /**
     * Get the MultiBank group account ID for a faction's bank.
     * Returns null if no bank account is linked.
     */
    String getFactionBankAccountId(String factionId);

    /**
     * Set the MultiBank group account ID for a faction's bank.
     * Called by MultiBank's FactionAccountListener when creating an account.
     */
    void setFactionBankAccountId(String factionId, String accountId);
}

