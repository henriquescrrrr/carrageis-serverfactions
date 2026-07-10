package pt.henrique.serverfactions.listener;

import pt.henrique.serverfactions.ServerFactions;
import pt.henrique.serverfactions.manager.FactionManager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Prevents damage between faction members when friendly fire is disabled.
 * friendly-fire: true  = faction members CAN hurt each other (no cancellation)
 * friendly-fire: false = faction members CANNOT hurt each other (damage cancelled)
 */
public class FriendlyFireListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = resolveAttacker(event);
        if (attacker == null || attacker.equals(victim)) return;

        ServerFactions plugin = ServerFactions.getInstance();
        if (plugin.getConfigManager().isFriendlyFire()) return; // friendly fire allowed, do nothing

        FactionManager fm = plugin.getFactionManager();
        if (fm.areInSameFaction(attacker.getUniqueId(), victim.getUniqueId())) {
            event.setCancelled(true); // friendly fire disabled, block damage
        }
    }

    /**
     * Resolve the attacking player, handling projectiles shot by players.
     */
    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}

