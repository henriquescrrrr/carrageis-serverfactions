package pt.henrique.serverfactions.gui;

import pt.henrique.serverfactions.ServerFactions;
import pt.henrique.serverfactions.config.GuiConfig;
import pt.henrique.serverfactions.config.LangManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import static pt.henrique.serverfactions.gui.GuiItems.*;

/**
 * Step 3 of creation flow: select Public or Private visibility.
 * After selection → opens CreateConfirmGui.
 */
public final class VisibilitySelectGui {

    private static final String GUI = "visibility-select";

    private VisibilitySelectGui() {}

    public static void open(Player player, String name, String tag) {
        ServerFactions plugin = ServerFactions.getInstance();
        LangManager lang = plugin.getLangManager();
        GuiConfig gc = plugin.getGuiConfig();

        GuiHolder gui = new GuiHolder(gc.getSize(GUI), mm(lang.getRaw("gui-create-faction", player)));
        gui.fill();

        // Public
        gui.setItem(gc.getSlot(GUI, "public"), create(Material.LIME_STAINED_GLASS_PANE,
                lang.getRaw("gui-btn-visibility-public", player)), p -> {
            CreateConfirmGui.open(p, name, tag, true);
        });

        // Private
        gui.setItem(gc.getSlot(GUI, "private"), create(Material.RED_STAINED_GLASS_PANE,
                lang.getRaw("gui-btn-visibility-private", player)), p -> {
            CreateConfirmGui.open(p, name, tag, false);
        });

        gui.openFor(player);
    }
}
