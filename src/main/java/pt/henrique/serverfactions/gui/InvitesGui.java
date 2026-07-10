package pt.henrique.serverfactions.gui;

import pt.henrique.serverfactions.ServerFactions;
import pt.henrique.serverfactions.config.GuiConfig;
import pt.henrique.serverfactions.config.LangManager;
import pt.henrique.serverfactions.manager.FactionManager;
import pt.henrique.serverfactions.model.Faction;
import pt.henrique.serverfactions.model.FactionInvite;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;

import static pt.henrique.serverfactions.gui.GuiItems.*;

/**
 * View pending invites for the player.
 * Left-click to accept, Right-click to decline.
 * Paginated.
 */
public final class InvitesGui {

    private static final String GUI = "invites";

    private InvitesGui() {}

    public static void open(Player player) {
        open(player, 0);
    }

    public static void open(Player player, int page) {
        ServerFactions plugin = ServerFactions.getInstance();
        LangManager lang = plugin.getLangManager();
        FactionManager fm = plugin.getFactionManager();
        GuiConfig gc = plugin.getGuiConfig();
        int pageSize = gc.getItemsPerPage(GUI);
        List<FactionInvite> invites = fm.getPlayerInvites(player.getUniqueId());

        GuiHolder gui = new GuiHolder(gc.getSize(GUI), mm(lang.getRaw("gui-invites", player)));
        gui.fillLastRow();

        int start = page * pageSize;
        for (int i = 0; i < pageSize && (start + i) < invites.size(); i++) {
            FactionInvite inv = invites.get(start + i);
            Faction faction = fm.getFaction(inv.getFactionId());
            if (faction == null) continue;

            String invitedBy = Bukkit.getOfflinePlayer(inv.getInvitedByUuid()).getName();
            long remainMs = inv.getExpiresAt() - System.currentTimeMillis();
            String expires = remainMs > 0 ? formatTime(remainMs) : "<red>Expired";

            final String factionId = inv.getFactionId();
            gui.setItem(i, create(Material.WRITABLE_BOOK,
                    "<yellow>" + faction.getName() + " <gray>[" + faction.getTag() + "]",
                    "<gray>Invited by: <white>" + (invitedBy != null ? invitedBy : "?"),
                    "<gray>Expires in: <white>" + expires,
                    "",
                    "<green>Left-click to ACCEPT",
                    "<red>Right-click to DECLINE"), (p, click) -> {
                p.closeInventory();
                if (click == ClickType.LEFT || click == ClickType.SHIFT_LEFT) {
                    fm.acceptInvite(p.getUniqueId(), factionId);
                } else {
                    fm.declineInvite(p.getUniqueId(), factionId);
                }
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
        if ((page + 1) * pageSize < invites.size()) {
            gui.setItem(gc.getSlot(GUI, "next-page"), create(Material.ARROW,
                    lang.getRaw("gui-btn-next-page", player)), p -> open(p, page + 1));
        }

        gui.openFor(player);
    }

    private static String formatTime(long ms) {
        long hours = ms / 3600000;
        long minutes = (ms % 3600000) / 60000;
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }
}
