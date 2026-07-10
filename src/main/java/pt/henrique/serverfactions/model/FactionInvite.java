package pt.henrique.serverfactions.model;

import java.util.UUID;

public class FactionInvite {

    private UUID invitedUuid;
    private String factionId;
    private UUID invitedByUuid;
    private long createdAt;
    private long expiresAt;

    public FactionInvite() {}

    public FactionInvite(UUID invitedUuid, String factionId, UUID invitedByUuid,
                         long createdAt, long expiresAt) {
        this.invitedUuid = invitedUuid;
        this.factionId = factionId;
        this.invitedByUuid = invitedByUuid;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }

    public UUID getInvitedUuid() { return invitedUuid; }
    public void setInvitedUuid(UUID invitedUuid) { this.invitedUuid = invitedUuid; }

    public String getFactionId() { return factionId; }
    public void setFactionId(String factionId) { this.factionId = factionId; }

    public UUID getInvitedByUuid() { return invitedByUuid; }
    public void setInvitedByUuid(UUID invitedByUuid) { this.invitedByUuid = invitedByUuid; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }
}

