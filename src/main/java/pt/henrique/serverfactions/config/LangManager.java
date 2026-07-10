package pt.henrique.serverfactions.config;

import pt.henrique.serverfactions.ServerFactions;
import pt.henrique.serverfactions.hook.PlayerSettingsHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class LangManager {

    private final ServerFactions plugin;
    private final String defaultLocale;
    private final PlayerSettingsHook psHook;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<String, YamlConfiguration> locales = new HashMap<>();

    public LangManager(ServerFactions plugin, ConfigManager config, PlayerSettingsHook psHook) {
        this.plugin = plugin;
        this.psHook = psHook;
        this.defaultLocale = config.getLanguage();
        reload();
    }

    public void reload() {
        locales.clear();

        // 1. Save default lang files from JAR if they don't exist on disk
        for (String loc : new String[]{"en_US", "pt_PT", "fr_FR"}) {
            plugin.saveResource("lang/" + loc + ".yml", false);
        }

        // 2. Load ALL .yml files from the lang/ folder on disk
        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (langFolder.isDirectory()) {
            File[] files = langFolder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.getName().endsWith(".yml")) {
                        String locale = file.getName().replace(".yml", "");
                        try {
                            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
                            // Merge with defaults from JAR (so new keys are available)
                            InputStream jarStream = plugin.getResource("lang/" + locale + ".yml");
                            if (jarStream != null) {
                                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                                        new InputStreamReader(jarStream, StandardCharsets.UTF_8));
                                yml.setDefaults(defaults);
                            }
                            locales.put(locale, yml);
                            plugin.getLogger().info("Loaded language: " + locale);
                        } catch (Exception e) {
                            plugin.getLogger().warning("Failed to load language file: " + locale + " — " + e.getMessage());
                        }
                    }
                }
            }
        }

        // 3. Ensure at least the default locale (pt_PT) exists
        if (locales.isEmpty()) {
            plugin.getLogger().severe("No language files loaded! Creating default pt_PT.");
            InputStream defaultStream = plugin.getResource("lang/pt_PT.yml");
            if (defaultStream != null) {
                locales.put("pt_PT", YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defaultStream, StandardCharsets.UTF_8)));
            }
        }

        if (!locales.containsKey(defaultLocale)) {
            plugin.getLogger().warning("Configured language '" + defaultLocale + "' not found. Using pt_PT.");
        }
    }

    /**
     * Get the locale for a specific player.
     * Tries PlayerSettings first, falls back to config default.
     */
    public String getPlayerLocale(Player player) {
        if (psHook != null && psHook.isAvailable()) {
            try {
                String lang = psHook.getPlayerLanguage(player.getUniqueId());
                if (lang != null && locales.containsKey(lang)) {
                    return lang;
                }
            } catch (Exception ignored) {}
        }
        return defaultLocale;
    }

    /**
     * Get raw (unparsed) message string for a player's locale.
     */
    public String getRaw(String key, Player player) {
        return getRawForLocale(key, getPlayerLocale(player));
    }

    /**
     * Get raw (unparsed) message string for the global locale.
     */
    public String getRaw(String key) {
        return getRawForLocale(key, defaultLocale);
    }

    /**
     * Get raw string for a specific locale.
     */
    public String getRawForLocale(String key, String locale) {
        YamlConfiguration yml = locales.get(locale);
        if (yml == null) yml = locales.get(defaultLocale);
        if (yml == null) yml = locales.get("pt_PT");
        if (yml == null) yml = locales.get("en_US");
        if (yml == null) return "<red>No language files loaded</red>";

        String value = yml.getString(key);
        if (value == null && yml.getDefaults() != null) {
            value = yml.getDefaults().getString(key);
        }
        if (value == null) return "<red>Missing: " + key + "</red>";
        return value;
    }

    /**
     * Get a parsed Component message for a player with placeholders (map-based).
     */
    public Component get(String key, Player player, Map<String, String> placeholders) {
        String raw = getRaw(key, player);
        String prefix = getRawForLocale("prefix", getPlayerLocale(player));
        raw = raw.replace("{prefix}", prefix);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return miniMessage.deserialize(raw);
    }

    /**
     * Get a parsed Component message for a player (no placeholders).
     */
    public Component get(String key, Player player) {
        return get(key, player, (Map<String, String>) null);
    }

    /**
     * Get a Component message with varargs replacements: key1, val1, key2, val2, ...
     */
    public Component getMessage(Player player, String key, String... replacements) {
        String raw = getRaw(key, player);
        String prefix = getRawForLocale("prefix", getPlayerLocale(player));
        raw = raw.replace("{prefix}", prefix);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            raw = raw.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return miniMessage.deserialize(raw);
    }

    /**
     * Get a Component message with map-based replacements (alternate signature).
     */
    public Component getMessage(Player player, String key, Map<String, String> replacements) {
        return get(key, player, replacements);
    }

    /**
     * Get a parsed Component message for the global locale with placeholders.
     */
    public Component get(String key, Map<String, String> placeholders) {
        String raw = getRaw(key);
        String prefix = getRawForLocale("prefix", defaultLocale);
        raw = raw.replace("{prefix}", prefix);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return miniMessage.deserialize(raw);
    }

    /**
     * Get a parsed Component message for the global locale (no placeholders).
     */
    public Component get(String key) {
        return get(key, (Map<String, String>) null);
    }
}

