package pt.henrique.serverfactions.gui;

import pt.henrique.serverfactions.ServerFactions;
import pt.henrique.serverfactions.config.GuiConfig;
import pt.henrique.serverfactions.config.LangManager;
import pt.henrique.serverfactions.manager.FactionManager;
import pt.henrique.serverfactions.model.Faction;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.Date;

import static pt.henrique.serverfactions.gui.GuiItems.*;

/**
 * Read-only GUI showing faction information.
 */
public final class FactionInfoGui {

    private static final String GUI = "faction-info";

    private FactionInfoGui() {}

    public static void open(Player player, String factionId) {
        ServerFactions plugin = ServerFactions.getInstance();
        LangManager lang = plugin.getLangManager();
        FactionManager fm = plugin.getFactionManager();
        GuiConfig gc = plugin.getGuiConfig();
        Faction faction = fm.getFaction(factionId);

        String title = lang.getRaw("gui-faction-info", player)
                .replace("{name}", faction != null ? faction.getName() : "?");
        GuiHolder gui = new GuiHolder(gc.getSize(GUI), mm(title));
        gui.fill();

        if (faction == null) {
            gui.openFor(player);
            return;
        }

        int members = fm.getMemberCount(factionId);
        String max = faction.isMemberLimitEnabled() ? String.valueOf(faction.getMemberLimit()) : "∞";
        String leaderName = Bukkit.getOfflinePlayer(faction.getLeaderUuid()).getName();
        String visibility = faction.isPublic() ? "Public" : "Private";
        String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date(faction.getCreatedAt()));

        // Faction banner with tag
        gui.setItem(gc.getSlot(GUI, "faction-banner"), create(Material.GOLDEN_HELMET,
                fm.getFormattedTag(factionId) + " <yellow>" + faction.getName()));

        // Leader head
        gui.setItem(gc.getSlot(GUI, "leader-head"), createHead(Bukkit.getOfflinePlayer(faction.getLeaderUuid()),
                lang.getRaw("faction-info-leader", player)
                        .replace("{leader}", leaderName != null ? leaderName : "?")));

        // Members count
        gui.setItem(gc.getSlot(GUI, "members-count"), create(Material.PLAYER_HEAD,
                lang.getRaw("faction-info-members", player)
                        .replace("{count}", String.valueOf(members))
                        .replace("{max}", max)));

        // Created date
        gui.setItem(gc.getSlot(GUI, "created-date"), create(Material.CLOCK,
                lang.getRaw("faction-info-created", player)
                        .replace("{date}", date)));

        // Visibility
        gui.setItem(gc.getSlot(GUI, "visibility"), create(faction.isPublic() ? Material.LIME_DYE : Material.GRAY_DYE,
                lang.getRaw("faction-info-visibility", player)
                        .replace("{visibility}", visibility)));

        // Back
        gui.setItem(gc.getSlot(GUI, "back"), create(Material.BARRIER,
                lang.getRaw("gui-btn-back", player)), p -> MainMenuGui.open(p));

        // Close
        gui.setItem(gc.getSlot(GUI, "close"), create(Material.BARRIER,
                lang.getRaw("gui-btn-close", player)), p -> p.closeInventory());

        gui.openFor(player);
    }
}

