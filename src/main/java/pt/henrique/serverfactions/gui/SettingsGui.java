package pt.henrique.serverfactions.gui;

import pt.henrique.serverfactions.ServerFactions;
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
 * Faction settings GUI. Only accessible to members with canEditSettings or leader.
 */
public final class SettingsGui {

    private static final String GUI = "settings";

    private SettingsGui() {}

    public static void open(Player player) {
        ServerFactions plugin = ServerFactions.getInstance();
        LangManager lang = plugin.getLangManager();
        FactionManager fm = plugin.getFactionManager();
        GuiConfig gc = plugin.getGuiConfig();
        Faction faction = fm.getFactionByPlayer(player.getUniqueId());
        if (faction == null) return;

        String factionId = faction.getFactionId();
        boolean isLeader = faction.getLeaderUuid().equals(player.getUniqueId());

        GuiHolder gui = new GuiHolder(gc.getSize(GUI), mm(lang.getRaw("gui-settings", player)));
        gui.fill();

        // Name
        gui.setItem(gc.getSlot(GUI, "name"), create(Material.NAME_TAG,
                lang.getRaw("gui-btn-name", player).replace("{name}", faction.getName()),
                lang.getRaw("gui-btn-edit-name", player)), p -> {
            p.closeInventory();
            String prompt = lang.getRaw("sign-faction-name", p);
            SignInputUtil.requestInput(plugin, p, prompt, newName -> {
                if (newName.isEmpty()) { SettingsGui.open(p); return; }
                int min = plugin.getConfigManager().getMinNameLength();
                int max = plugin.getConfigManager().getMaxNameLength();
                if (newName.length() < min || newName.length() > max) {
                    p.sendMessage(lang.get("faction-name-too-long", p,
                            Map.of("min", String.valueOf(min), "max", String.valueOf(max))));
                    SettingsGui.open(p);
                    return;
                }
                if (!newName.matches("[a-zA-Z0-9 _-]+")) {
                    p.sendMessage(lang.get("faction-name-invalid", p));
                    SettingsGui.open(p);
                    return;
                }
                if (fm.isNameTaken(newName) && !newName.equalsIgnoreCase(faction.getName())) {
                    p.sendMessage(lang.get("faction-name-taken", p));
                    SettingsGui.open(p);
                    return;
                }
                fm.setName(factionId, newName);
                p.sendMessage(lang.get("faction-name-changed", p, Map.of("name", newName)));
                SettingsGui.open(p);
            });
        });

        // Tag
        gui.setItem(gc.getSlot(GUI, "tag"), create(Material.NAME_TAG,
                lang.getRaw("gui-btn-tag", player).replace("{tag}", faction.getTag()),
                lang.getRaw("gui-btn-edit-name", player)), p -> {
            p.closeInventory();
            String prompt = lang.getRaw("sign-faction-tag", p)
                    .replace("{max}", String.valueOf(plugin.getConfigManager().getMaxTagLength()));
            SignInputUtil.requestInput(plugin, p, prompt, newTag -> {
                if (newTag.isEmpty()) { SettingsGui.open(p); return; }
                int maxTag = plugin.getConfigManager().getMaxTagLength();
                if (newTag.length() > maxTag) {
                    p.sendMessage(lang.get("faction-tag-too-long", p,
                            Map.of("max", String.valueOf(maxTag))));
                    SettingsGui.open(p);
                    return;
                }
                if (!newTag.matches("[a-zA-Z0-9_-]+")) {
                    // Prevent MiniMessage/formatting injection via the tag (it is
                    // rendered unescaped in the formatted tag and placeholders).
                    p.sendMessage(lang.get("faction-name-invalid", p));
                    SettingsGui.open(p);
                    return;
                }
                if (fm.isTagTaken(newTag) && !newTag.equalsIgnoreCase(faction.getTag())) {
                    p.sendMessage(lang.get("faction-tag-taken", p));
                    SettingsGui.open(p);
                    return;
                }
                fm.setTag(factionId, newTag);
                p.sendMessage(lang.get("faction-tag-changed", p, Map.of("tag", newTag)));
                SettingsGui.open(p);
            });
        });

        // Primary Color
        gui.setItem(gc.getSlot(GUI, "primary-color"), create(Material.ORANGE_WOOL,
                lang.getRaw("gui-btn-primary-color", player),
                "<gray>Current: " + faction.getPrimaryColor() + "Sample"), p -> {
            ColorPickerGui.open(p, true);
        });

        // Secondary Color
        gui.setItem(gc.getSlot(GUI, "secondary-color"), create(Material.LIGHT_GRAY_WOOL,
                lang.getRaw("gui-btn-secondary-color", player),
                "<gray>Current: " + faction.getSecondaryColor() + "Sample"), p -> {
            ColorPickerGui.open(p, false);
        });

        // Visibility toggle
        boolean isPublic = faction.isPublic();
        gui.setItem(gc.getSlot(GUI, "visibility"), create(isPublic ? Material.REDSTONE_LAMP : Material.LEVER,
                isPublic
                        ? lang.getRaw("gui-btn-visibility-public", player)
                        : lang.getRaw("gui-btn-visibility-private", player)), p -> {
            fm.setPublic(factionId, !isPublic);
            p.sendMessage(lang.get(!isPublic ? "faction-visibility-public" : "faction-visibility-private", p));
            SettingsGui.open(p); // refresh
        });

        // Member Limit (toggle + amount combined in one slot)
        boolean limitEnabled = faction.isMemberLimitEnabled();
        gui.setItem(gc.getSlot(GUI, "member-limit"), create(Material.LEVER,
                lang.getRaw("gui-btn-member-limit-toggle", player)
                        .replace("{status}", limitEnabled ? "<green>Enabled" : "<red>Disabled"),
                limitEnabled
                        ? "<gray>Max: <white>" + faction.getMemberLimit() + " <gray>— Click to adjust"
                        : "<gray>Click to toggle"), (p, click) -> {
            if (!limitEnabled) {
                // Enable limit
                if (faction.getMemberLimit() <= 0) {
                    fm.setMemberLimit(factionId, 10);
                }
                fm.setMemberLimitEnabled(factionId, true);
                p.sendMessage(lang.get("faction-member-limit-enabled", p,
                        Map.of("limit", String.valueOf(Math.max(faction.getMemberLimit(), 10)))));
                SettingsGui.open(p);
            } else if (click.isRightClick()) {
                // Disable limit
                fm.setMemberLimitEnabled(factionId, false);
                p.sendMessage(lang.get("faction-member-limit-disabled", p));
                SettingsGui.open(p);
            } else {
                // Left-click → open member limit adjustment
                MemberLimitGui.open(p);
            }
        });

        // Transfer Leadership (only for leader)
        if (isLeader) {
            gui.setItem(gc.getSlot(GUI, "transfer-leadership"), create(Material.GOLDEN_HELMET,
                    lang.getRaw("gui-btn-transfer-leadership", player)), p -> {
                TransferLeadershipGui.open(p);
            });
        }

        // Back
        gui.setItem(gc.getSlot(GUI, "back"), create(Material.ARROW,
                lang.getRaw("gui-btn-back", player)), p -> MainMenuGui.open(p));

        // Close
        gui.setItem(gc.getSlot(GUI, "close"), create(Material.BARRIER,
                lang.getRaw("gui-btn-close", player)), p -> p.closeInventory());

        gui.openFor(player);
    }
}

