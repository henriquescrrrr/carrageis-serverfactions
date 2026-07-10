package pt.henrique.serverfactions.gui;

import pt.henrique.serverfactions.ServerFactions;
import pt.henrique.serverfactions.config.GuiConfig;
import pt.henrique.serverfactions.config.LangManager;
import pt.henrique.serverfactions.manager.FactionManager;
import pt.henrique.serverfactions.model.Faction;
import pt.henrique.serverfactions.model.FactionRole;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static pt.henrique.serverfactions.gui.GuiItems.*;

/**
 * Select a role to assign to a target member.
 * Lists all roles the assigner can assign (priority > assigner's priority).
 */
public final class SelectRoleGui {

    private static final String GUI = "select-role";

    private SelectRoleGui() {}

    public static void open(Player viewer, UUID targetUuid) {
        ServerFactions plugin = ServerFactions.getInstance();
        LangManager lang = plugin.getLangManager();
        FactionManager fm = plugin.getFactionManager();
        GuiConfig gc = plugin.getGuiConfig();
        Faction faction = fm.getFactionByPlayer(viewer.getUniqueId());
        if (faction == null) return;

        String factionId = faction.getFactionId();
        FactionRole viewerRole = fm.getPlayerRole(viewer.getUniqueId());
        boolean isLeader = faction.getLeaderUuid().equals(viewer.getUniqueId());
        int viewerPriority = viewerRole != null ? viewerRole.getPriority() : Integer.MAX_VALUE;

        String targetName = Bukkit.getOfflinePlayer(targetUuid).getName();

        String title = lang.getRaw("gui-select-role", viewer)
                .replace("{player}", targetName != null ? targetName : "?");
        int pageSize = gc.getItemsPerPage(GUI);
        GuiHolder gui = new GuiHolder(gc.getSize(GUI), mm(title));
        gui.fillLastRow();

        // Get roles the viewer can assign
        List<FactionRole> roles = fm.getFactionRoles(factionId).stream()
                .filter(r -> !r.isLeaderRole())
                .filter(r -> isLeader || r.getPriority() > viewerPriority)
                .sorted(Comparator.comparingInt(FactionRole::getPriority))
                .toList();

        for (int i = 0; i < pageSize && i < roles.size(); i++) {
            FactionRole role = roles.get(i);
            int memberCount = fm.getRoleMemberCount(role.getRoleId());
            String permSummary = buildPermSummary(role);

            gui.setItem(i, create(Material.NAME_TAG,
                    "<yellow>" + role.getName(),
                    "<gray>Priority: <white>" + role.getPriority(),
                    "<gray>Members: <white>" + memberCount,
                    permSummary), p -> {
                fm.assignRole(p.getUniqueId(), targetUuid, role.getRoleId());
                MemberActionGui.open(p, targetUuid);
            });
        }

        // Back
        gui.setItem(gc.getSlot(GUI, "back"), create(Material.ARROW,
                lang.getRaw("gui-btn-back", viewer)), p -> MemberActionGui.open(p, targetUuid));

        // Close
        gui.setItem(gc.getSlot(GUI, "close"), create(Material.BARRIER,
                lang.getRaw("gui-btn-close", viewer)), p -> p.closeInventory());

        gui.openFor(viewer);
    }

    private static String buildPermSummary(FactionRole role) {
        StringBuilder sb = new StringBuilder();
        sb.append(role.isCanInvite() ? "<green>✔" : "<red>✘").append(" Invite ");
        sb.append(role.isCanKick() ? "<green>✔" : "<red>✘").append(" Kick ");
        sb.append(role.isCanEditSettings() ? "<green>✔" : "<red>✘").append(" Settings ");
        sb.append(role.isCanManageRoles() ? "<green>✔" : "<red>✘").append(" Roles");
        return sb.toString();
    }
}

