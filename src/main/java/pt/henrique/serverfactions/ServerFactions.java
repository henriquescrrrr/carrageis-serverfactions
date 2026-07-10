package pt.henrique.serverfactions;

import pt.henrique.serverfactions.api.ServerFactionsApi;
import pt.henrique.serverfactions.api.ServerFactionsProvider;
import pt.henrique.serverfactions.command.FactionCommand;
import pt.henrique.serverfactions.config.ConfigManager;
import pt.henrique.serverfactions.config.GuiConfig;
import pt.henrique.serverfactions.config.LangManager;
import pt.henrique.serverfactions.economy.EconomyManager;
import pt.henrique.serverfactions.gui.GuiListener;
import pt.henrique.serverfactions.hook.MultiBankHook;
import pt.henrique.serverfactions.hook.PlaceholderApiHook;
import pt.henrique.serverfactions.hook.PlayerSettingsHook;
import pt.henrique.serverfactions.listener.ChatListener;
import pt.henrique.serverfactions.listener.FriendlyFireListener;
import pt.henrique.serverfactions.listener.PlayerConnectionListener;
import pt.henrique.serverfactions.manager.FactionManager;
import pt.henrique.serverfactions.storage.DatabaseManager;
import pt.henrique.serverfactions.util.SignInputUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class ServerFactions extends JavaPlugin {

    private static ServerFactions instance;
    private ConfigManager configManager;
    private GuiConfig guiConfig;
    private LangManager langManager;
    private EconomyManager economyManager;
    private PlayerSettingsHook playerSettingsHook;
    private MultiBankHook multiBankHook;
    private ServerFactionsProvider apiProvider;
    private DatabaseManager databaseManager;
    private FactionManager factionManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    @Override
    public void onEnable() {
        instance = this;

        // Save default configs
        saveDefaultConfig();
        saveResource("lang/en_US.yml", false);
        saveResource("lang/pt_PT.yml", false);
        saveResource("lang/fr_FR.yml", false);

        // Init config
        configManager = new ConfigManager(this);
        guiConfig = new GuiConfig(this);

        // Init hooks
        playerSettingsHook = new PlayerSettingsHook(this);
        multiBankHook = new MultiBankHook(this);

        // Init lang (depends on config + PlayerSettings hook)
        langManager = new LangManager(this, configManager, playerSettingsHook);

        // Init economy (depends on config)
        economyManager = new EconomyManager(this, configManager);

        // Init API provider and register in ServicesManager
        apiProvider = new ServerFactionsProvider();
        getServer().getServicesManager().register(
                ServerFactionsApi.class, apiProvider, this, ServicePriority.Normal
        );

        // Init database (SQLite)
        databaseManager = new DatabaseManager(this);

        // Init FactionManager (loads cache from DB, wires into API)
        factionManager = new FactionManager(this, databaseManager);
        apiProvider.setManager(factionManager);

        // Register permissions
        registerPermissions();

        // Register Brigadier commands
        FactionCommand.register(this);

        // Register listeners
        getServer().getPluginManager().registerEvents(new GuiListener(), this);
        getServer().getPluginManager().registerEvents(new SignInputUtil.SignInputListener(), this);
        getServer().getPluginManager().registerEvents(new ChatListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(), this);
        getServer().getPluginManager().registerEvents(new FriendlyFireListener(), this);

        // Schedule invite cleanup every 5 minutes (6000 ticks)
        Bukkit.getScheduler().runTaskTimer(this, () -> factionManager.cleanExpiredInvites(), 6000L, 6000L);

        // PlaceholderAPI hook
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderApiHook().register();
            getLogger().info("PlaceholderAPI hook: active");
        }

        getLogger().info("ServerFactions enabled!");
        getLogger().info("Economy provider: " + economyManager.getActiveProviderName());
        if (playerSettingsHook.isAvailable()) {
            getLogger().info("PlayerSettings hook: active");
        }
        if (multiBankHook.isAvailable()) {
            getLogger().info("MultiBank hook: active (faction bank accounts enabled)");
        }
    }

    private void registerPermissions() {
        try {
            Bukkit.getPluginManager().addPermission(
                    new Permission("serverfactions.use", "Basic faction commands", PermissionDefault.TRUE));
            Bukkit.getPluginManager().addPermission(
                    new Permission("serverfactions.admin", "Admin faction commands", PermissionDefault.OP));
        } catch (Exception ignored) {
            // Permissions already registered
        }
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
        instance = null;
        getLogger().info("ServerFactions disabled!");
    }

    public static ServerFactions getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public GuiConfig getGuiConfig() {
        return guiConfig;
    }

    public LangManager getLangManager() {
        return langManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public PlayerSettingsHook getPlayerSettingsHook() {
        return playerSettingsHook;
    }

    public MultiBankHook getMultiBankHook() {
        return multiBankHook;
    }

    public ServerFactionsProvider getApiProvider() {
        return apiProvider;
    }

    public MiniMessage getMiniMessage() {
        return miniMessage;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public FactionManager getFactionManager() {
        return factionManager;
    }
}
