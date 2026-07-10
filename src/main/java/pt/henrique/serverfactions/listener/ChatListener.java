package pt.henrique.serverfactions.listener;

import pt.henrique.serverfactions.ServerFactions;
import pt.henrique.serverfactions.config.LangManager;
import pt.henrique.serverfactions.manager.FactionManager;
import pt.henrique.serverfactions.model.Faction;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Map;
import java.util.UUID;

/**
 * Handles faction chat. When a player has faction chat active,
 * their messages are sent only to faction members instead of globally.
 */
public class ChatListener implements Listener {

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        ServerFactions plugin = ServerFactions.getInstance();
        FactionManager fm = plugin.getFactionManager();

        if (!fm.isFactionChatActive(player.getUniqueId())) return;
        if (!fm.isInFaction(player.getUniqueId())) return;

        // Cancel the global chat event
        event.setCancelled(true);

        Faction faction = fm.getFactionByPlayer(player.getUniqueId());
        if (faction == null) return;

        // Convert message Component to plain text for lang replacement
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        // Security: escape MiniMessage tags in the player's message. The lang layer
        // substitutes {message} as a raw string and then runs MiniMessage.deserialize,
        // so unescaped input could inject colors, clickable/hoverable components
        // (e.g. <click:run_command:...> that runs as the recipient) or break out of
        // the chat template. escapeTags keeps the text visible but inert.
        message = MiniMessage.miniMessage().escapeTags(message);
        LangManager lang = plugin.getLangManager();

        // Format using the faction-chat-format from lang
        Component formatted = lang.get("faction-chat-format", player, Map.of(
                "color", faction.getPrimaryColor(),
                "player", player.getName(),
                "message", message
        ));

        // Send to all online faction members
        for (UUID memberUuid : fm.getFactionMemberUuids(faction.getFactionId())) {
            Player member = Bukkit.getPlayer(memberUuid);
            if (member != null && member.isOnline()) {
                member.sendMessage(formatted);
            }
        }
    }
}

