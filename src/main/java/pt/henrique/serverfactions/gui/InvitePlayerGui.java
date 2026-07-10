package pt.henrique.serverfactions.gui;

import pt.henrique.serverfactions.ServerFactions;
import pt.henrique.serverfactions.config.GuiConfig;
import pt.henrique.serverfactions.config.LangManager;
import pt.henrique.serverfactions.manager.FactionManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

import static pt.henrique.serverfactions.gui.GuiItems.*;

/**
 * Select an online player to invite to your faction.
 * Shows heads of online players not in any faction. Paginated.
 */
public final class InvitePlayerGui {

    private static final String GUI = "invite-player";

    private InvitePlayerGui() {}

    public static void open(Player player) {
        open(player, 0);
    }

    public static void open(Player player, int page) {
        ServerFactions plugin = ServerFactions.getInstance();
        LangManager lang = plugin.getLangManager();
        FactionManager fm = plugin.getFactionManager();
        GuiConfig gc = plugin.getGuiConfig();
        int pageSize = gc.getItemsPerPage(GUI);

        List<Player> candidates = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(player)) continue;
            if (!fm.isInFaction(online.getUniqueId())) {
                candidates.add(online);
            }
        }

        GuiHolder gui = new GuiHolder(gc.getSize(GUI), mm(lang.getRaw("gui-invite-player", player)));
        gui.fillLastRow();

        int start = page * pageSize;
        for (int i = 0; i < pageSize && (start + i) < candidates.size(); i++) {
            Player target = candidates.get(start + i);
            gui.setItem(i, createHead(target,
                    "<yellow>" + target.getName(),
                    "<gray>Click to invite"), p -> {
                String factionId = fm.getFactionIdByPlayer(p.getUniqueId());
                if (factionId == null) return;
                fm.invitePlayer(p.getUniqueId(), target.getUniqueId(), factionId);
                InvitePlayerGui.open(p, page);
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
        if ((page + 1) * pageSize < candidates.size()) {
            gui.setItem(gc.getSlot(GUI, "next-page"), create(Material.ARROW,
                    lang.getRaw("gui-btn-next-page", player)), p -> open(p, page + 1));
        }

        gui.openFor(player);
    }
}
