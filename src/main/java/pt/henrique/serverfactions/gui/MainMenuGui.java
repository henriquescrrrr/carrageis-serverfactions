package pt.henrique.serverfactions.gui;

import pt.henrique.serverfactions.ServerFactions;
import pt.henrique.serverfactions.config.ConfigManager;
import pt.henrique.serverfactions.config.GuiConfig;
import pt.henrique.serverfactions.config.LangManager;
import pt.henrique.serverfactions.manager.FactionManager;
import pt.henrique.serverfactions.model.Faction;
import pt.henrique.serverfactions.model.FactionRole;
import pt.henrique.serverfactions.util.SignInputUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;

import static pt.henrique.serverfactions.gui.GuiItems.*;

/**
 * Main faction menu opened via /fac.
 * Shows different layouts depending on whether the player is in a faction.
 */
public final class MainMenuGui {

    private MainMenuGui() {}

    public static void open(Player player) {
        ServerFactions plugin = ServerFactions.getInstance();
        LangManager lang = plugin.getLangManager();
        FactionManager fm = plugin.getFactionManager();
        GuiConfig gc = plugin.getGuiConfig();
        Faction faction = fm.getFactionByPlayer(player.getUniqueId());

        if (faction != null) {
            GuiHolder gui = new GuiHolder(gc.getSize("main-menu-faction"),
                    mm(lang.getRaw("gui-main-menu", player)));
            gui.fill();
            buildFactionMenu(gui, player, faction, fm, lang, plugin, gc);
            gui.openFor(player);
        } else {
            GuiHolder gui = new GuiHolder(gc.getSize("main-menu-no-faction"),
                    mm(lang.getRaw("gui-main-menu", player)));
            gui.fill();
            buildNoFactionMenu(gui, player, fm, lang, plugin, gc);
            gui.openFor(player);
        }
    }

    // ======================== NO FACTION LAYOUT ========================

    private static void buildNoFactionMenu(GuiHolder gui, Player player,
                                            FactionManager fm, LangManager lang,
                                            ServerFactions plugin, GuiConfig gc) {
        // Create Faction
        gui.setItem(gc.getSlot("main-menu-no-faction", "create"), create(Material.EMERALD_BLOCK,
                lang.getRaw("gui-btn-create", player)), p -> {
            p.closeInventory();
            startCreationFlow(p, plugin, lang);
        });

        // Browse Public Factions
        gui.setItem(gc.getSlot("main-menu-no-faction", "browse-public"), create(Material.COMPASS,
                lang.getRaw("gui-btn-public-factions", player)), p -> {
            PublicFactionsGui.open(p);
        });

        // My Invites
        int inviteCount = fm.getPlayerInvites(player.getUniqueId()).size();
        gui.setItem(gc.getSlot("main-menu-no-faction", "invites"), create(Material.WRITABLE_BOOK,
                lang.getRaw("gui-btn-invites", player)
                        .replace("{count}", String.valueOf(inviteCount))), p -> {
            InvitesGui.open(p);
        });

        // Close
        gui.setItem(gc.getSlot("main-menu-no-faction", "close"), create(Material.BARRIER,
                lang.getRaw("gui-btn-close", player)), p -> p.closeInventory());
    }

    // ======================== IN-FACTION LAYOUT ========================

    private static void buildFactionMenu(GuiHolder gui, Player player, Faction faction,
                                          FactionManager fm, LangManager lang,
                                          ServerFactions plugin, GuiConfig gc) {
        String factionId = faction.getFactionId();
        int memberCount = fm.getMemberCount(factionId);
        String max = faction.isMemberLimitEnabled() ? String.valueOf(faction.getMemberLimit()) : "∞";
        boolean isLeader = faction.getLeaderUuid().equals(player.getUniqueId());
        boolean chatOn = fm.isFactionChatActive(player.getUniqueId());
        FactionRole playerRole = fm.getPlayerRole(player.getUniqueId());

        // Faction Info (leader head)
        String leaderName = Bukkit.getOfflinePlayer(faction.getLeaderUuid()).getName();
        gui.setItem(gc.getSlot("main-menu-faction", "faction-info"),
                createHead(Bukkit.getOfflinePlayer(faction.getLeaderUuid()),
                        fm.getFormattedTag(factionId) + " <yellow>" + faction.getName(),
                        "<gray>Leader: <white>" + (leaderName != null ? leaderName : "?"),
                        "<gray>Members: <white>" + memberCount + "/" + max), p -> {
            FactionInfoGui.open(p, factionId);
        });

        // Members
        gui.setItem(gc.getSlot("main-menu-faction", "members"), create(Material.PLAYER_HEAD,
                lang.getRaw("gui-btn-members", player)
                        .replace("{count}", String.valueOf(memberCount))), p -> {
            MembersGui.open(p);
        });

        // Chat Toggle
        Material chatMat = chatOn ? Material.LIME_WOOL : Material.RED_WOOL;
        gui.setItem(gc.getSlot("main-menu-faction", "chat-toggle"), create(chatMat,
                lang.getRaw("gui-btn-chat-toggle", player)
                        .replace("{status}", chatOn ? "<green>ON" : "<red>OFF")), p -> {
            fm.toggleFactionChat(p.getUniqueId());
            boolean now = fm.isFactionChatActive(p.getUniqueId());
            p.sendMessage(lang.get(now ? "faction-chat-enabled" : "faction-chat-disabled", p));
            MainMenuGui.open(p); // refresh
        });

        // Browse Public
        gui.setItem(gc.getSlot("main-menu-faction", "browse-public"), create(Material.COMPASS,
                lang.getRaw("gui-btn-public-factions", player)), p -> {
            PublicFactionsGui.open(p);
        });

        // Invites (received invites)
        int inviteCount = fm.getPlayerInvites(player.getUniqueId()).size();
        gui.setItem(gc.getSlot("main-menu-faction", "invites"), create(Material.WRITABLE_BOOK,
                lang.getRaw("gui-btn-invites", player)
                        .replace("{count}", String.valueOf(inviteCount))), p -> {
            InvitesGui.open(p);
        });

        // Roles — grayed out if no canManageRoles permission
        boolean canManageRoles = isLeader || (playerRole != null && playerRole.isCanManageRoles());
        if (canManageRoles) {
            gui.setItem(gc.getSlot("main-menu-faction", "roles"), create(Material.NAME_TAG,
                    lang.getRaw("gui-btn-roles", player)), p -> {
                RolesGui.open(p);
            });
        } else {
            gui.setItem(gc.getSlot("main-menu-faction", "roles"), locked(
                    lang.getRaw("gui-btn-roles", player),
                    "<red>Your role doesn't have this permission."));
        }

        // Settings — grayed out if no canEditSettings permission
        boolean canEditSettings = isLeader || (playerRole != null && playerRole.isCanEditSettings());
        if (canEditSettings) {
            gui.setItem(gc.getSlot("main-menu-faction", "settings"), create(Material.COMPARATOR,
                    lang.getRaw("gui-btn-settings", player)), p -> {
                SettingsGui.open(p);
            });
        } else {
            gui.setItem(gc.getSlot("main-menu-faction", "settings"), locked(
                    lang.getRaw("gui-btn-settings", player),
                    "<red>Your role doesn't have this permission."));
        }

        // Invite Player — grayed out if no canInvite permission
        boolean canInvite = isLeader || (playerRole != null && playerRole.isCanInvite());
        if (canInvite) {
            gui.setItem(gc.getSlot("main-menu-faction", "invite-player"), create(Material.OAK_SIGN,
                    lang.getRaw("gui-btn-invite-player", player)), p -> {
                InvitePlayerGui.open(p);
            });
        } else {
            gui.setItem(gc.getSlot("main-menu-faction", "invite-player"), locked(
                    lang.getRaw("gui-btn-invite-player", player),
                    "<red>Your role doesn't have this permission."));
        }

        // Leave Faction
        gui.setItem(gc.getSlot("main-menu-faction", "leave"), create(Material.RED_BED,
                lang.getRaw("gui-btn-leave", player)), p -> {
            ConfirmActionGui.open(p,
                    lang.getRaw("gui-confirm-leave", p),
                    "<yellow>Leave <white>" + faction.getName() + "</white>?",
                    () -> {
                        fm.leaveFaction(p.getUniqueId());
                        MainMenuGui.open(p);
                    });
        });

        // Close
        gui.setItem(gc.getSlot("main-menu-faction", "close"), create(Material.BARRIER,
                lang.getRaw("gui-btn-close", player)), p -> p.closeInventory());

        // Dissolve (only visible for leader)
        if (isLeader) {
            gui.setItem(gc.getSlot("main-menu-faction", "dissolve"), create(Material.TNT,
                    lang.getRaw("gui-btn-dissolve", player)), p -> {
                ConfirmActionGui.open(p,
                        lang.getRaw("gui-confirm-dissolve", p),
                        "<red>Dissolve <white>" + faction.getName() + "</white>? This cannot be undone!",
                        () -> fm.dissolveFaction(factionId, p.getUniqueId()));
            });
        }
    }

    // ======================== CREATION FLOW ========================

    /**
     * Multi-step creation flow: Sign(name) → Sign(tag) → Visibility → Confirm
     */
    static void startCreationFlow(Player player, ServerFactions plugin, LangManager lang) {
        String namePrompt = lang.getRaw("sign-faction-name", player);
        SignInputUtil.requestInput(plugin, player, namePrompt, name -> {
            if (name.isEmpty()) return;

            FactionManager fm = plugin.getFactionManager();
            ConfigManager config = plugin.getConfigManager();
            int min = config.getMinNameLength();
            int max = config.getMaxNameLength();
            if (name.length() < min || name.length() > max) {
                player.sendMessage(lang.get("faction-name-too-long", player,
                        Map.of("min", String.valueOf(min), "max", String.valueOf(max))));
                return;
            }
            if (!name.matches("[a-zA-Z0-9 _-]+")) {
                player.sendMessage(lang.get("faction-name-invalid", player));
                return;
            }
            if (fm.isNameTaken(name)) {
                player.sendMessage(lang.get("faction-name-taken", player));
                return;
            }

            // Step 2: Tag
            String tagPrompt = lang.getRaw("sign-faction-tag", player)
                    .replace("{max}", String.valueOf(config.getMaxTagLength()));
            SignInputUtil.requestInput(plugin, player, tagPrompt, tag -> {
                if (tag.isEmpty()) return;

                int maxTag = config.getMaxTagLength();
                if (tag.length() > maxTag) {
                    player.sendMessage(lang.get("faction-tag-too-long", player,
                            Map.of("max", String.valueOf(maxTag))));
                    return;
                }
                if (!tag.matches("[a-zA-Z0-9_-]+")) {
                    // Early feedback; createFaction re-validates as the authoritative gate.
                    player.sendMessage(lang.get("faction-name-invalid", player));
                    return;
                }
                if (fm.isTagTaken(tag)) {
                    player.sendMessage(lang.get("faction-tag-taken", player));
                    return;
                }

                // Step 3: Visibility selection
                VisibilitySelectGui.open(player, name, tag);
            });
        });
    }
}
