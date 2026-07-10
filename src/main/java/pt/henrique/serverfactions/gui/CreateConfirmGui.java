package pt.henrique.serverfactions.gui;

import pt.henrique.serverfactions.ServerFactions;
import pt.henrique.serverfactions.config.ConfigManager;
import pt.henrique.serverfactions.config.GuiConfig;
import pt.henrique.serverfactions.config.LangManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import static pt.henrique.serverfactions.gui.GuiItems.*;

/**
 * Step 4 (final) of creation flow: confirm name, tag, visibility, and cost.
 */
public final class CreateConfirmGui {

    private static final String GUI = "create-confirm";

    private CreateConfirmGui() {}

    public static void open(Player player, String name, String tag, boolean isPublic) {
        ServerFactions plugin = ServerFactions.getInstance();
        LangManager lang = plugin.getLangManager();
        ConfigManager config = plugin.getConfigManager();
        GuiConfig gc = plugin.getGuiConfig();

        GuiHolder gui = new GuiHolder(gc.getSize(GUI), mm(lang.getRaw("gui-create-faction", player)));
        gui.fill();

        // Summary / preview
        String visibility = isPublic ? "<green>Public" : "<red>Private";
        double cost = config.getCreationCost();
        String costLine = cost > 0
                ? "<gray>Cost: <yellow>" + String.format("%.2f", cost)
                : "<gray>Cost: <green>Free";
        gui.setItem(gc.getSlot(GUI, "preview"), create(Material.BOOK,
                "<yellow>Confirm Faction Creation",
                "<gray>Name: <white>" + name,
                "<gray>Tag: <white>" + tag,
                "<gray>Visibility: " + visibility,
                costLine));

        // Confirm
        gui.setItem(gc.getSlot(GUI, "confirm"), create(Material.LIME_WOOL,
                lang.getRaw("gui-btn-confirm", player)), p -> {
            p.closeInventory();
            plugin.getFactionManager().createFaction(p, name, tag, isPublic);
        });

        // Cancel
        gui.setItem(gc.getSlot(GUI, "cancel"), create(Material.RED_WOOL,
                lang.getRaw("gui-btn-cancel", player)), p -> {
            MainMenuGui.open(p);
        });

        gui.openFor(player);
    }
}
