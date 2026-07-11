package pt.henrique.serverfactions.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Reusable Sign-editor GUI for text input.
 * Places a temporary sign below the player, opens the editor,
 * then restores the block and invokes the callback with the typed text.
 */
public final class SignInputUtil {

    private static final Map<UUID, PendingInput> pending = new ConcurrentHashMap<>();

    private SignInputUtil() {}

    /**
     * Open a sign editor for the player to type text.
     *
     * @param plugin     the owning plugin
     * @param player     the player
     * @param promptLine the prompt shown on line 3 of the sign
     * @param callback   called on main thread with the text from line 0 (trimmed), or empty if cancelled
     */
    public static void requestInput(Plugin plugin, Player player, String promptLine, Consumer<String> callback) {
        UUID uuid = player.getUniqueId();

        // Cancel any existing pending input
        PendingInput old = pending.remove(uuid);
        if (old != null) restoreBlock(old);

        Location loc = player.getLocation().clone();
        int y = Math.max(loc.getBlockY() - 4, player.getWorld().getMinHeight());
        loc = new Location(loc.getWorld(), loc.getBlockX(), y, loc.getBlockZ());

        Block block = loc.getBlock();
        BlockState savedState = block.getState();

        // Place temporary sign
        block.setType(Material.OAK_SIGN, false);
        if (!(block.getState() instanceof Sign sign)) {
            savedState.update(true, false);
            callback.accept("");
            return;
        }

        var front = sign.getSide(Side.FRONT);
        front.line(0, Component.empty());
        front.line(1, Component.text("^^^^^^^^"));
        front.line(2, Component.text(promptLine));
        front.line(3, Component.empty());
        sign.setWaxed(false);
        sign.update(true, false);

        PendingInput input = new PendingInput(uuid, block.getLocation(), savedState, callback);
        pending.put(uuid, input);

        // Open sign editor (1 tick delay to let the client receive the block change)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                restoreAndCleanup(uuid);
                return;
            }
            if (block.getState() instanceof Sign s) {
                player.openSign(s, Side.FRONT);
            }
        }, 2L);

        // Safety timeout: restore after 60 seconds if no response
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Remove ONLY if the map still holds THIS request — a plain remove(uuid)
            // here would cancel a NEWER pending input for the same player (e.g. the
            // tag prompt that follows the name prompt in the creation flow).
            if (pending.remove(uuid, input)) {
                restoreBlock(input);
                input.callback.accept("");
            }
        }, 1200L);
    }

    private static void restoreBlock(PendingInput input) {
        try {
            input.savedState.update(true, false);
        } catch (Exception ignored) {}
    }

    private static void restoreAndCleanup(UUID uuid) {
        PendingInput input = pending.remove(uuid);
        if (input != null) restoreBlock(input);
    }

    /**
     * Listener that must be registered to handle sign completion and player quit.
     */
    public static class SignInputListener implements Listener {

        @EventHandler(priority = EventPriority.LOWEST)
        public void onSignChange(SignChangeEvent event) {
            UUID uuid = event.getPlayer().getUniqueId();
            PendingInput input = pending.remove(uuid);
            if (input == null) return;

            // Check location matches
            if (!event.getBlock().getLocation().equals(input.location)) {
                pending.put(uuid, input); // wasn't our sign
                return;
            }

            event.setCancelled(true);

            // Restore the original block
            restoreBlock(input);

            // Read line 0 as the player's input
            Component line = event.line(0);
            String text = line != null ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(line).trim() : "";

            input.callback.accept(text);
        }

        @EventHandler
        public void onPlayerQuit(PlayerQuitEvent event) {
            restoreAndCleanup(event.getPlayer().getUniqueId());
        }
    }

    private record PendingInput(UUID uuid, Location location, BlockState savedState, Consumer<String> callback) {}
}

