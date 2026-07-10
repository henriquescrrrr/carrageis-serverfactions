package pt.henrique.serverfactions.model;

import java.util.UUID;

public class FactionMember {

    private UUID playerUuid;
    private String factionId;
    private String roleId;
    private long joinedAt;

    public FactionMember() {}

    public FactionMember(UUID playerUuid, String factionId, String roleId, long joinedAt) {
        this.playerUuid = playerUuid;
        this.factionId = factionId;
        this.roleId = roleId;
        this.joinedAt = joinedAt;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public void setPlayerUuid(UUID playerUuid) { this.playerUuid = playerUuid; }

    public String getFactionId() { return factionId; }
    public void setFactionId(String factionId) { this.factionId = factionId; }

    public String getRoleId() { return roleId; }
    public void setRoleId(String roleId) { this.roleId = roleId; }

    public long getJoinedAt() { return joinedAt; }
    public void setJoinedAt(long joinedAt) { this.joinedAt = joinedAt; }
}

