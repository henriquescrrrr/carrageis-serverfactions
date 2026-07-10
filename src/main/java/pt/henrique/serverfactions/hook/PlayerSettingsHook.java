package pt.henrique.serverfactions.hook;

import pt.henrique.serverfactions.ServerFactions;
import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Hook into PlayerSettings plugin for per-player language preferences.
 * Uses reflection so the plugin compiles without PlayerSettings on classpath.
 */
public class PlayerSettingsHook {

    private final ServerFactions plugin;
    private Object apiInstance;
    private Method getLanguageMethod;
    private boolean available = false;

    public PlayerSettingsHook(ServerFactions plugin) {
        this.plugin = plugin;
        try {
            if (Bukkit.getPluginManager().getPlugin("PlayerSettings") != null) {
                Class<?> apiClass = Class.forName("pt.henrique.playersettings.api.PlayerSettingsApi");
                var rsp = Bukkit.getServicesManager().getRegistration(apiClass);
                if (rsp != null) {
                    apiInstance = rsp.getProvider();
                    getLanguageMethod = apiClass.getMethod("getLanguage", UUID.class);
                    available = true;
                    plugin.getLogger().info("PlayerSettings hook loaded.");
                }
            }
        } catch (ClassNotFoundException e) {
            plugin.getLogger().info("PlayerSettings not found, using config language.");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to hook into PlayerSettings: " + e.getMessage());
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Get the player's preferred language from PlayerSettings.
     *
     * @return locale string (e.g., "en_US", "pt_PT") or null if unavailable
     */
    public String getPlayerLanguage(UUID playerUuid) {
        if (!available) return null;
        try {
            Object result = getLanguageMethod.invoke(apiInstance, playerUuid);
            return result instanceof String s ? s : null;
        } catch (Exception e) {
            return null;
        }
    }
}

