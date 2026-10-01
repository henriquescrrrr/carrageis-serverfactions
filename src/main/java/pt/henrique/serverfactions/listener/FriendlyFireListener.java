package pt.henrique.serverfactions.listener;

import pt.henrique.serverfactions.ServerFactions;
import pt.henrique.serverfactions.manager.FactionManager;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Prevents damage between faction members when friendly fire is disabled.
 * friendly-fire: true  = faction members CAN hurt each other (no cancellation)
 * friendly-fire: false = faction members CANNOT hurt each other (damage cancelled)
 * <p>
 * Runs at HIGH so the cancellation lands before CombatLOG's MONITOR handler, which
 * is {@code ignoreCancelled = true} and therefore does not tag either player for a
 * blocked friendly hit.
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
     * Resolve the player the damage is ultimately attributable to.
     * <p>
     * Reading only {@code getDamager()} left three routes through the guard: an
     * area effect cloud from a lingering harming potion (the damager is the cloud,
     * which is not a Projectile), primed TNT and other explosives (the damager is
     * the explosive), and a tamed pet set on a faction mate (the damager is the
     * animal). The damage source's causing entity covers the first two and every
     * other indirect source the server attributes to a player, and is the same
     * entity vanilla credits the kill to. Pets are resolved separately, because
     * there the server credits the animal rather than its owner.
     */
    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        Entity causing = event.getDamageSource().getCausingEntity();
        if (causing instanceof Player player) {
            return player;
        }

        Entity damager = event.getDamager();
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) return player;
        }
        if (damager instanceof Tameable tameable && tameable.isTamed()
                && tameable.getOwner() instanceof Player owner) {
            return owner;
        }
        // A pet arrives with the animal as both damager and causing entity, so this
        // only fires when the two differ (a pet's projectile, for instance).
        if (causing instanceof Tameable tameable && tameable.isTamed()) {
            AnimalTamer owner = tameable.getOwner();
            if (owner instanceof Player player) return player;
        }
        return null;
    }
}
