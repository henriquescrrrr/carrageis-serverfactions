package pt.henrique.serverfactions.api;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Implementation of ServerFactionsApi.
 * Delegates to FactionManager (wired in Phase 2).
 * Until FactionManager is connected, returns safe defaults.
 */
public class ServerFactionsProvider implements ServerFactionsApi {

    // Will be set when FactionManager is created in Phase 2
    private FactionManagerBridge manager;

    /**
     * Bridge interface so we don't depend on FactionManager class directly in Phase 1.
     * FactionManager will implement this in Phase 2.
     */
    public interface FactionManagerBridge {
        boolean areInSameFaction(UUID player1, UUID player2);
        String getFactionIdByPlayer(UUID player);
        String getFactionName(String factionId);
        String getFactionTag(String factionId);
        UUID getFactionLeader(String factionId);
        List<UUID> getFactionMemberUuids(String factionId);
        boolean isInFaction(UUID player);
        String getBankAccountId(String factionId);
        void setBankAccountId(String factionId, String accountId);
    }

    public void setManager(FactionManagerBridge manager) {
        this.manager = manager;
    }

    @Override
    public boolean areInSameFaction(UUID player1, UUID player2) {
        if (manager == null) return false;
        return manager.areInSameFaction(player1, player2);
    }

    @Override
    public String getFactionId(UUID player) {
        if (manager == null) return null;
        return manager.getFactionIdByPlayer(player);
    }

    @Override
    public String getFactionName(String factionId) {
        if (manager == null) return null;
        return manager.getFactionName(factionId);
    }

    @Override
    public String getFactionTag(String factionId) {
        if (manager == null) return null;
        return manager.getFactionTag(factionId);
    }

    @Override
    public UUID getFactionLeader(String factionId) {
        if (manager == null) return null;
        return manager.getFactionLeader(factionId);
    }

    @Override
    public List<UUID> getFactionMembers(String factionId) {
        if (manager == null) return Collections.emptyList();
        return manager.getFactionMemberUuids(factionId);
    }

    @Override
    public boolean isInFaction(UUID player) {
        if (manager == null) return false;
        return manager.isInFaction(player);
    }

    @Override
    public String getFactionBankAccountId(String factionId) {
        if (manager == null) return null;
        return manager.getBankAccountId(factionId);
    }

    @Override
    public void setFactionBankAccountId(String factionId, String accountId) {
        if (manager != null) {
            manager.setBankAccountId(factionId, accountId);
        }
    }
}

