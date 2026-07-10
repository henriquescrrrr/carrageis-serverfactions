package pt.henrique.serverfactions.gui;

import pt.henrique.serverfactions.ServerFactions;
import pt.henrique.serverfactions.config.GuiConfig;
import pt.henrique.serverfactions.config.LangManager;
import pt.henrique.serverfactions.manager.FactionManager;
import pt.henrique.serverfactions.model.FactionRole;
import pt.henrique.serverfactions.util.SignInputUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

import static pt.henrique.serverfactions.gui.GuiItems.*;

/**
 * Edit a specific role's name, priority, and permissions.
 * All changes are accumulated locally and saved on "Save" click.
 */
public final class RoleEditorGui {

    private static final String GUI = "role-editor";

    private RoleEditorGui() {}

    public static void open(Player player, String roleId) {
        open(player, roleId, null);
    }

    public static void open(Player player, String roleId, RoleState override) {
        ServerFactions plugin = ServerFactions.getInstance();
        LangManager lang = plugin.getLangManager();
        FactionManager fm = plugin.getFactionManager();
        GuiConfig gc = plugin.getGuiConfig();

        FactionRole role = fm.findRoleById(roleId);
        if (role == null || role.isLeaderRole()) return;

        // Authorization: only the leader, or a canManageRoles member acting on a role
        // strictly below their own rank, may edit this role. Prevents a "trusted" role
        // from editing its own permissions or a superior's (privilege escalation).
        if (!fm.canManageRole(player.getUniqueId(), roleId)) {
            player.sendMessage(lang.get("faction-no-permission-roles", player));
            return;
        }

        RoleState state = override != null ? override : RoleState.from(role);

        String title = lang.getRaw("gui-role-editor", player).replace("{role}", state.name);
        GuiHolder gui = new GuiHolder(gc.getSize(GUI), mm(title));
        gui.fill();

        // Role name (click to rename)
        gui.setItem(gc.getSlot(GUI, "role-name"), create(Material.NAME_TAG,
                "<yellow>" + state.name,
                lang.getRaw("gui-btn-edit-name", player)), p -> {
            p.closeInventory();
            String prompt = lang.getRaw("sign-role-name", p);
            SignInputUtil.requestInput(plugin, p, prompt, newName -> {
                if (!newName.isEmpty()) {
                    state.name = newName;
                }
                open(p, roleId, state);
            });
        });

        // Priority down
        gui.setItem(gc.getSlot(GUI, "priority-down"), create(Material.RED_STAINED_GLASS_PANE,
                "<red>Priority -1",
                "<gray>Current: <white>" + state.priority), p -> {
            if (state.priority > 1) {
                state.priority--;
            }
            open(p, roleId, state);
        });

        // Priority display
        gui.setItem(gc.getSlot(GUI, "priority-display"), create(Material.PAPER,
                lang.getRaw("gui-btn-role-priority", player)
                        .replace("{priority}", String.valueOf(state.priority)),
                "<gray>Lower = higher rank",
                "<gray>Leader is always 0"));

        // Priority up
        gui.setItem(gc.getSlot(GUI, "priority-up"), create(Material.LIME_STAINED_GLASS_PANE,
                "<green>Priority +1",
                "<gray>Current: <white>" + state.priority), p -> {
            state.priority++;
            open(p, roleId, state);
        });

        // Delete role
        int memberCount = fm.getRoleMemberCount(roleId);
        if (memberCount > 0) {
            gui.setItem(gc.getSlot(GUI, "delete"), locked("<red>Delete Role",
                    "<red>Can't delete — " + memberCount + " members have this role.",
                    "<red>Reassign them first."));
        } else {
            gui.setItem(gc.getSlot(GUI, "delete"), create(Material.BARRIER,
                    lang.getRaw("gui-btn-delete-role", player)), p -> {
                ConfirmActionGui.open(p,
                        "<dark_gray>Delete Role?",
                        "<red>Delete role <white>" + state.name + "</white>?",
                        () -> {
                            fm.deleteRole(p.getUniqueId(), roleId);
                            p.sendMessage(lang.get("faction-role-deleted", p,
                                    Map.of("role", state.name)));
                            RolesGui.open(p);
                        });
            });
        }

        // Permission toggles
        addPermToggle(gui, gc.getSlot(GUI, "perm-invite"), "canInvite", "Invite Players",
                "Members with this role can invite players.", state, roleId, player);
        addPermToggle(gui, gc.getSlot(GUI, "perm-kick"), "canKick", "Kick Members",
                "Members with this role can kick lower-ranked members.", state, roleId, player);
        addPermToggle(gui, gc.getSlot(GUI, "perm-settings"), "canEditSettings", "Edit Settings",
                "Members with this role can change faction settings.", state, roleId, player);
        addPermToggle(gui, gc.getSlot(GUI, "perm-roles"), "canManageRoles", "Manage Roles",
                "Members with this role can create/edit/delete roles.", state, roleId, player);
        addPermToggle(gui, gc.getSlot(GUI, "perm-promote"), "canPromote", "Promote Members",
                "Members with this role can promote lower-ranked members.", state, roleId, player);
        addPermToggle(gui, gc.getSlot(GUI, "perm-demote"), "canDemote", "Demote Members",
                "Members with this role can demote lower-ranked members.", state, roleId, player);
        addPermToggle(gui, gc.getSlot(GUI, "perm-bank"), "canAccessBank", "Access Bank",
                "Members with this role can access the faction bank.", state, roleId, player);

        // Back
        gui.setItem(gc.getSlot(GUI, "back"), create(Material.ARROW,
                lang.getRaw("gui-btn-back", player)), p -> RolesGui.open(p));

        // Save
        gui.setItem(gc.getSlot(GUI, "save"), create(Material.LIME_WOOL,
                "<green>Save"), p -> {
            fm.editRole(p.getUniqueId(), roleId, state.name, state.priority, state.toPermMap());
            p.sendMessage(lang.get("faction-settings-saved", p));
            RolesGui.open(p);
        });


        gui.openFor(player);
    }

    private static void addPermToggle(GuiHolder gui, int slot, String perm, String displayName,
                                       String description, RoleState state,
                                       String roleId, Player player) {
        boolean enabled = state.getPerm(perm);
        Material mat = enabled ? Material.LIME_WOOL : Material.RED_WOOL;
        String prefix = enabled ? "<green>✔ " : "<red>✘ ";

        gui.setItem(slot, create(mat,
                prefix + displayName,
                "<gray>" + description), p -> {
            state.setPerm(perm, !enabled);
            open(p, roleId, state);
        });
    }

    /**
     * Mutable state for editing a role before saving.
     */
    static class RoleState {
        String name;
        int priority;
        boolean canInvite, canKick, canEditSettings, canManageRoles;
        boolean canPromote, canDemote, canAccessBank;

        static RoleState from(FactionRole role) {
            RoleState s = new RoleState();
            s.name = role.getName();
            s.priority = role.getPriority();
            s.canInvite = role.isCanInvite();
            s.canKick = role.isCanKick();
            s.canEditSettings = role.isCanEditSettings();
            s.canManageRoles = role.isCanManageRoles();
            s.canPromote = role.isCanPromote();
            s.canDemote = role.isCanDemote();
            s.canAccessBank = role.isCanAccessBank();
            return s;
        }

        boolean getPerm(String perm) {
            return switch (perm) {
                case "canInvite" -> canInvite;
                case "canKick" -> canKick;
                case "canEditSettings" -> canEditSettings;
                case "canManageRoles" -> canManageRoles;
                case "canPromote" -> canPromote;
                case "canDemote" -> canDemote;
                case "canAccessBank" -> canAccessBank;
                default -> false;
            };
        }

        void setPerm(String perm, boolean value) {
            switch (perm) {
                case "canInvite" -> canInvite = value;
                case "canKick" -> canKick = value;
                case "canEditSettings" -> canEditSettings = value;
                case "canManageRoles" -> canManageRoles = value;
                case "canPromote" -> canPromote = value;
                case "canDemote" -> canDemote = value;
                case "canAccessBank" -> canAccessBank = value;
            }
        }

        Map<String, Boolean> toPermMap() {
            Map<String, Boolean> map = new HashMap<>();
            map.put("canInvite", canInvite);
            map.put("canKick", canKick);
            map.put("canEditSettings", canEditSettings);
            map.put("canManageRoles", canManageRoles);
            map.put("canPromote", canPromote);
            map.put("canDemote", canDemote);
            map.put("canAccessBank", canAccessBank);
            return map;
        }
    }
}


