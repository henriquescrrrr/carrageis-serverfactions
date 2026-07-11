package pt.henrique.serverfactions.config;

import pt.henrique.serverfactions.ServerFactions;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {

    private final ServerFactions plugin;

    // General
    private String language;
    private boolean debug;

    // Economy
    private String economyMode;
    private List<String> economyPriority;

    // Faction settings
    private double creationCost;
    private int maxTagLength;
    private int maxNameLength;
    private int minNameLength;
    private int inviteExpiryHours;
    private int defaultMemberLimit;
    private boolean defaultMemberLimitEnabled;
    private int absoluteMaxMembers;
    private int maxRoles;
    private boolean defaultPublic;
    private boolean friendlyFire;

    // Default roles
    private List<DefaultRole> defaultRoles;

    // MultiBank
    private boolean autoCreateBank;
    private String bankNamePrefix;

    // Sounds
    private Map<String, String> sounds;

    public ConfigManager(ServerFactions plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        language = config.getString("language", "pt_PT");
        debug = config.getBoolean("debug", false);

        economyMode = config.getString("economy.mode", "auto");
        economyPriority = config.getStringList("economy.priority");
        if (economyPriority.isEmpty()) {
            economyPriority = List.of("MultiBank", "Vault");
        }

        creationCost = config.getDouble("faction.creation-cost", 100.0);
        maxTagLength = config.getInt("faction.max-tag-length", 4);
        maxNameLength = config.getInt("faction.max-name-length", 24);
        minNameLength = config.getInt("faction.min-name-length", 3);
        inviteExpiryHours = config.getInt("faction.invite-expiry-hours", 24);
        defaultMemberLimit = config.getInt("faction.default-member-limit", 0);
        defaultMemberLimitEnabled = config.getBoolean("faction.default-member-limit-enabled", false);
        absoluteMaxMembers = config.getInt("faction.absolute-max-members", 50);
        maxRoles = config.getInt("faction.max-roles", 15);
        defaultPublic = config.getBoolean("faction.default-public", false);
        friendlyFire = config.getBoolean("faction.friendly-fire", true);

        // Load default roles
        defaultRoles = new ArrayList<>();
        List<Map<?, ?>> rolesList = config.getMapList("default-roles");
        for (Map<?, ?> roleMap : rolesList) {
            DefaultRole role = new DefaultRole();
            role.name = String.valueOf(roleMap.get("name") != null ? roleMap.get("name") : "Member");
            role.priority = roleMap.containsKey("priority")
                    ? ((Number) roleMap.get("priority")).intValue() : 1;
            role.canInvite = Boolean.TRUE.equals(roleMap.get("can-invite"));
            role.canKick = Boolean.TRUE.equals(roleMap.get("can-kick"));
            role.canEditSettings = Boolean.TRUE.equals(roleMap.get("can-edit-settings"));
            role.canManageRoles = Boolean.TRUE.equals(roleMap.get("can-manage-roles"));
            role.canPromote = Boolean.TRUE.equals(roleMap.get("can-promote"));
            role.canDemote = Boolean.TRUE.equals(roleMap.get("can-demote"));
            role.canAccessBank = Boolean.TRUE.equals(roleMap.get("can-access-bank"));
            defaultRoles.add(role);
        }

        autoCreateBank = config.getBoolean("multibank.auto-create-bank", true);
        bankNamePrefix = config.getString("multibank.bank-name-prefix", "Faction: ");

        sounds = new HashMap<>();
        ConfigurationSection soundSection = config.getConfigurationSection("sounds");
        if (soundSection != null) {
            for (String key : soundSection.getKeys(false)) {
                sounds.put(key, soundSection.getString(key));
            }
        }

        if (debug) {
            plugin.getLogger().info("[Debug] Config loaded: language=" + language
                    + ", creationCost=" + creationCost + ", roles=" + defaultRoles.size());
        }
    }

    // === Getters ===

    public String getLanguage() {
        return language;
    }

    public boolean isDebug() {
        return debug;
    }

    public String getEconomyMode() {
        return economyMode;
    }

    public List<String> getEconomyPriority() {
        return economyPriority;
    }

    public double getCreationCost() {
        return creationCost;
    }

    public int getMaxTagLength() {
        return maxTagLength;
    }

    public int getMaxNameLength() {
        return maxNameLength;
    }

    public int getMinNameLength() {
        return minNameLength;
    }

    public int getInviteExpiryHours() {
        return inviteExpiryHours;
    }

    public int getDefaultMemberLimit() {
        return defaultMemberLimit;
    }

    public boolean isDefaultMemberLimitEnabled() {
        return defaultMemberLimitEnabled;
    }

    public int getAbsoluteMaxMembers() {
        return absoluteMaxMembers;
    }

    public int getMaxRoles() {
        return maxRoles;
    }

    public boolean isDefaultPublic() {
        return defaultPublic;
    }

    public boolean isFriendlyFire() {
        return friendlyFire;
    }

    public List<DefaultRole> getDefaultRoles() {
        return defaultRoles;
    }

    public boolean isAutoCreateBank() {
        return autoCreateBank;
    }

    public String getBankNamePrefix() {
        return bankNamePrefix;
    }

    public String getSound(String key) {
        return sounds.get(key);
    }

    public Map<String, String> getSounds() {
        return sounds;
    }

    // === Inner class for default role templates ===

    public static class DefaultRole {
        public String name;
        public int priority;
        public boolean canInvite;
        public boolean canKick;
        public boolean canEditSettings;
        public boolean canManageRoles;
        public boolean canPromote;
        public boolean canDemote;
        public boolean canAccessBank;
    }
}


