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
 * Actions available for a specific faction member.
 */
public final class MemberActionGui {

    private static final String GUI = "member-action";

    private MemberActionGui() {}

    public static void open(Player viewer, UUID targetUuid) {
        ServerFactions plugin = ServerFactions.getInstance();
        LangManager lang = plugin.getLangManager();
        FactionManager fm = plugin.getFactionManager();
        GuiConfig gc = plugin.getGuiConfig();
        Faction faction = fm.getFactionByPlayer(viewer.getUniqueId());
        if (faction == null) return;

        String factionId = faction.getFactionId();
        FactionRole viewerRole = fm.getPlayerRole(viewer.getUniqueId());
        FactionRole targetRole = fm.getPlayerRole(targetUuid);
        boolean isLeader = faction.getLeaderUuid().equals(viewer.getUniqueId());
        int viewerPriority = viewerRole != null ? viewerRole.getPriority() : Integer.MAX_VALUE;
        int targetPriority = targetRole != null ? targetRole.getPriority() : Integer.MAX_VALUE;

        String targetName = Bukkit.getOfflinePlayer(targetUuid).getName();
        String targetRoleName = targetRole != null ? targetRole.getName() : "?";

        GuiHolder gui = new GuiHolder(gc.getSize(GUI),
                mm("<dark_gray>Member: " + (targetName != null ? targetName : "?")));
        gui.fill();

        // Target head info
        gui.setItem(gc.getSlot(GUI, "member-head"), createHead(Bukkit.getOfflinePlayer(targetUuid),
                "<yellow>" + (targetName != null ? targetName : "?"),
                "<gray>Role: <white>" + targetRoleName,
                "<gray>Priority: <white>" + targetPriority));

        // Change Role
        boolean canManageRoles = isLeader || (viewerRole != null && viewerRole.isCanManageRoles());
        boolean canActOnTarget = isLeader || viewerPriority < targetPriority;
        if (canManageRoles && canActOnTarget) {
            gui.setItem(gc.getSlot(GUI, "change-role"), create(Material.NAME_TAG,
                    "<yellow>Change Role"), p -> {
                SelectRoleGui.open(p, targetUuid);
            });
        } else {
            gui.setItem(gc.getSlot(GUI, "change-role"), locked("<gray>Change Role",
                    canManageRoles
                            ? "<red>You can't manage someone of equal or higher rank."
                            : "<red>Your role doesn't have this permission."));
        }

        // Promote
        boolean canPromote = isLeader || (viewerRole != null && viewerRole.isCanPromote());
        if (canPromote && canActOnTarget) {
            gui.setItem(gc.getSlot(GUI, "promote"), create(Material.LIME_DYE,
                    "<green>Promote"), p -> {
                FactionRole nextUp = getAdjacentRole(fm, factionId, targetPriority, true);
                if (nextUp != null && (isLeader || nextUp.getPriority() > viewerPriority)) {
                    fm.assignRole(p.getUniqueId(), targetUuid, nextUp.getRoleId());
                    MemberActionGui.open(p, targetUuid);
                } else {
                    p.sendMessage(lang.get("faction-cant-assign-higher", p));
                }
            });
        } else {
            gui.setItem(gc.getSlot(GUI, "promote"), locked("<gray>Promote",
                    canPromote
                            ? "<red>You can't promote someone of equal or higher rank."
                            : "<red>Your role doesn't have this permission."));
        }

        // Demote
        boolean canDemote = isLeader || (viewerRole != null && viewerRole.isCanDemote());
        if (canDemote && canActOnTarget) {
            gui.setItem(gc.getSlot(GUI, "demote"), create(Material.RED_DYE,
                    "<red>Demote"), p -> {
                FactionRole nextDown = getAdjacentRole(fm, factionId, targetPriority, false);
                if (nextDown != null) {
                    fm.assignRole(p.getUniqueId(), targetUuid, nextDown.getRoleId());
                    MemberActionGui.open(p, targetUuid);
                }
            });
        } else {
            gui.setItem(gc.getSlot(GUI, "demote"), locked("<gray>Demote",
                    canDemote
                            ? "<red>You can't demote someone of equal or higher rank."
                            : "<red>Your role doesn't have this permission."));
        }

        // Kick
        boolean canKick = isLeader || (viewerRole != null && viewerRole.isCanKick());
        if (canKick && canActOnTarget) {
            gui.setItem(gc.getSlot(GUI, "kick"), create(Material.BARRIER,
                    "<red>Kick " + (targetName != null ? targetName : "?")), p -> {
                ConfirmActionGui.open(p,
                        lang.getRaw("gui-confirm-kick", p),
                        "<red>Kick <white>" + (targetName != null ? targetName : "?")
                                + "</white> from the faction?",
                        () -> {
                            fm.kickMember(p.getUniqueId(), targetUuid);
                            MembersGui.open(p);
                        });
            });
        } else {
            gui.setItem(gc.getSlot(GUI, "kick"), locked("<gray>Kick",
                    canKick
                            ? "<red>You can't kick someone of equal or higher rank."
                            : "<red>Your role doesn't have this permission."));
        }

        // Back
        gui.setItem(gc.getSlot(GUI, "back"), create(Material.ARROW,
                lang.getRaw("gui-btn-back", viewer)), p -> MembersGui.open(p));

        gui.openFor(viewer);
    }

    /**
     * Find the next role up (lower priority) or down (higher priority) from the given priority.
     */
    private static FactionRole getAdjacentRole(FactionManager fm, String factionId,
                                                int currentPriority, boolean up) {
        List<FactionRole> roles = fm.getFactionRoles(factionId);
        if (roles == null || roles.isEmpty()) return null;

        List<FactionRole> sorted = roles.stream()
                .sorted(Comparator.comparingInt(FactionRole::getPriority))
                .toList();

        if (up) {
            // Find role with the highest priority that is still < currentPriority
            FactionRole best = null;
            for (FactionRole r : sorted) {
                if (r.getPriority() < currentPriority && r.getPriority() > 0) { // skip leader role (0)
                    best = r;
                }
            }
            return best;
        } else {
            // Find role with the lowest priority that is still > currentPriority
            for (FactionRole r : sorted) {
                if (r.getPriority() > currentPriority) {
                    return r;
                }
            }
            return null;
        }
    }
}

