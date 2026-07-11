package pt.henrique.serverfactions.gui;

import pt.henrique.serverfactions.ServerFactions;
import pt.henrique.serverfactions.config.GuiConfig;
import pt.henrique.serverfactions.config.LangManager;
import pt.henrique.serverfactions.manager.FactionManager;
import pt.henrique.serverfactions.model.Faction;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import static pt.henrique.serverfactions.gui.GuiItems.*;

/**
 * Detail view of a public faction.
 */
public final class PublicFactionDetailGui {

    private static final String GUI = "public-faction-detail";

    private PublicFactionDetailGui() {}

    public static void open(Player player, String factionId) {
        ServerFactions plugin = ServerFactions.getInstance();
        LangManager lang = plugin.getLangManager();
        FactionManager fm = plugin.getFactionManager();
        GuiConfig gc = plugin.getGuiConfig();
        Faction faction = fm.getFaction(factionId);
        if (faction == null) return;

        GuiHolder gui = new GuiHolder(gc.getSize(GUI), mm("<dark_gray>ℹ " + faction.getName()));
        gui.fill();

        int members = fm.getMemberCount(factionId);
        String max = faction.isMemberLimitEnabled() ? String.valueOf(faction.getMemberLimit()) : "∞";
        String leaderName = Bukkit.getOfflinePlayer(faction.getLeaderUuid()).getName();

        // Faction banner/info
        gui.setItem(gc.getSlot(GUI, "faction-banner"), create(Material.WHITE_BANNER,
                fm.getFormattedTag(factionId) + " <yellow>" + faction.getName()));

        // Leader head
        gui.setItem(gc.getSlot(GUI, "leader-head"), createHead(Bukkit.getOfflinePlayer(faction.getLeaderUuid()),
                "<yellow>Leader",
                "<white>" + (leaderName != null ? leaderName : "?")));

        // Members
        gui.setItem(gc.getSlot(GUI, "members-info"), create(Material.PLAYER_HEAD,
                "<yellow>Members",
                "<white>" + members + "/" + max));

        // Visibility
        gui.setItem(gc.getSlot(GUI, "visibility"), create(faction.isPublic() ? Material.LIME_DYE : Material.GRAY_DYE,
                "<yellow>Visibility",
                faction.isPublic() ? "<green>Public" : "<red>Private"));

        // Back
        gui.setItem(gc.getSlot(GUI, "back"), create(Material.ARROW,
                lang.getRaw("gui-btn-back", player)), p -> PublicFactionsGui.open(p));

        // Join button
        boolean alreadyInFaction = fm.isInFaction(player.getUniqueId());
        boolean isFull = faction.isMemberLimitEnabled() && faction.getMemberLimit() > 0
                && members >= faction.getMemberLimit();

        if (alreadyInFaction) {
            gui.setItem(gc.getSlot(GUI, "join-button"), locked(
                    lang.getRaw("gui-btn-join-public", player).replace("{name}", faction.getName()),
                    "<red>You're already in a faction."));
        } else if (isFull) {
            gui.setItem(gc.getSlot(GUI, "join-button"), locked(
                    lang.getRaw("gui-btn-join-public", player).replace("{name}", faction.getName()),
                    "<red>Faction is full."));
        } else if (!faction.isPublic()) {
            gui.setItem(gc.getSlot(GUI, "join-button"), locked(
                    lang.getRaw("gui-btn-join-public", player).replace("{name}", faction.getName()),
                    "<red>This faction is private."));
        } else {
            gui.setItem(gc.getSlot(GUI, "join-button"), create(Material.LIME_WOOL,
                    lang.getRaw("gui-btn-join-public", player).replace("{name}", faction.getName())), p -> {
                p.closeInventory();
                // joinFaction re-checks visibility/limit live and messages the
                // player on refusal; only reopen the menu when the join succeeded.
                boolean joined = fm.joinFaction(p.getUniqueId(), factionId);
                if (joined) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (p.isOnline()) MainMenuGui.open(p);
                    }, 2L);
                }
            });
        }

        // Close
        gui.setItem(gc.getSlot(GUI, "close"), create(Material.BARRIER,
                lang.getRaw("gui-btn-close", player)), p -> p.closeInventory());

        gui.openFor(player);
    }
}

