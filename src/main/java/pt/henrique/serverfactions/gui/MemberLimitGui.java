package pt.henrique.serverfactions.gui;

import pt.henrique.serverfactions.ServerFactions;
import pt.henrique.serverfactions.config.ConfigManager;
import pt.henrique.serverfactions.config.GuiConfig;
import pt.henrique.serverfactions.config.LangManager;
import pt.henrique.serverfactions.manager.FactionManager;
import pt.henrique.serverfactions.model.Faction;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;

import static pt.henrique.serverfactions.gui.GuiItems.*;

/**
 * Adjust member limit with ± buttons.
 */
public final class MemberLimitGui {

    private static final String GUI = "member-limit";

    private MemberLimitGui() {}

    public static void open(Player player) {
        open(player, -1);
    }

    public static void open(Player player, int overrideLimit) {
        ServerFactions plugin = ServerFactions.getInstance();
        LangManager lang = plugin.getLangManager();
        FactionManager fm = plugin.getFactionManager();
        ConfigManager config = plugin.getConfigManager();
        GuiConfig gc = plugin.getGuiConfig();
        Faction faction = fm.getFactionByPlayer(player.getUniqueId());
        if (faction == null) return;

        // Reachable only from SettingsGui today, but re-gate here so the menu can
        // never be re-entered by a player whose permission was revoked meanwhile.
        if (!fm.canEditSettings(player.getUniqueId())) {
            player.sendMessage(lang.get("faction-no-permission-settings", player));
            return;
        }

        String factionId = faction.getFactionId();
        int currentMembers = fm.getMemberCount(factionId);
        int absMax = config.getAbsoluteMaxMembers();
        int limit = overrideLimit >= 0 ? overrideLimit : faction.getMemberLimit();
        limit = Math.max(limit, Math.max(currentMembers, 1));
        limit = Math.min(limit, absMax);
        final int displayLimit = limit;

        GuiHolder gui = new GuiHolder(gc.getSize(GUI), mm(lang.getRaw("gui-member-limit", player)));
        gui.fill();

        // Current value display
        gui.setItem(gc.getSlot(GUI, "current-display"), create(Material.PLAYER_HEAD,
                "<yellow>Member Limit: <white>" + displayLimit,
                "<gray>Current members: <white>" + currentMembers,
                "<gray>Maximum allowed: <white>" + absMax));

        // Adjustment buttons
        gui.setItem(gc.getSlot(GUI, "dec-10"), create(Material.RED_STAINED_GLASS_PANE,
                "<red>-10"), p -> open(p, clamp(displayLimit - 10, currentMembers, absMax)));

        gui.setItem(gc.getSlot(GUI, "dec-5"), create(Material.RED_STAINED_GLASS_PANE,
                "<red>-5"), p -> open(p, clamp(displayLimit - 5, currentMembers, absMax)));

        gui.setItem(gc.getSlot(GUI, "dec-1"), create(Material.RED_STAINED_GLASS_PANE,
                "<red>-1"), p -> open(p, clamp(displayLimit - 1, currentMembers, absMax)));

        gui.setItem(gc.getSlot(GUI, "inc-1"), create(Material.LIME_STAINED_GLASS_PANE,
                "<green>+1"), p -> open(p, clamp(displayLimit + 1, currentMembers, absMax)));

        gui.setItem(gc.getSlot(GUI, "inc-5"), create(Material.LIME_STAINED_GLASS_PANE,
                "<green>+5"), p -> open(p, clamp(displayLimit + 5, currentMembers, absMax)));

        gui.setItem(gc.getSlot(GUI, "inc-10"), create(Material.LIME_STAINED_GLASS_PANE,
                "<green>+10"), p -> open(p, clamp(displayLimit + 10, currentMembers, absMax)));

        // Save
        gui.setItem(gc.getSlot(GUI, "save"), create(Material.LIME_WOOL,
                "<green>Save"), p -> {
            if (fm.setMemberLimit(p.getUniqueId(), factionId, displayLimit)) {
                p.sendMessage(lang.get("faction-member-limit-changed", p,
                        Map.of("limit", String.valueOf(displayLimit))));
            }
            SettingsGui.open(p);
        });

        // Back
        gui.setItem(gc.getSlot(GUI, "back"), create(Material.ARROW,
                lang.getRaw("gui-btn-back", player)), p -> SettingsGui.open(p));

        // Close
        gui.setItem(gc.getSlot(GUI, "close"), create(Material.BARRIER,
                lang.getRaw("gui-btn-close", player)), p -> p.closeInventory());

        gui.openFor(player);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(Math.max(min, 1), Math.min(value, max));
    }
}

