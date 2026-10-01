package pt.henrique.serverfactions.gui;

import pt.henrique.serverfactions.ServerFactions;
import pt.henrique.serverfactions.config.GuiConfig;
import pt.henrique.serverfactions.config.LangManager;
import pt.henrique.serverfactions.manager.FactionManager;
import pt.henrique.serverfactions.model.Faction;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import static pt.henrique.serverfactions.gui.GuiItems.*;

/**
 * Color picker for primary or secondary faction tag color.
 * Colors fill rows 0-1 (slots 0-15), controls in last row.
 */
public final class ColorPickerGui {

    private static final String GUI = "color-picker";

    private ColorPickerGui() {}

    // Color definitions: Material, MiniMessage tag, display name
    private static final ColorEntry[] ROW_0 = {
            new ColorEntry(Material.BLACK_WOOL, "<black>", "Black"),
            new ColorEntry(Material.BLUE_WOOL, "<dark_blue>", "Dark Blue"),
            new ColorEntry(Material.GREEN_WOOL, "<dark_green>", "Dark Green"),
            new ColorEntry(Material.CYAN_WOOL, "<dark_aqua>", "Dark Aqua"),
            new ColorEntry(Material.RED_WOOL, "<dark_red>", "Dark Red"),
            new ColorEntry(Material.PURPLE_WOOL, "<dark_purple>", "Dark Purple"),
            new ColorEntry(Material.ORANGE_WOOL, "<gold>", "Gold"),
            new ColorEntry(Material.LIGHT_GRAY_WOOL, "<gray>", "Gray"),
    };

    private static final ColorEntry[] ROW_1 = {
            new ColorEntry(Material.GRAY_WOOL, "<dark_gray>", "Dark Gray"),
            new ColorEntry(Material.LIGHT_BLUE_WOOL, "<blue>", "Blue"),
            new ColorEntry(Material.LIME_WOOL, "<green>", "Green"),
            new ColorEntry(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "<aqua>", "Aqua"),
            new ColorEntry(Material.PINK_WOOL, "<red>", "Red"),
            new ColorEntry(Material.MAGENTA_WOOL, "<light_purple>", "Light Purple"),
            new ColorEntry(Material.YELLOW_WOOL, "<yellow>", "Yellow"),
            new ColorEntry(Material.WHITE_WOOL, "<white>", "White"),
    };

    /**
     * @param isPrimary true = editing primary color, false = secondary
     */
    public static void open(Player player, boolean isPrimary) {
        ServerFactions plugin = ServerFactions.getInstance();
        LangManager lang = plugin.getLangManager();
        FactionManager fm = plugin.getFactionManager();
        GuiConfig gc = plugin.getGuiConfig();
        Faction faction = fm.getFactionByPlayer(player.getUniqueId());
        if (faction == null) return;

        // Reachable only from SettingsGui today, but re-gate here so the menu can
        // never be re-entered by a player whose permission was revoked meanwhile.
        if (!fm.canEditSettings(player.getUniqueId())) {
            player.sendMessage(lang.get("faction-no-permission-settings", player));
            return;
        }

        String factionId = faction.getFactionId();
        String title = lang.getRaw("gui-color-picker", player);
        GuiHolder gui = new GuiHolder(gc.getSize(GUI), mm(title));
        gui.fill();

        // Row 0: first 8 colors (slots 0-7)
        for (int i = 0; i < ROW_0.length; i++) {
            ColorEntry c = ROW_0[i];
            gui.setItem(i, create(c.material,
                    c.tag + c.displayName,
                    "<gray>Tag: <white>" + c.tag), p -> {
                applyColor(p, fm, factionId, isPrimary, c.tag);
                ColorPickerGui.open(p, isPrimary);
            });
        }

        // Row 1: next 8 colors (slots 9-16)
        for (int i = 0; i < ROW_1.length; i++) {
            ColorEntry c = ROW_1[i];
            gui.setItem(9 + i, create(c.material,
                    c.tag + c.displayName,
                    "<gray>Tag: <white>" + c.tag), p -> {
                applyColor(p, fm, factionId, isPrimary, c.tag);
                ColorPickerGui.open(p, isPrimary);
            });
        }

        // Back
        gui.setItem(gc.getSlot(GUI, "back"), create(Material.ARROW,
                lang.getRaw("gui-btn-back", player)), p -> SettingsGui.open(p));

        // Preview
        String preview = fm.getFormattedTag(factionId);
        gui.setItem(gc.getSlot(GUI, "preview"), create(Material.PAPER,
                "<yellow>Preview: " + preview,
                isPrimary ? "<gray>Editing: <white>Primary Color" : "<gray>Editing: <white>Secondary Color"));

        // Close
        gui.setItem(gc.getSlot(GUI, "close"), create(Material.BARRIER,
                lang.getRaw("gui-btn-close", player)), p -> p.closeInventory());

        gui.openFor(player);
    }

    private static void applyColor(Player player, FactionManager fm, String factionId,
                                    boolean isPrimary, String colorTag) {
        boolean applied = isPrimary
                ? fm.setPrimaryColor(player.getUniqueId(), factionId, colorTag)
                : fm.setSecondaryColor(player.getUniqueId(), factionId, colorTag);
        if (!applied) return;

        player.sendMessage(ServerFactions.getInstance().getLangManager()
                .get("faction-color-changed", player));
        String soundName = ServerFactions.getInstance().getConfigManager().getSound("settings-saved");
        if (soundName != null) {
            try {
                player.playSound(Sound.sound(
                        NamespacedKey.fromString(soundName),
                        Sound.Source.MASTER, 1.0f, 1.0f));
            } catch (Exception ignored) {}
        }
    }

    private record ColorEntry(Material material, String tag, String displayName) {}
}

