package pt.henrique.serverfactions.model;

public class FactionRole {

    private String roleId;
    private String factionId;
    private String name;
    private int priority;
    private boolean canInvite;
    private boolean canKick;
    private boolean canEditSettings;
    private boolean canManageRoles;
    private boolean canPromote;
    private boolean canDemote;
    private boolean canAccessBank;

    public FactionRole() {}

    public FactionRole(String roleId, String factionId, String name, int priority,
                       boolean canInvite, boolean canKick, boolean canEditSettings,
                       boolean canManageRoles, boolean canPromote, boolean canDemote,
                       boolean canAccessBank) {
        this.roleId = roleId;
        this.factionId = factionId;
        this.name = name;
        this.priority = priority;
        this.canInvite = canInvite;
        this.canKick = canKick;
        this.canEditSettings = canEditSettings;
        this.canManageRoles = canManageRoles;
        this.canPromote = canPromote;
        this.canDemote = canDemote;
        this.canAccessBank = canAccessBank;
    }

    /** Returns true if this is the Leader role (priority 0). */
    public boolean isLeaderRole() { return priority == 0; }

    public String getRoleId() { return roleId; }
    public void setRoleId(String roleId) { this.roleId = roleId; }

    public String getFactionId() { return factionId; }
    public void setFactionId(String factionId) { this.factionId = factionId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public boolean isCanInvite() { return canInvite; }
    public void setCanInvite(boolean canInvite) { this.canInvite = canInvite; }

    public boolean isCanKick() { return canKick; }
    public void setCanKick(boolean canKick) { this.canKick = canKick; }

    public boolean isCanEditSettings() { return canEditSettings; }
    public void setCanEditSettings(boolean canEditSettings) { this.canEditSettings = canEditSettings; }

    public boolean isCanManageRoles() { return canManageRoles; }
    public void setCanManageRoles(boolean canManageRoles) { this.canManageRoles = canManageRoles; }

    public boolean isCanPromote() { return canPromote; }
    public void setCanPromote(boolean canPromote) { this.canPromote = canPromote; }

    public boolean isCanDemote() { return canDemote; }
    public void setCanDemote(boolean canDemote) { this.canDemote = canDemote; }

    public boolean isCanAccessBank() { return canAccessBank; }
    public void setCanAccessBank(boolean canAccessBank) { this.canAccessBank = canAccessBank; }

    /** Check a permission by string key. */
    public boolean hasPermission(String permission) {
        return switch (permission) {
            case "canInvite" -> canInvite;
            case "canKick" -> canKick;
            case "canEditSettings" -> canEditSettings;
            case "canManageRoles" -> canManageRoles;
            case "canPromote" -> canPromote;
            case "canDemote" -> canDemote;
            case "canAccessBank" -> canAccessBank;
            default -> false;
        };
    }
}

