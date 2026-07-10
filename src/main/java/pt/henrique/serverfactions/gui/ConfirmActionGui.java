package pt.henrique.serverfactions.gui;

import pt.henrique.serverfactions.ServerFactions;
import pt.henrique.serverfactions.config.GuiConfig;
import pt.henrique.serverfactions.config.LangManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import static pt.henrique.serverfactions.gui.GuiItems.*;

/**
 * Generic confirmation GUI (leave, dissolve, kick, delete role, etc.).
 */
public final class ConfirmActionGui {

    private static final String GUI = "confirm-action";

    private ConfirmActionGui() {}

    public static void open(Player player, String title, String description, Runnable onConfirm) {
        ServerFactions plugin = ServerFactions.getInstance();
        LangManager lang = plugin.getLangManager();
        GuiConfig gc = plugin.getGuiConfig();

        GuiHolder gui = new GuiHolder(gc.getSize(GUI), mm(title));
        gui.fill();

        // Red wool — cancel
        for (String key : new String[]{"cancel-1", "cancel-2", "cancel-3"}) {
            gui.setItem(gc.getSlot(GUI, key), create(Material.RED_WOOL,
                    lang.getRaw("gui-btn-cancel", player)), p -> p.closeInventory());
        }

        // Preview
        gui.setItem(gc.getSlot(GUI, "preview"), create(Material.PAPER, description));

        // Green wool — confirm
        for (String key : new String[]{"confirm-1", "confirm-2", "confirm-3"}) {
            gui.setItem(gc.getSlot(GUI, key), create(Material.LIME_WOOL,
                    lang.getRaw("gui-btn-confirm", player)), p -> {
                p.closeInventory();
                onConfirm.run();
            });
        }

        gui.openFor(player);
    }
}
