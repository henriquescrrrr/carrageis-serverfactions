package pt.henrique.serverfactions.gui;

import pt.henrique.serverfactions.ServerFactions;
import pt.henrique.serverfactions.config.GuiConfig;
import pt.henrique.serverfactions.config.LangManager;
import pt.henrique.serverfactions.manager.FactionManager;
import pt.henrique.serverfactions.model.Faction;
import pt.henrique.serverfactions.model.FactionMember;
import pt.henrique.serverfactions.model.FactionRole;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.*;

import static pt.henrique.serverfactions.gui.GuiItems.*;

/**
 * Shows all faction members as player heads. Paginated.
 * Click a member → MemberActionGui (if you have permission).
 */
public final class MembersGui {

    private static final String GUI = "members";

    private MembersGui() {}

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

        String factionId = faction.getFactionId();
        Collection<FactionMember> allMembers = fm.getFactionMembersList(factionId);
        int pageSize = gc.getItemsPerPage(GUI);

        // Sort: leader first, then by role priority, then by name
        List<FactionMember> sorted = new ArrayList<>(allMembers);
        sorted.sort((a, b) -> {
            FactionRole ra = fm.getPlayerRole(a.getPlayerUuid());
            FactionRole rb = fm.getPlayerRole(b.getPlayerUuid());
            int pa = ra != null ? ra.getPriority() : Integer.MAX_VALUE;
            int pb = rb != null ? rb.getPriority() : Integer.MAX_VALUE;
            return Integer.compare(pa, pb);
        });

        GuiHolder gui = new GuiHolder(gc.getSize(GUI), mm(lang.getRaw("gui-members", player)));
        gui.fillLastRow(); // navigation row

        int start = page * pageSize;
        for (int i = 0; i < pageSize && (start + i) < sorted.size(); i++) {
            FactionMember member = sorted.get(start + i);
            UUID memberUuid = member.getPlayerUuid();
            String memberName = Bukkit.getOfflinePlayer(memberUuid).getName();
            FactionRole role = fm.getPlayerRole(memberUuid);
            String roleName = role != null ? role.getName() : "?";
            boolean isOnline = Bukkit.getPlayer(memberUuid) != null;
            String joinDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date(member.getJoinedAt()));
            boolean isFactionLeader = faction.getLeaderUuid().equals(memberUuid);

            var head = createHead(Bukkit.getOfflinePlayer(memberUuid),
                    "<yellow>" + (memberName != null ? memberName : "?"),
                    "<gray>Role: <white>" + roleName,
                    "<gray>Joined: <white>" + joinDate,
                    isOnline ? "<green>● Online" : "<red>● Offline");

            if (isFactionLeader) withGlow(head);

            gui.setItem(i, head, p -> {
                if (memberUuid.equals(p.getUniqueId())) return;
                MemberActionGui.open(p, memberUuid);
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

        if ((page + 1) * pageSize < sorted.size()) {
            gui.setItem(gc.getSlot(GUI, "next-page"), create(Material.ARROW,
                    lang.getRaw("gui-btn-next-page", player)), p -> open(p, page + 1));
        }

        gui.openFor(player);
    }
}

