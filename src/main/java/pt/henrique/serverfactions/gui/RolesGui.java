package pt.henrique.serverfactions.gui;

import pt.henrique.serverfactions.ServerFactions;
import pt.henrique.serverfactions.config.GuiConfig;
import pt.henrique.serverfactions.config.LangManager;
import pt.henrique.serverfactions.manager.FactionManager;
import pt.henrique.serverfactions.model.Faction;
import pt.henrique.serverfactions.model.FactionRole;
import pt.henrique.serverfactions.util.SignInputUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static pt.henrique.serverfactions.gui.GuiItems.*;

/**
 * Lists all roles for the faction, sorted by priority ascending.
 * Paginated. Leader role has enchant glow and is not clickable.
 * Bottom row includes "Create Role" button.
 */
public final class RolesGui {

    private static final String GUI = "roles";

    private RolesGui() {}

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
        int pageSize = gc.getItemsPerPage(GUI);

        List<FactionRole> roles = fm.getFactionRoles(factionId).stream()
                .sorted(Comparator.comparingInt(FactionRole::getPriority))
                .toList();

        GuiHolder gui = new GuiHolder(gc.getSize(GUI), mm(lang.getRaw("gui-roles", player)));
        gui.fillLastRow(); // navigation row

        int start = page * pageSize;
        for (int i = 0; i < pageSize && (start + i) < roles.size(); i++) {
            FactionRole role = roles.get(start + i);
            int memberCount = fm.getRoleMemberCount(role.getRoleId());

            var item = create(Material.NAME_TAG,
                    "<yellow>" + role.getName(),
                    "<gray>Priority: <white>" + role.getPriority(),
                    "<gray>Members: <white>" + memberCount,
                    "",
                    buildPermList(role));

            if (role.isLeaderRole()) {
                withGlow(item);
                gui.setItem(i, item); // no click handler for leader role
            } else if (fm.canManageRole(player.getUniqueId(), role.getRoleId())) {
                gui.setItem(i, item, p -> {
                    RoleEditorGui.open(p, role.getRoleId());
                });
            } else {
                // Role at or above the viewer's own rank — visible but not editable.
                gui.setItem(i, item); // no click handler
            }
        }

        // Navigation row
        if (page > 0) {
            gui.setItem(gc.getSlot(GUI, "prev-page"), create(Material.ARROW,
                    lang.getRaw("gui-btn-prev-page", player)), p -> open(p, page - 1));
        }

        // Create Role button
        gui.setItem(gc.getSlot(GUI, "create-role"), create(Material.LIME_WOOL,
                lang.getRaw("gui-btn-create-role", player)), p -> {
            p.closeInventory();
            String prompt = lang.getRaw("sign-role-name", p);
            SignInputUtil.requestInput(plugin, p, prompt, roleName -> {
                if (roleName.isEmpty()) { RolesGui.open(p); return; }
                int maxPriority = roles.stream()
                        .mapToInt(FactionRole::getPriority).max().orElse(0);
                Map<String, Boolean> perms = new HashMap<>();
                fm.createRole(p.getUniqueId(), factionId, roleName, maxPriority + 1, perms);
                p.sendMessage(lang.get("faction-role-created", p, Map.of("role", roleName)));
                List<FactionRole> updatedRoles = fm.getFactionRoles(factionId);
                FactionRole newRole = updatedRoles.stream()
                        .filter(r -> r.getName().equals(roleName))
                        .findFirst().orElse(null);
                if (newRole != null) {
                    RoleEditorGui.open(p, newRole.getRoleId());
                } else {
                    RolesGui.open(p);
                }
            });
        });

        gui.setItem(gc.getSlot(GUI, "back"), create(Material.BARRIER,
                lang.getRaw("gui-btn-back", player)), p -> MainMenuGui.open(p));

        gui.setItem(gc.getSlot(GUI, "close"), create(Material.BARRIER,
                lang.getRaw("gui-btn-close", player)), p -> p.closeInventory());

        if ((page + 1) * pageSize < roles.size()) {
            gui.setItem(gc.getSlot(GUI, "next-page"), create(Material.ARROW,
                    lang.getRaw("gui-btn-next-page", player)), p -> open(p, page + 1));
        }

        gui.openFor(player);
    }

    private static String buildPermList(FactionRole role) {
        return (role.isCanInvite() ? "<green>✔" : "<red>✘") + " Invite  "
                + (role.isCanKick() ? "<green>✔" : "<red>✘") + " Kick  "
                + (role.isCanEditSettings() ? "<green>✔" : "<red>✘") + " Settings  "
                + (role.isCanManageRoles() ? "<green>✔" : "<red>✘") + " Roles  "
                + (role.isCanPromote() ? "<green>✔" : "<red>✘") + " Promote  "
                + (role.isCanDemote() ? "<green>✔" : "<red>✘") + " Demote  "
                + (role.isCanAccessBank() ? "<green>✔" : "<red>✘") + " Bank";
    }
}

