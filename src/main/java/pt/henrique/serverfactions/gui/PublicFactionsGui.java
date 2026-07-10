package pt.henrique.serverfactions.gui;

import pt.henrique.serverfactions.ServerFactions;
import pt.henrique.serverfactions.config.GuiConfig;
import pt.henrique.serverfactions.config.LangManager;
import pt.henrique.serverfactions.manager.FactionManager;
import pt.henrique.serverfactions.model.Faction;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

import static pt.henrique.serverfactions.gui.GuiItems.*;

/**
 * Browse public factions. Click to view details. Paginated.
 */
public final class PublicFactionsGui {

    private static final String GUI = "public-factions";

    private PublicFactionsGui() {}

    public static void open(Player player) {
        open(player, 0);
    }

    public static void open(Player player, int page) {
        ServerFactions plugin = ServerFactions.getInstance();
        LangManager lang = plugin.getLangManager();
        FactionManager fm = plugin.getFactionManager();
        GuiConfig gc = plugin.getGuiConfig();
        int pageSize = gc.getItemsPerPage(GUI);
        List<Faction> factions = fm.getPublicFactions();

        GuiHolder gui = new GuiHolder(gc.getSize(GUI), mm(lang.getRaw("gui-public-factions", player)));
        gui.fillLastRow();

        int start = page * pageSize;
        for (int i = 0; i < pageSize && (start + i) < factions.size(); i++) {
            Faction f = factions.get(start + i);
            int members = fm.getMemberCount(f.getFactionId());
            String max = f.isMemberLimitEnabled() ? String.valueOf(f.getMemberLimit()) : "∞";
            String leaderName = Bukkit.getOfflinePlayer(f.getLeaderUuid()).getName();

            final String factionId = f.getFactionId();
            gui.setItem(i, create(Material.WHITE_BANNER,
                    fm.getFormattedTag(factionId) + " " + f.getName(),
                    "<gray>Leader: <white>" + (leaderName != null ? leaderName : "?"),
                    "<gray>Members: <white>" + members + "/" + max,
                    "",
                    "<yellow>Click to view details"), p -> {
                PublicFactionDetailGui.open(p, factionId);
            });
        }

        // Navigation
        if (page > 0) {
            gui.setItem(gc.getSlot(GUI, "prev-page"), create(Material.ARROW,
                    lang.getRaw("gui-btn-prev-page", player)), p -> open(p, page - 1));
        }
        gui.setItem(gc.getSlot(GUI, "back"), create(Material.BARRIER,
                lang.getRaw("gui-btn-back", player)), p -> MainMenuGui.open(p));
        gui.setItem(gc.getSlot(GUI, "close"), create(Material.BARRIER,
                lang.getRaw("gui-btn-close", player)), p -> p.closeInventory());
        if ((page + 1) * pageSize < factions.size()) {
            gui.setItem(gc.getSlot(GUI, "next-page"), create(Material.ARROW,
                    lang.getRaw("gui-btn-next-page", player)), p -> open(p, page + 1));
        }

        gui.openFor(player);
    }
}
