package pt.henrique.serverfactions.hook;

import pt.henrique.serverfactions.ServerFactions;
import pt.henrique.serverfactions.manager.FactionManager;
import pt.henrique.serverfactions.model.Faction;
import pt.henrique.serverfactions.model.FactionRole;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI expansion for ServerFactions.
 * Provides placeholders: %factions_tag%, %factions_tag_formatted%, %factions_name%,
 * %factions_role%, %factions_leader%, %factions_members%, %factions_is_public%, %factions_color%
 *
 * Only loaded when PlaceholderAPI is present.
 */
public class PlaceholderApiHook extends PlaceholderExpansion {

    @Override
    public @NotNull String getIdentifier() {
        return "factions";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Henrique";
    }

    @Override
    public @NotNull String getVersion() {
        return ServerFactions.getInstance().getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        FactionManager fm = ServerFactions.getInstance().getFactionManager();
        Faction faction = fm.getFactionByPlayer(player.getUniqueId());

        return switch (params) {
            case "tag" -> faction != null ? faction.getTag() : "";
            case "tag_formatted" -> faction != null ? fm.getFormattedTag(faction.getFactionId()) : "";
            case "name" -> faction != null ? faction.getName() : "";
            case "role" -> {
                FactionRole role = fm.getPlayerRole(player.getUniqueId());
                yield role != null ? role.getName() : "";
            }
            case "leader" -> {
                if (faction == null) yield "";
                OfflinePlayer leader = Bukkit.getOfflinePlayer(faction.getLeaderUuid());
                yield leader.getName() != null ? leader.getName() : "";
            }
            case "members" -> faction != null
                    ? String.valueOf(fm.getMemberCount(faction.getFactionId())) : "0";
            case "is_public" -> faction != null
                    ? String.valueOf(faction.isPublic()) : "false";
            case "color" -> faction != null ? faction.getPrimaryColor() : "";
            default -> null;
        };
    }
}

