package pt.henrique.serverfactions.model;

import java.util.UUID;

public class Faction {

    private String factionId;
    private String name;
    private String tag;
    private UUID leaderUuid;
    private String primaryColor;
    private String secondaryColor;
    private boolean isPublic;
    private int memberLimit;
    private boolean memberLimitEnabled;
    private long createdAt;
    private String bankAccountId;

    public Faction() {}

    public Faction(String factionId, String name, String tag, UUID leaderUuid,
                   String primaryColor, String secondaryColor, boolean isPublic,
                   int memberLimit, boolean memberLimitEnabled, long createdAt,
                   String bankAccountId) {
        this.factionId = factionId;
        this.name = name;
        this.tag = tag;
        this.leaderUuid = leaderUuid;
        this.primaryColor = primaryColor;
        this.secondaryColor = secondaryColor;
        this.isPublic = isPublic;
        this.memberLimit = memberLimit;
        this.memberLimitEnabled = memberLimitEnabled;
        this.createdAt = createdAt;
        this.bankAccountId = bankAccountId;
    }

    public String getFactionId() { return factionId; }
    public void setFactionId(String factionId) { this.factionId = factionId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }

    public UUID getLeaderUuid() { return leaderUuid; }
    public void setLeaderUuid(UUID leaderUuid) { this.leaderUuid = leaderUuid; }

    public String getPrimaryColor() { return primaryColor; }
    public void setPrimaryColor(String primaryColor) { this.primaryColor = primaryColor; }

    public String getSecondaryColor() { return secondaryColor; }
    public void setSecondaryColor(String secondaryColor) { this.secondaryColor = secondaryColor; }

    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean isPublic) { this.isPublic = isPublic; }

    public int getMemberLimit() { return memberLimit; }
    public void setMemberLimit(int memberLimit) { this.memberLimit = memberLimit; }

    public boolean isMemberLimitEnabled() { return memberLimitEnabled; }
    public void setMemberLimitEnabled(boolean memberLimitEnabled) { this.memberLimitEnabled = memberLimitEnabled; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public String getBankAccountId() { return bankAccountId; }
    public void setBankAccountId(String bankAccountId) { this.bankAccountId = bankAccountId; }
}

