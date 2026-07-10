package pt.henrique.serverfactions.config;

import pt.henrique.serverfactions.ServerFactions;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.Set;

/**
 * Reads GUI sizes and slot positions from config.yml's "gui" section.
 * Every GUI class reads its layout from here instead of hardcoding slot numbers.
 */
public class GuiConfig {

    private final ServerFactions plugin;
    private FileConfiguration config;
    private final Set<String> warnedKeys = new HashSet<>();

    public GuiConfig(ServerFactions plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
    }

    /**
     * Get the raw inventory size (number of slots, e.g. 9, 18, 27, 54).
     */
    public int getSize(String guiName) {
        return config.getInt("gui." + guiName + ".size", 27);
    }

    /**
     * Get the slot index for a named slot in a GUI.
     * Returns -1 if not configured (the item simply won't be placed).
     */
    public int getSlot(String guiName, String slotName) {
        String path = "gui." + guiName + ".slots." + slotName;
        int slot = config.getInt(path, -1);
        if (slot == -1) {
            String key = guiName + "." + slotName;
            if (warnedKeys.add(key)) {
                plugin.getLogger().warning("[GuiConfig] Slot not configured: " + path);
            }
        }
        return slot;
    }

    /**
     * Get items-per-page for paginated GUIs.
     */
    public int getItemsPerPage(String guiName) {
        return config.getInt("gui." + guiName + ".items-per-page", 45);
    }

    /**
     * Reload with fresh config reference.
     */
    public void reload() {
        this.config = plugin.getConfig();
        warnedKeys.clear();
    }
}

