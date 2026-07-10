package pt.henrique.serverfactions.gui;

import pt.henrique.serverfactions.ServerFactions;
import pt.henrique.serverfactions.config.GuiConfig;
import pt.henrique.serverfactions.config.LangManager;
import pt.henrique.serverfactions.manager.FactionManager;
import pt.henrique.serverfactions.model.Faction;
import pt.henrique.serverfactions.model.FactionMember;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static pt.henrique.serverfactions.gui.GuiItems.*;

/**
 * Paginated member selector for transferring leadership.
 * Shows all faction members except the leader.
 */
public final class TransferLeadershipGui {

    private static final String GUI = "transfer-leadership";

    private TransferLeadershipGui() {}

    public static void open(Player player) {
        open(player, 0);
    }

    public static void open(Player player, int page) {
        ServerFactions plugin = ServerFactions.getInstance();
        LangManager lang = plugin.getLangManager();
        FactionManager fm = plugin.getFactionManager();
        GuiConfig gc = plugin.getGuiConfig();
        Faction faction = fm.getFactionByPlayer(player.getUniqueId());
        if (faction == null) return;
        if (!faction.getLeaderUuid().equals(player.getUniqueId())) return;

        String factionId = faction.getFactionId();
        int pageSize = gc.getItemsPerPage(GUI);

        Collection<FactionMember> allMembers = fm.getFactionMembersList(factionId);
        List<FactionMember> candidates = new ArrayList<>();
        for (FactionMember m : allMembers) {
            if (!m.getPlayerUuid().equals(player.getUniqueId())) {
                candidates.add(m);
            }
        }

        GuiHolder gui = new GuiHolder(gc.getSize(GUI), mm(lang.getRaw("gui-transfer-leadership", player)));
        gui.fillLastRow();

        int start = page * pageSize;
        for (int i = 0; i < pageSize && (start + i) < candidates.size(); i++) {
            FactionMember member = candidates.get(start + i);
            UUID targetUuid = member.getPlayerUuid();
            String targetName = Bukkit.getOfflinePlayer(targetUuid).getName();
            var role = fm.getPlayerRole(targetUuid);
            String roleName = role != null ? role.getName() : "?";

            gui.setItem(i, createHead(Bukkit.getOfflinePlayer(targetUuid),
                    "<yellow>" + (targetName != null ? targetName : "?"),
                    "<gray>Role: <white>" + roleName,
                    "",
                    "<gold>Click to transfer leadership"), p -> {
                ConfirmActionGui.open(p,
                        lang.getRaw("gui-transfer-leadership", p),
                        "<gold>Transfer leadership to <white>" + (targetName != null ? targetName : "?")
                                + "</white>? <red>This cannot be undone!",
                        () -> {
                            fm.transferLeadership(p.getUniqueId(), targetUuid);
                            MainMenuGui.open(p);
                        });
            });
        }

        // Navigation
        if (page > 0) {
            gui.setItem(gc.getSlot(GUI, "prev-page"), create(Material.ARROW,
                    lang.getRaw("gui-btn-prev-page", player)), p -> open(p, page - 1));
        }
        gui.setItem(gc.getSlot(GUI, "back"), create(Material.BARRIER,
                lang.getRaw("gui-btn-back", player)), p -> SettingsGui.open(p));
        gui.setItem(gc.getSlot(GUI, "close"), create(Material.BARRIER,
                lang.getRaw("gui-btn-close", player)), p -> p.closeInventory());
        if ((page + 1) * pageSize < candidates.size()) {
            gui.setItem(gc.getSlot(GUI, "next-page"), create(Material.ARROW,
                    lang.getRaw("gui-btn-next-page", player)), p -> open(p, page + 1));
        }

        gui.openFor(player);
    }
}

