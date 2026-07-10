package pt.henrique.serverfactions.listener;

import pt.henrique.serverfactions.ServerFactions;
import pt.henrique.serverfactions.config.LangManager;
import pt.henrique.serverfactions.manager.FactionManager;
import pt.henrique.serverfactions.model.FactionInvite;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;

/**
 * Handles player connection events:
 * - On join: notify about pending invites
 * - On quit: deactivate faction chat
 */
public class PlayerConnectionListener implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        ServerFactions plugin = ServerFactions.getInstance();
        FactionManager fm = plugin.getFactionManager();
        LangManager lang = plugin.getLangManager();

        // Notify about pending invites (delayed 1 second so player sees it)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            List<FactionInvite> invites = fm.getPlayerInvites(player.getUniqueId());
            if (!invites.isEmpty()) {
                Component msg = MiniMessage.miniMessage().deserialize(
                        lang.getRaw("prefix", player)
                                + " <yellow>You have <white>" + invites.size()
                                + "</white> pending faction invite(s)! "
                                + "<click:run_command:/fac invites><gold>[View Invites]</gold></click></yellow>");
                player.sendMessage(msg);
            }
        }, 40L); // 2 seconds after join
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        ServerFactions plugin = ServerFactions.getInstance();
        if (plugin.getFactionManager() != null) {
            plugin.getFactionManager().setFactionChatActive(event.getPlayer().getUniqueId(), false);
        }
    }
}

