package pt.henrique.serverfactions.manager;

import pt.henrique.serverfactions.ServerFactions;
import pt.henrique.serverfactions.api.ServerFactionsProvider;
import pt.henrique.serverfactions.api.event.*;
import pt.henrique.serverfactions.config.ConfigManager;
import pt.henrique.serverfactions.config.LangManager;
import pt.henrique.serverfactions.economy.EconomyManager;
import pt.henrique.serverfactions.hook.MultiBankHook;
import pt.henrique.serverfactions.model.Faction;
import pt.henrique.serverfactions.model.FactionInvite;
import pt.henrique.serverfactions.model.FactionMember;
import pt.henrique.serverfactions.model.FactionRole;
import pt.henrique.serverfactions.storage.DatabaseManager;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The brain of the plugin. In-memory cache backed by SQLite.
 * All reads from cache. Writes update cache immediately, then persist to DB async.
 * Implements FactionManagerBridge for the public API.
 */
public class FactionManager implements ServerFactionsProvider.FactionManagerBridge {

    private final ServerFactions plugin;
    private final DatabaseManager database;
    private final ConfigManager config;
    private final LangManager lang;
    private final EconomyManager economy;
    private final MultiBankHook multiBankHook;

    // === Caches ===
    private final Map<String, Faction> factionsById = new ConcurrentHashMap<>();
    private final Map<String, String> factionsByName = new ConcurrentHashMap<>();       // lowercase name → factionId
    private final Map<String, String> factionsByTag = new ConcurrentHashMap<>();        // lowercase tag → factionId
    private final Map<UUID, String> playerFaction = new ConcurrentHashMap<>();          // playerUuid → factionId
    private final Map<String, Map<UUID, FactionMember>> factionMembers = new ConcurrentHashMap<>();
    private final Map<String, List<FactionRole>> factionRoles = new ConcurrentHashMap<>();
    private final Map<String, List<FactionInvite>> factionInvites = new ConcurrentHashMap<>();
    private final Map<String, String> formattedTagCache = new ConcurrentHashMap<>();

    // Transient state
    private final Set<UUID> factionChatActive = ConcurrentHashMap.newKeySet();

    public FactionManager(ServerFactions plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
        this.config = plugin.getConfigManager();
        this.lang = plugin.getLangManager();
        this.economy = plugin.getEconomyManager();
        this.multiBankHook = plugin.getMultiBankHook();
        loadData();
    }

    // ================================================================
    //  DATA LOADING (synchronous, called once during startup)
    // ================================================================

    private void loadData() {
        // Load factions
        for (Faction f : database.loadAllFactions()) {
            factionsById.put(f.getFactionId(), f);
            factionsByName.put(f.getName().toLowerCase(), f.getFactionId());
            factionsByTag.put(f.getTag().toLowerCase(), f.getFactionId());
        }

        // Load roles
        for (FactionRole r : database.loadAllRoles()) {
            factionRoles.computeIfAbsent(r.getFactionId(), k -> new ArrayList<>()).add(r);
        }

        // Load members
        for (FactionMember m : database.loadAllMembers()) {
            playerFaction.put(m.getPlayerUuid(), m.getFactionId());
            factionMembers.computeIfAbsent(m.getFactionId(), k -> new ConcurrentHashMap<>())
                    .put(m.getPlayerUuid(), m);
        }

        // Load invites (skip expired)
        long now = System.currentTimeMillis();
        for (FactionInvite inv : database.loadAllInvites()) {
            if (inv.getExpiresAt() > now) {
                factionInvites.computeIfAbsent(inv.getFactionId(), k -> new ArrayList<>()).add(inv);
            }
        }
        // Clean expired from DB
        database.deleteExpiredInvites();

        plugin.getLogger().info("Loaded " + factionsById.size() + " factions, "
                + playerFaction.size() + " members.");
    }

    // ================================================================
    //  FACTION LIFECYCLE
    // ================================================================

    /**
     * Create a new faction. Validates, withdraws cost, creates faction + roles + member.
     * All async economy calls chain with scheduler callbacks.
     */
    public void createFaction(Player leader, String name, String tag, boolean isPublic) {
        UUID leaderUuid = leader.getUniqueId();

        // Sync validation from cache
        if (playerFaction.containsKey(leaderUuid)) {
            leader.sendMessage(lang.get("faction-already-in-faction", leader));
            return;
        }
        if (!isValidName(name)) {
            leader.sendMessage(lang.get("faction-name-invalid", leader));
            return;
        }
        if (name.length() < config.getMinNameLength() || name.length() > config.getMaxNameLength()) {
            leader.sendMessage(lang.get("faction-name-too-long", leader, Map.of(
                    "min", String.valueOf(config.getMinNameLength()),
                    "max", String.valueOf(config.getMaxNameLength()))));
            return;
        }
        if (tag.isEmpty() || tag.length() > config.getMaxTagLength()) {
            leader.sendMessage(lang.get("faction-tag-too-long", leader, Map.of(
                    "max", String.valueOf(config.getMaxTagLength()))));
            return;
        }
        if (!isValidTag(tag)) {
            // The tag is embedded (unescaped) into MiniMessage templates and the
            // formatted tag / PlaceholderAPI output, so reject anything but a safe
            // charset to prevent MiniMessage/formatting injection.
            leader.sendMessage(lang.get("faction-name-invalid", leader));
            return;
        }
        if (isNameTaken(name)) {
            leader.sendMessage(lang.get("faction-name-taken", leader));
            return;
        }
        if (isTagTaken(tag)) {
            leader.sendMessage(lang.get("faction-tag-taken", leader));
            return;
        }

        double cost = config.getCreationCost();

        if (cost <= 0) {
            doCreateFaction(leader, name, tag, isPublic);
            return;
        }

        // Async: check balance then withdraw
        economy.has(leaderUuid, cost).thenAccept(hasMoney -> {
            if (!hasMoney) {
                economy.getBalance(leaderUuid).thenAccept(balance ->
                        runSync(() -> leader.sendMessage(lang.get("faction-not-enough-money", leader, Map.of(
                                "cost", formatMoney(cost), "balance", formatMoney(balance)))))
                );
                return;
            }
            economy.withdraw(leaderUuid, cost).thenAccept(success ->
                    runSync(() -> {
                        if (!success) {
                            leader.sendMessage(lang.get("faction-not-enough-money", leader, Map.of(
                                    "cost", formatMoney(cost), "balance", "?")));
                            return;
                        }
                        // Re-validate after async gap
                        if (playerFaction.containsKey(leaderUuid) || isNameTaken(name) || isTagTaken(tag)) {
                            // The cost was already withdrawn but the faction can no longer
                            // be created (name/tag raced away or the player joined another
                            // faction meanwhile) — refund it instead of destroying money.
                            economy.deposit(leaderUuid, cost).thenAccept(refunded -> {
                                if (!refunded) {
                                    plugin.getLogger().warning("Could not refund faction creation cost ("
                                            + cost + ") to " + leaderUuid);
                                }
                            });
                            leader.sendMessage(lang.get("faction-name-taken", leader));
                            return;
                        }
                        doCreateFaction(leader, name, tag, isPublic);
                    })
            );
        });
    }

    private void doCreateFaction(Player leader, String name, String tag, boolean isPublic) {
        UUID leaderUuid = leader.getUniqueId();
        String factionId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        // Create faction object
        Faction faction = new Faction(factionId, name, tag, leaderUuid,
                "<white>", "<gray>", isPublic,
                config.getDefaultMemberLimit(), config.isDefaultMemberLimitEnabled(),
                now, null);

        // Create Leader role (priority 0, all perms true)
        String leaderRoleId = UUID.randomUUID().toString();
        FactionRole leaderRole = new FactionRole(leaderRoleId, factionId, "Leader", 0,
                true, true, true, true, true, true, true);

        // Create default roles from config
        List<FactionRole> allRoles = new ArrayList<>();
        allRoles.add(leaderRole);
        String defaultMemberRoleId = null;
        int highestPriority = 0;

        for (ConfigManager.DefaultRole dr : config.getDefaultRoles()) {
            String roleId = UUID.randomUUID().toString();
            FactionRole role = new FactionRole(roleId, factionId, dr.name, dr.priority,
                    dr.canInvite, dr.canKick, dr.canEditSettings, dr.canManageRoles,
                    dr.canPromote, dr.canDemote, dr.canAccessBank);
            allRoles.add(role);
            if (dr.priority > highestPriority) {
                highestPriority = dr.priority;
                defaultMemberRoleId = roleId;
            }
        }

        // Create leader member
        FactionMember leaderMember = new FactionMember(leaderUuid, factionId, leaderRoleId, now);

        // Update caches
        factionsById.put(factionId, faction);
        factionsByName.put(name.toLowerCase(), factionId);
        factionsByTag.put(tag.toLowerCase(), factionId);
        playerFaction.put(leaderUuid, factionId);
        Map<UUID, FactionMember> membersMap = new ConcurrentHashMap<>();
        membersMap.put(leaderUuid, leaderMember);
        factionMembers.put(factionId, membersMap);
        factionRoles.put(factionId, allRoles);
        invalidateFormattedTag(factionId);

        // Persist to DB async
        database.saveFaction(faction);
        for (FactionRole role : allRoles) {
            database.saveRole(role);
        }
        database.saveMember(leaderMember);

        // Fire event
        Bukkit.getPluginManager().callEvent(new FactionCreateEvent(factionId, leaderUuid, name, tag));

        // MultiBank: create bank account
        if (multiBankHook.isAvailable() && config.isAutoCreateBank()) {
            String accountName = config.getBankNamePrefix() + name;
            multiBankHook.createFactionBankAccount(accountName, leaderUuid)
                    .thenAccept(accountId -> {
                        if (accountId != null) {
                            runSync(() -> {
                                faction.setBankAccountId(accountId);
                                database.updateFaction(faction);
                            });
                        }
                    });
        }

        // Messages
        leader.sendMessage(lang.get("faction-created", leader, Map.of("name", name, "tag", tag)));
        playSound(leader, "faction-created");

        // Broadcast
        Component broadcast = lang.get("faction-created-broadcast", Map.of(
                "player", leader.getName(), "name", name, "tag", tag));
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(broadcast));
    }

    /**
     * Dissolve a faction. Only the leader or a {@code serverfactions.admin} may do this;
     * {@code dissolvedBy == null} means console/system (always allowed).
     */
    public void dissolveFaction(String factionId, UUID dissolvedBy) {
        Faction faction = factionsById.get(factionId);
        if (faction == null) return;

        // Server-side authorization (defence in depth on top of the GUI gating):
        // a stale GUI or crafted call must not let a non-leader dissolve the faction.
        if (dissolvedBy != null && !faction.getLeaderUuid().equals(dissolvedBy)) {
            Player actor = Bukkit.getPlayer(dissolvedBy);
            if (actor == null || !actor.hasPermission("serverfactions.admin")) {
                if (actor != null) actor.sendMessage(lang.get("faction-only-leader-dissolve", actor));
                return;
            }
        }

        // Fire disband event
        Bukkit.getPluginManager().callEvent(new FactionDisbandEvent(factionId, dissolvedBy));

        // Fire leave event for every member
        Map<UUID, FactionMember> members = factionMembers.getOrDefault(factionId, Collections.emptyMap());
        for (UUID memberUuid : members.keySet()) {
            Bukkit.getPluginManager().callEvent(
                    new FactionMemberLeaveEvent(factionId, memberUuid, LeaveReason.DISBANDED));

            // Notify online members
            Player online = Bukkit.getPlayer(memberUuid);
            if (online != null) {
                online.sendMessage(lang.get("faction-dissolved-member", online,
                        Map.of("name", faction.getName())));
                playSound(online, "faction-dissolved");
                factionChatActive.remove(memberUuid);
            }
        }

        // Bank account: MultiBank has no delete API. The account becomes orphaned.
        // An admin can manually freeze it via /mb admin freeze if needed.
        if (faction.getBankAccountId() != null) {
            plugin.getLogger().info("Faction disbanded. Bank account " + faction.getBankAccountId()
                    + " is now orphaned.");
        }

        // Remove from caches
        factionsById.remove(factionId);
        factionsByName.remove(faction.getName().toLowerCase());
        factionsByTag.remove(faction.getTag().toLowerCase());
        formattedTagCache.remove(factionId);
        for (UUID memberUuid : members.keySet()) {
            playerFaction.remove(memberUuid);
        }
        factionMembers.remove(factionId);
        factionRoles.remove(factionId);
        factionInvites.remove(factionId);

        // Delete from DB async
        database.deleteFaction(factionId);

        // Broadcast dissolution
        Component broadcast = lang.get("faction-dissolved", Map.of("name", faction.getName()));
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(broadcast));
    }

    // ================================================================
    //  MEMBERS
    // ================================================================

    /**
     * Join a faction. The faction must be public, or the player must hold a pending
     * (non-expired) invite — re-checked here server-side so a stale "Join" button on a
     * faction that just went private cannot bypass the invite requirement.
     *
     * @return true if the player actually joined
     */
    public boolean joinFaction(UUID playerUuid, String factionId) {
        Faction faction = factionsById.get(factionId);
        if (faction == null || playerFaction.containsKey(playerUuid)) return false;

        // Private factions require an invite
        if (!faction.isPublic() && !hasInvite(playerUuid, factionId)) {
            Player p = Bukkit.getPlayer(playerUuid);
            if (p != null) p.sendMessage(lang.get("faction-faction-private", p));
            return false;
        }

        // Check member limit
        if (faction.isMemberLimitEnabled() && faction.getMemberLimit() > 0) {
            int count = getMemberCount(factionId);
            if (count >= faction.getMemberLimit()) {
                Player p = Bukkit.getPlayer(playerUuid);
                if (p != null) {
                    p.sendMessage(lang.get("faction-full", p, Map.of(
                            "count", String.valueOf(count), "max", String.valueOf(faction.getMemberLimit()))));
                }
                return false;
            }
        }

        // Get the lowest-rank default role (highest priority number)
        FactionRole defaultRole = getLowestRole(factionId);
        if (defaultRole == null) return false;

        long now = System.currentTimeMillis();
        FactionMember member = new FactionMember(playerUuid, factionId, defaultRole.getRoleId(), now);

        // Update caches
        playerFaction.put(playerUuid, factionId);
        factionMembers.computeIfAbsent(factionId, k -> new ConcurrentHashMap<>()).put(playerUuid, member);

        // Remove any pending invites for this player to this faction
        removeInvite(playerUuid, factionId);

        // Persist
        database.saveMember(member);

        // Fire event
        Bukkit.getPluginManager().callEvent(new FactionMemberJoinEvent(factionId, playerUuid));

        // Invite to bank account (leader is the inviter)
        if (multiBankHook.isAvailable() && faction.getBankAccountId() != null) {
            multiBankHook.inviteMemberToAccount(faction.getBankAccountId(), faction.getLeaderUuid(), playerUuid);
        }

        // Messages
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null) {
            player.sendMessage(lang.get("faction-joined", player, Map.of("name", faction.getName())));
            playSound(player, "member-joined");
        }
        broadcastToFaction(factionId, "faction-joined-broadcast",
                Map.of("player", getPlayerName(playerUuid)), playerUuid);
        return true;
    }

    public void leaveFaction(UUID playerUuid) {
        String factionId = playerFaction.get(playerUuid);
        if (factionId == null) return;

        Faction faction = factionsById.get(factionId);
        if (faction == null) return;

        // Leader cannot leave
        if (faction.getLeaderUuid().equals(playerUuid)) {
            Player p = Bukkit.getPlayer(playerUuid);
            if (p != null) p.sendMessage(lang.get("faction-leader-cannot-leave", p));
            return;
        }

        // Remove from caches
        playerFaction.remove(playerUuid);
        Map<UUID, FactionMember> members = factionMembers.get(factionId);
        if (members != null) members.remove(playerUuid);
        factionChatActive.remove(playerUuid);

        // Persist
        database.deleteMember(playerUuid);

        // Fire event
        Bukkit.getPluginManager().callEvent(
                new FactionMemberLeaveEvent(factionId, playerUuid, LeaveReason.LEFT));

        // Messages
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null) {
            player.sendMessage(lang.get("faction-left", player, Map.of("name", faction.getName())));
            playSound(player, "member-left");
        }
        broadcastToFaction(factionId, "faction-left-broadcast",
                Map.of("player", getPlayerName(playerUuid)), null);
    }

    public void kickMember(UUID kickerUuid, UUID targetUuid) {
        String factionId = playerFaction.get(kickerUuid);
        if (factionId == null) return;

        Faction faction = factionsById.get(factionId);
        if (faction == null) return;

        Player kicker = Bukkit.getPlayer(kickerUuid);

        // Can't kick yourself
        if (kickerUuid.equals(targetUuid)) {
            if (kicker != null) kicker.sendMessage(lang.get("faction-cant-kick-self", kicker));
            return;
        }

        // The target must be in the kicker's faction. Without this a stale members
        // GUI (the target left and joined another faction while the menu was open)
        // would still run the removal below, which deletes the target's membership
        // row and their playerFaction entry — evicting them from a faction the
        // kicker has no standing in, and leaving that faction's member map stale.
        if (!factionId.equals(playerFaction.get(targetUuid))) {
            if (kicker != null) kicker.sendMessage(lang.get("faction-no-permission-kick", kicker));
            return;
        }

        // Check permission
        FactionRole kickerRole = getPlayerRole(kickerUuid);
        if (kickerRole == null || (!kickerRole.isLeaderRole() && !kickerRole.isCanKick())) {
            if (kicker != null) kicker.sendMessage(lang.get("faction-no-permission-kick", kicker));
            return;
        }

        // Check hierarchy
        FactionRole targetRole = getPlayerRole(targetUuid);
        if (targetRole != null && targetRole.getPriority() <= kickerRole.getPriority() && !kickerRole.isLeaderRole()) {
            if (kicker != null) kicker.sendMessage(lang.get("faction-cant-kick-higher", kicker));
            return;
        }

        // Remove from caches
        playerFaction.remove(targetUuid);
        Map<UUID, FactionMember> members = factionMembers.get(factionId);
        if (members != null) members.remove(targetUuid);
        factionChatActive.remove(targetUuid);

        // Persist
        database.deleteMember(targetUuid);

        // Fire event
        Bukkit.getPluginManager().callEvent(
                new FactionMemberLeaveEvent(factionId, targetUuid, LeaveReason.KICKED));

        // Messages
        Player target = Bukkit.getPlayer(targetUuid);
        if (target != null) {
            target.sendMessage(lang.get("faction-kicked", target, Map.of(
                    "faction", faction.getName(), "player", getPlayerName(kickerUuid))));
            playSound(target, "member-kicked");
        }
        if (kicker != null) {
            kicker.sendMessage(lang.get("faction-kicked-confirm", kicker,
                    Map.of("target", getPlayerName(targetUuid))));
        }
        broadcastToFaction(factionId, "faction-kick-broadcast",
                Map.of("target", getPlayerName(targetUuid)), null);
    }

    // ================================================================
    //  INVITES
    // ================================================================

    public void invitePlayer(UUID inviterUuid, UUID targetUuid, String factionId) {
        Faction faction = factionsById.get(factionId);
        if (faction == null) return;

        Player inviter = Bukkit.getPlayer(inviterUuid);

        // Can't invite yourself
        if (inviterUuid.equals(targetUuid)) {
            if (inviter != null) inviter.sendMessage(lang.get("faction-invite-self", inviter));
            return;
        }

        // The inviter must belong to the faction they are inviting into. The role
        // check below reads the inviter's role in their OWN faction, so without
        // this a member with canInvite could mint invites to any other faction.
        if (!factionId.equals(playerFaction.get(inviterUuid))) {
            if (inviter != null) inviter.sendMessage(lang.get("faction-no-permission-invite", inviter));
            return;
        }

        // Check permission
        FactionRole inviterRole = getPlayerRole(inviterUuid);
        if (inviterRole == null || (!inviterRole.isLeaderRole() && !inviterRole.isCanInvite())) {
            if (inviter != null) inviter.sendMessage(lang.get("faction-no-permission-invite", inviter));
            return;
        }

        // Target already in a faction?
        if (playerFaction.containsKey(targetUuid)) {
            if (inviter != null) {
                inviter.sendMessage(lang.get("faction-target-in-faction", inviter,
                        Map.of("player", getPlayerName(targetUuid))));
            }
            return;
        }

        // Already has a pending invite?
        if (hasInvite(targetUuid, factionId)) {
            if (inviter != null) inviter.sendMessage(lang.get("faction-invite-already-sent", inviter));
            return;
        }

        // Check member limit
        if (faction.isMemberLimitEnabled() && faction.getMemberLimit() > 0) {
            int count = getMemberCount(factionId);
            if (count >= faction.getMemberLimit()) {
                if (inviter != null) {
                    inviter.sendMessage(lang.get("faction-full", inviter, Map.of(
                            "count", String.valueOf(count), "max", String.valueOf(faction.getMemberLimit()))));
                }
                return;
            }
        }

        // Create invite
        long now = System.currentTimeMillis();
        long expiresAt = now + (config.getInviteExpiryHours() * 3600000L);
        FactionInvite invite = new FactionInvite(targetUuid, factionId, inviterUuid, now, expiresAt);

        factionInvites.computeIfAbsent(factionId, k -> new ArrayList<>()).add(invite);
        database.saveInvite(invite);

        // Messages
        if (inviter != null) {
            inviter.sendMessage(lang.get("faction-invite-sent", inviter,
                    Map.of("player", getPlayerName(targetUuid))));
        }
        Player target = Bukkit.getPlayer(targetUuid);
        if (target != null) {
            target.sendMessage(lang.get("faction-invite-received", target, Map.of(
                    "player", getPlayerName(inviterUuid), "faction", faction.getName())));
            playSound(target, "invite-received");
        }
    }

    public void acceptInvite(UUID playerUuid, String factionId) {
        Player player = Bukkit.getPlayer(playerUuid);
        Faction faction = factionsById.get(factionId);
        if (!hasInvite(playerUuid, factionId)) {
            // Invite expired (or was withdrawn) between the GUI opening and the click.
            if (player != null && faction != null) {
                player.sendMessage(lang.get("faction-invite-expired", player,
                        Map.of("faction", faction.getName())));
            }
            removeInvite(playerUuid, factionId); // clean up an expired leftover, if any
            return;
        }
        if (playerFaction.containsKey(playerUuid)) {
            if (player != null) player.sendMessage(lang.get("faction-already-in-faction", player));
            return;
        }

        // Join first; only report success (and consume the invite) if it worked.
        // joinFaction removes the pending invite itself on success; on failure
        // (e.g. faction full) the invite is kept so the player can retry later.
        if (!joinFaction(playerUuid, factionId)) return;

        if (player != null && faction != null) {
            player.sendMessage(lang.get("faction-invite-accepted", player,
                    Map.of("faction", faction.getName())));
        }
    }

    public void declineInvite(UUID playerUuid, String factionId) {
        removeInvite(playerUuid, factionId);
        Player player = Bukkit.getPlayer(playerUuid);
        Faction faction = factionsById.get(factionId);
        if (player != null && faction != null) {
            player.sendMessage(lang.get("faction-invite-declined", player,
                    Map.of("faction", faction.getName())));
        }
    }

    private boolean hasInvite(UUID playerUuid, String factionId) {
        List<FactionInvite> invites = factionInvites.get(factionId);
        if (invites == null) return false;
        return invites.stream().anyMatch(i ->
                i.getInvitedUuid().equals(playerUuid) && !i.isExpired());
    }

    private void removeInvite(UUID playerUuid, String factionId) {
        List<FactionInvite> invites = factionInvites.get(factionId);
        if (invites != null) {
            invites.removeIf(i -> i.getInvitedUuid().equals(playerUuid));
        }
        database.deleteInvite(playerUuid, factionId);
    }

    /** Get all pending (non-expired) invites for a player. */
    public List<FactionInvite> getPlayerInvites(UUID playerUuid) {
        List<FactionInvite> result = new ArrayList<>();
        for (List<FactionInvite> invites : factionInvites.values()) {
            for (FactionInvite inv : invites) {
                if (inv.getInvitedUuid().equals(playerUuid) && !inv.isExpired()) {
                    result.add(inv);
                }
            }
        }
        return result;
    }

    /** Clean expired invites from cache and DB. */
    public void cleanExpiredInvites() {
        for (List<FactionInvite> invites : factionInvites.values()) {
            invites.removeIf(FactionInvite::isExpired);
        }
        database.deleteExpiredInvites();
    }

    // ================================================================
    //  LEADERSHIP
    // ================================================================

    public void transferLeadership(UUID currentLeaderUuid, UUID newLeaderUuid) {
        String factionId = playerFaction.get(currentLeaderUuid);
        if (factionId == null) return;

        Faction faction = factionsById.get(factionId);
        if (faction == null) return;
        if (!faction.getLeaderUuid().equals(currentLeaderUuid)) return;

        // Both must be in the same faction
        if (!factionId.equals(playerFaction.get(newLeaderUuid))) return;

        // Get Leader role
        FactionRole leaderRole = getLeaderRole(factionId);
        if (leaderRole == null) return;

        // Get current roles
        FactionRole oldLeaderCurrentRole = getPlayerRole(currentLeaderUuid);
        FactionRole newLeaderCurrentRole = getPlayerRole(newLeaderUuid);
        if (newLeaderCurrentRole == null) return;

        // Swap: new leader gets Leader role, old leader gets new leader's old role
        FactionMember oldLeaderMember = getMember(currentLeaderUuid);
        FactionMember newLeaderMember = getMember(newLeaderUuid);
        if (oldLeaderMember == null || newLeaderMember == null) return;

        oldLeaderMember.setRoleId(newLeaderCurrentRole.getRoleId());
        newLeaderMember.setRoleId(leaderRole.getRoleId());
        faction.setLeaderUuid(newLeaderUuid);

        // Persist
        database.saveMember(oldLeaderMember);
        database.saveMember(newLeaderMember);
        database.updateFaction(faction);

        // Fire event
        Bukkit.getPluginManager().callEvent(
                new FactionLeaderChangeEvent(factionId, currentLeaderUuid, newLeaderUuid));

        // Messages
        Player oldLeader = Bukkit.getPlayer(currentLeaderUuid);
        Player newLeader = Bukkit.getPlayer(newLeaderUuid);
        if (oldLeader != null) {
            oldLeader.sendMessage(lang.get("faction-leadership-transferred", oldLeader,
                    Map.of("player", getPlayerName(newLeaderUuid))));
        }
        if (newLeader != null) {
            newLeader.sendMessage(lang.get("faction-leadership-received", newLeader,
                    Map.of("faction", faction.getName())));
        }
    }

    // ================================================================
    //  ROLES
    // ================================================================

    /**
     * Authorization guard for role management (create/edit/delete). Defence in depth
     * on top of the GUI gating: re-verifies the actor server-side so a demoted/kicked
     * player with a stale open GUI — or a crafted API call — cannot escalate.
     *
     * <p>The Leader (priority 0) may manage any non-leader role. A non-leader actor must:
     * be in the same faction as the role, hold {@code canManageRoles}, and only act on
     * roles ranked strictly below their own (priority strictly greater). This blocks
     * editing one's own role or a superior's role. Permission-grant escalation and
     * priority changes are validated separately in {@link #editRole}/{@link #createRole}.
     *
     * @param actorUuid   the player attempting the action (may be null → denied)
     * @param factionId   the faction the role belongs to
     * @param targetRole  the role being managed, or null for a create in {@code factionId}
     * @return true if the action is authorised
     */
    private boolean canManageRole(UUID actorUuid, String factionId, FactionRole targetRole) {
        if (actorUuid == null || factionId == null) return false;
        // Actor must belong to the faction whose roles are being managed.
        if (!factionId.equals(playerFaction.get(actorUuid))) return false;

        FactionRole actorRole = getPlayerRole(actorUuid);
        if (actorRole == null) return false;
        if (actorRole.isLeaderRole()) return true; // leader may manage any non-leader role

        if (!actorRole.isCanManageRoles()) return false;
        // Never let a non-leader touch the leader role.
        if (targetRole != null) {
            if (targetRole.isLeaderRole()) return false;
            // Only manage roles strictly below the actor (higher priority number).
            if (targetRole.getPriority() <= actorRole.getPriority()) return false;
        }
        return true;
    }

    /**
     * Non-leaders may not grant a permission they do not themselves hold (privilege
     * escalation), and may not create/set a role at or above their own rank.
     * The leader is unrestricted. Returns the priority the role should be clamped to.
     */
    private int sanitizeRolePermissions(UUID actorUuid, int requestedPriority, Map<String, Boolean> permissions) {
        int priority = Math.max(requestedPriority, 1); // 0 is Leader only
        FactionRole actorRole = getPlayerRole(actorUuid);
        if (actorRole != null && !actorRole.isLeaderRole()) {
            // Managed roles must stay strictly below the actor's own rank.
            priority = Math.max(priority, actorRole.getPriority() + 1);
            // Strip any permission the actor lacks so it cannot be handed out.
            for (String perm : new String[]{"canInvite", "canKick", "canEditSettings",
                    "canManageRoles", "canPromote", "canDemote", "canAccessBank"}) {
                if (Boolean.TRUE.equals(permissions.get(perm)) && !actorRole.hasPermission(perm)) {
                    permissions.put(perm, false);
                }
            }
        }
        return priority;
    }

    /**
     * Create a new role. Validates the actor, the role name charset/length
     * (role names are rendered through MiniMessage in GUIs and chat messages,
     * so an unvalidated name would be a formatting/click injection vector)
     * and the per-faction role cap.
     *
     * @return the created role, or null if creation was refused (the actor is messaged)
     */
    public FactionRole createRole(UUID actorUuid, String factionId, String name, int priority, Map<String, Boolean> permissions) {
        if (!canManageRole(actorUuid, factionId, null)) {
            messagePlayer(actorUuid, "faction-no-permission-roles", null);
            return null;
        }
        if (!isValidRoleName(name)) {
            messagePlayer(actorUuid, "faction-name-invalid", null);
            return null;
        }
        if (getFactionRoles(factionId).size() >= config.getMaxRoles()) {
            messagePlayer(actorUuid, "faction-max-roles", null);
            return null;
        }
        int safePriority = sanitizeRolePermissions(actorUuid, priority, permissions);
        String roleId = UUID.randomUUID().toString();
        FactionRole role = new FactionRole(roleId, factionId, name,
                safePriority,
                permissions.getOrDefault("canInvite", false),
                permissions.getOrDefault("canKick", false),
                permissions.getOrDefault("canEditSettings", false),
                permissions.getOrDefault("canManageRoles", false),
                permissions.getOrDefault("canPromote", false),
                permissions.getOrDefault("canDemote", false),
                permissions.getOrDefault("canAccessBank", false));

        factionRoles.computeIfAbsent(factionId, k -> new ArrayList<>()).add(role);
        database.saveRole(role);
        return role;
    }

    /**
     * Edit a role. Same validation rules as {@link #createRole}.
     *
     * @return true if the role was saved (the actor is messaged on refusal)
     */
    public boolean editRole(UUID actorUuid, String roleId, String name, int priority, Map<String, Boolean> permissions) {
        FactionRole role = findRoleById(roleId);
        if (role == null || role.isLeaderRole()) return false;
        if (!canManageRole(actorUuid, role.getFactionId(), role)) {
            messagePlayer(actorUuid, "faction-no-permission-roles", null);
            return false;
        }
        if (!isValidRoleName(name)) {
            messagePlayer(actorUuid, "faction-name-invalid", null);
            return false;
        }

        int safePriority = sanitizeRolePermissions(actorUuid, priority, permissions);
        role.setName(name);
        role.setPriority(safePriority);
        if (permissions.containsKey("canInvite")) role.setCanInvite(permissions.get("canInvite"));
        if (permissions.containsKey("canKick")) role.setCanKick(permissions.get("canKick"));
        if (permissions.containsKey("canEditSettings")) role.setCanEditSettings(permissions.get("canEditSettings"));
        if (permissions.containsKey("canManageRoles")) role.setCanManageRoles(permissions.get("canManageRoles"));
        if (permissions.containsKey("canPromote")) role.setCanPromote(permissions.get("canPromote"));
        if (permissions.containsKey("canDemote")) role.setCanDemote(permissions.get("canDemote"));
        if (permissions.containsKey("canAccessBank")) role.setCanAccessBank(permissions.get("canAccessBank"));

        database.saveRole(role);
        return true;
    }

    /**
     * Delete a role. Refused while members still hold it — otherwise those members
     * would keep a dangling roleId and silently lose their role/permissions.
     *
     * @return true if the role was deleted (the actor is messaged on refusal)
     */
    public boolean deleteRole(UUID actorUuid, String roleId) {
        FactionRole role = findRoleById(roleId);
        if (role == null || role.isLeaderRole()) return false;
        if (!canManageRole(actorUuid, role.getFactionId(), role)) {
            messagePlayer(actorUuid, "faction-no-permission-roles", null);
            return false;
        }
        int inUse = getRoleMemberCount(roleId);
        if (inUse > 0) {
            messagePlayer(actorUuid, "faction-role-in-use", Map.of("count", String.valueOf(inUse)));
            return false;
        }

        List<FactionRole> roles = factionRoles.get(role.getFactionId());
        if (roles != null) roles.removeIf(r -> r.getRoleId().equals(roleId));
        database.deleteRole(roleId);
        return true;
    }

    /** Server-side check used by the GUI to decide whether a role may be opened for editing. */
    public boolean canManageRole(UUID actorUuid, String roleId) {
        FactionRole role = findRoleById(roleId);
        if (role == null) return false;
        return canManageRole(actorUuid, role.getFactionId(), role);
    }

    public void assignRole(UUID assignerUuid, UUID targetUuid, String roleId) {
        FactionRole assignerRole = getPlayerRole(assignerUuid);
        FactionRole targetRole = findRoleById(roleId);
        if (assignerRole == null || targetRole == null) return;

        // Assigner and target must be in the SAME faction, and the role must belong
        // to that faction. Without this, a stale GUI (target switched factions while
        // the menu was open) could graft a role from another faction onto the member.
        String assignerFaction = playerFaction.get(assignerUuid);
        if (assignerFaction == null
                || !assignerFaction.equals(playerFaction.get(targetUuid))
                || !assignerFaction.equals(targetRole.getFactionId())) {
            return;
        }

        // Server-side permission re-check (the GUI gates on these, but a demoted
        // player with a stale menu must not keep assigning roles).
        if (!assignerRole.isLeaderRole()
                && !assignerRole.isCanPromote()
                && !assignerRole.isCanDemote()
                && !assignerRole.isCanManageRoles()) {
            messagePlayer(assignerUuid, "faction-no-permission-roles", null);
            return;
        }

        // Can only assign roles with higher priority number (lower rank) than your own
        if (!assignerRole.isLeaderRole() && targetRole.getPriority() <= assignerRole.getPriority()) {
            Player assigner = Bukkit.getPlayer(assignerUuid);
            if (assigner != null) assigner.sendMessage(lang.get("faction-cant-assign-higher", assigner));
            return;
        }

        FactionMember member = getMember(targetUuid);
        if (member == null) return;

        member.setRoleId(roleId);
        database.saveMember(member);

        // Messages
        Player assigner = Bukkit.getPlayer(assignerUuid);
        Player target = Bukkit.getPlayer(targetUuid);
        if (assigner != null) {
            assigner.sendMessage(lang.get("faction-role-assigned", assigner, Map.of(
                    "player", getPlayerName(targetUuid), "role", targetRole.getName())));
        }
        if (target != null) {
            Faction faction = factionsById.get(targetRole.getFactionId());
            target.sendMessage(lang.get("faction-role-assigned-target", target, Map.of(
                    "role", targetRole.getName(),
                    "faction", faction != null ? faction.getName() : "")));
            playSound(target, "role-changed");
        }
    }

    public FactionRole getPlayerRole(UUID playerUuid) {
        FactionMember member = getMember(playerUuid);
        if (member == null) return null;
        return findRoleById(member.getRoleId());
    }

    public List<FactionRole> getFactionRoles(String factionId) {
        return factionRoles.getOrDefault(factionId, Collections.emptyList());
    }

    public boolean hasPermission(UUID playerUuid, String permission) {
        FactionRole role = getPlayerRole(playerUuid);
        if (role == null) return false;
        if (role.isLeaderRole()) return true;
        return role.hasPermission(permission);
    }

    /** Count members that have this role */
    public int getRoleMemberCount(String roleId) {
        int count = 0;
        for (Map<UUID, FactionMember> members : factionMembers.values()) {
            for (FactionMember m : members.values()) {
                if (m.getRoleId().equals(roleId)) count++;
            }
        }
        return count;
    }

    // ================================================================
    //  SETTINGS
    // ================================================================

    /**
     * Authorization guard for every faction setting. Defence in depth on top of the
     * GUI gating, which only ran when {@link pt.henrique.serverfactions.gui.SettingsGui}
     * was <em>opened</em>: a player demoted, kicked or moved to another faction while
     * their menu was still on screen could keep clicking its buttons, and every
     * setter below wrote unconditionally.
     *
     * <p>The actor must be in the faction they are editing and either be its leader
     * or hold {@code canEditSettings}.
     */
    private boolean canEditSettings(UUID actorUuid, String factionId) {
        if (actorUuid == null || factionId == null) return false;
        if (!factionId.equals(playerFaction.get(actorUuid))) return false;

        Faction faction = factionsById.get(factionId);
        if (faction == null) return false;
        if (faction.getLeaderUuid().equals(actorUuid)) return true;

        FactionRole role = getPlayerRole(actorUuid);
        return role != null && (role.isLeaderRole() || role.isCanEditSettings());
    }

    /** Server-side check the settings GUIs use before opening. */
    public boolean canEditSettings(UUID actorUuid) {
        return canEditSettings(actorUuid, playerFaction.get(actorUuid));
    }

    /**
     * Common preamble for the setters: authorise the actor and return the faction,
     * or null (with the actor messaged) when the action is refused.
     */
    private Faction authorizeSettings(UUID actorUuid, String factionId) {
        if (!canEditSettings(actorUuid, factionId)) {
            messagePlayer(actorUuid, "faction-no-permission-settings", null);
            return null;
        }
        return factionsById.get(factionId);
    }

    /**
     * Rename a faction. Charset, length and uniqueness are enforced here rather
     * than only in the GUI, because the name is embedded unescaped in MiniMessage
     * templates and because {@code factionsByName} is a unique index: writing a
     * name another faction already holds would repoint that index at this faction.
     *
     * @return true if the rename was applied (the actor is messaged on refusal)
     */
    public boolean setName(UUID actorUuid, String factionId, String newName) {
        Faction faction = authorizeSettings(actorUuid, factionId);
        if (faction == null) return false;

        if (newName == null || !isValidName(newName)
                || newName.length() < config.getMinNameLength()
                || newName.length() > config.getMaxNameLength()) {
            messagePlayer(actorUuid, "faction-name-invalid", null);
            return false;
        }
        if (isNameTaken(newName) && !newName.equalsIgnoreCase(faction.getName())) {
            messagePlayer(actorUuid, "faction-name-taken", null);
            return false;
        }

        factionsByName.remove(faction.getName().toLowerCase());
        faction.setName(newName);
        factionsByName.put(newName.toLowerCase(), factionId);
        database.updateFaction(faction);
        return true;
    }

    /**
     * Change a faction's tag. Same reasoning as {@link #setName}; the tag is also
     * rendered unescaped through {@link #getFormattedTag} and PlaceholderAPI.
     *
     * @return true if the change was applied (the actor is messaged on refusal)
     */
    public boolean setTag(UUID actorUuid, String factionId, String newTag) {
        Faction faction = authorizeSettings(actorUuid, factionId);
        if (faction == null) return false;

        if (newTag == null || !isValidTag(newTag) || newTag.length() > config.getMaxTagLength()) {
            messagePlayer(actorUuid, "faction-name-invalid", null);
            return false;
        }
        if (isTagTaken(newTag) && !newTag.equalsIgnoreCase(faction.getTag())) {
            messagePlayer(actorUuid, "faction-tag-taken", null);
            return false;
        }

        factionsByTag.remove(faction.getTag().toLowerCase());
        faction.setTag(newTag);
        factionsByTag.put(newTag.toLowerCase(), factionId);
        invalidateFormattedTag(factionId);
        database.updateFaction(faction);
        return true;
    }

    public boolean setPrimaryColor(UUID actorUuid, String factionId, String color) {
        Faction faction = authorizeSettings(actorUuid, factionId);
        if (faction == null || !isAllowedColorTag(color)) return false;
        faction.setPrimaryColor(color);
        invalidateFormattedTag(factionId);
        database.updateFaction(faction);
        return true;
    }

    public boolean setSecondaryColor(UUID actorUuid, String factionId, String color) {
        Faction faction = authorizeSettings(actorUuid, factionId);
        if (faction == null || !isAllowedColorTag(color)) return false;
        faction.setSecondaryColor(color);
        invalidateFormattedTag(factionId);
        database.updateFaction(faction);
        return true;
    }

    public boolean setPublic(UUID actorUuid, String factionId, boolean isPublic) {
        Faction faction = authorizeSettings(actorUuid, factionId);
        if (faction == null) return false;
        faction.setPublic(isPublic);
        database.updateFaction(faction);
        return true;
    }

    public boolean setMemberLimit(UUID actorUuid, String factionId, int limit) {
        Faction faction = authorizeSettings(actorUuid, factionId);
        if (faction == null) return false;
        faction.setMemberLimit(Math.max(1, Math.min(limit, config.getAbsoluteMaxMembers())));
        database.updateFaction(faction);
        return true;
    }

    public boolean setMemberLimitEnabled(UUID actorUuid, String factionId, boolean enabled) {
        Faction faction = authorizeSettings(actorUuid, factionId);
        if (faction == null) return false;
        faction.setMemberLimitEnabled(enabled);
        database.updateFaction(faction);
        return true;
    }

    // ================================================================
    //  QUERIES
    // ================================================================

    public Faction getFaction(String factionId) {
        return factionsById.get(factionId);
    }

    public Faction getFactionByPlayer(UUID playerUuid) {
        String factionId = playerFaction.get(playerUuid);
        return factionId != null ? factionsById.get(factionId) : null;
    }

    @Override
    public boolean areInSameFaction(UUID player1, UUID player2) {
        String f1 = playerFaction.get(player1);
        String f2 = playerFaction.get(player2);
        return f1 != null && f1.equals(f2);
    }

    @Override
    public String getFactionIdByPlayer(UUID player) {
        return playerFaction.get(player);
    }

    @Override
    public String getFactionName(String factionId) {
        Faction f = factionsById.get(factionId);
        return f != null ? f.getName() : null;
    }

    @Override
    public String getFactionTag(String factionId) {
        Faction f = factionsById.get(factionId);
        return f != null ? f.getTag() : null;
    }

    @Override
    public UUID getFactionLeader(String factionId) {
        Faction f = factionsById.get(factionId);
        return f != null ? f.getLeaderUuid() : null;
    }

    @Override
    public List<UUID> getFactionMemberUuids(String factionId) {
        Map<UUID, FactionMember> members = factionMembers.get(factionId);
        return members != null ? new ArrayList<>(members.keySet()) : Collections.emptyList();
    }

    @Override
    public boolean isInFaction(UUID player) {
        return playerFaction.containsKey(player);
    }

    @Override
    public String getBankAccountId(String factionId) {
        Faction f = factionsById.get(factionId);
        return f != null ? f.getBankAccountId() : null;
    }

    @Override
    public void setBankAccountId(String factionId, String accountId) {
        Faction f = factionsById.get(factionId);
        if (f != null) {
            f.setBankAccountId(accountId);
            database.updateFaction(f);
        }
    }

    /**
     * Get the formatted tag with colors, e.g., "<gray>[<red>TIW<gray>]"
     */
    public String getFormattedTag(String factionId) {
        return formattedTagCache.computeIfAbsent(factionId, id -> {
            Faction f = factionsById.get(id);
            if (f == null) return "";
            return f.getSecondaryColor() + "[" + f.getPrimaryColor() + f.getTag()
                    + f.getSecondaryColor() + "]";
        });
    }

    public List<Faction> getPublicFactions() {
        List<Faction> result = new ArrayList<>();
        for (Faction f : factionsById.values()) {
            if (f.isPublic()) result.add(f);
        }
        return result;
    }

    public int getMemberCount(String factionId) {
        Map<UUID, FactionMember> members = factionMembers.get(factionId);
        return members != null ? members.size() : 0;
    }

    public boolean isNameTaken(String name) {
        return factionsByName.containsKey(name.toLowerCase());
    }

    public boolean isTagTaken(String tag) {
        return factionsByTag.containsKey(tag.toLowerCase());
    }

    /** Lookup factionId by name (case-insensitive). Returns null if not found. */
    public String getFactionIdByName(String name) {
        return factionsByName.get(name.toLowerCase());
    }

    /** Get all factions (for suggestions/admin). */
    public Collection<Faction> getAllFactions() {
        return factionsById.values();
    }

    public Collection<FactionMember> getFactionMembersList(String factionId) {
        Map<UUID, FactionMember> members = factionMembers.get(factionId);
        return members != null ? members.values() : Collections.emptyList();
    }

    // ================================================================
    //  FACTION CHAT
    // ================================================================

    public boolean isFactionChatActive(UUID playerUuid) {
        return factionChatActive.contains(playerUuid);
    }

    public void toggleFactionChat(UUID playerUuid) {
        if (factionChatActive.contains(playerUuid)) {
            factionChatActive.remove(playerUuid);
        } else {
            factionChatActive.add(playerUuid);
        }
    }

    public void setFactionChatActive(UUID playerUuid, boolean active) {
        if (active) factionChatActive.add(playerUuid);
        else factionChatActive.remove(playerUuid);
    }

    // ================================================================
    //  HELPERS
    // ================================================================

    private FactionMember getMember(UUID playerUuid) {
        String factionId = playerFaction.get(playerUuid);
        if (factionId == null) return null;
        Map<UUID, FactionMember> members = factionMembers.get(factionId);
        return members != null ? members.get(playerUuid) : null;
    }

    public FactionRole findRoleById(String roleId) {
        for (List<FactionRole> roles : factionRoles.values()) {
            for (FactionRole r : roles) {
                if (r.getRoleId().equals(roleId)) return r;
            }
        }
        return null;
    }

    private FactionRole getLeaderRole(String factionId) {
        List<FactionRole> roles = factionRoles.get(factionId);
        if (roles == null) return null;
        return roles.stream().filter(r -> r.getPriority() == 0).findFirst().orElse(null);
    }

    /** Get the lowest-rank role (highest priority number) for assigning to new members. */
    private FactionRole getLowestRole(String factionId) {
        List<FactionRole> roles = factionRoles.get(factionId);
        if (roles == null || roles.isEmpty()) return null;
        return roles.stream().max(Comparator.comparingInt(FactionRole::getPriority)).orElse(null);
    }

    private void invalidateFormattedTag(String factionId) {
        formattedTagCache.remove(factionId);
    }

    private boolean isValidName(String name) {
        return name.matches("[a-zA-Z0-9 _-]+");
    }

    private boolean isValidTag(String tag) {
        return tag.matches("[a-zA-Z0-9_-]+");
    }

    /**
     * The colour palette the picker offers, and the only values accepted for a
     * faction's primary/secondary colour. Both are concatenated straight into
     * {@link #getFormattedTag}, chat formats and the PlaceholderAPI output without
     * escaping, so an arbitrary string here is a MiniMessage injection: a stored
     * {@code <click:run_command:...>} would render for every player who sees the
     * tag, not just the one who set it.
     */
    private static final Set<String> ALLOWED_COLOR_TAGS = Set.of(
            "<black>", "<dark_blue>", "<dark_green>", "<dark_aqua>",
            "<dark_red>", "<dark_purple>", "<gold>", "<gray>",
            "<dark_gray>", "<blue>", "<green>", "<aqua>",
            "<red>", "<light_purple>", "<yellow>", "<white>");

    private boolean isAllowedColorTag(String tag) {
        return tag != null && ALLOWED_COLOR_TAGS.contains(tag);
    }

    /**
     * Role names are embedded (unescaped) into MiniMessage GUI items, titles and
     * chat messages ({@code faction-role-assigned}), so restrict them to a safe
     * charset — same policy as faction names — and a sane length.
     */
    private boolean isValidRoleName(String name) {
        return name != null && name.matches("[a-zA-Z0-9 _-]{1,32}");
    }

    /** Send a lang message to a (possibly offline) player by UUID; no-op when offline. */
    private void messagePlayer(UUID playerUuid, String langKey, Map<String, String> placeholders) {
        if (playerUuid == null) return;
        Player p = Bukkit.getPlayer(playerUuid);
        if (p != null) {
            p.sendMessage(placeholders == null ? lang.get(langKey, p) : lang.get(langKey, p, placeholders));
        }
    }

    private String formatMoney(double amount) {
        return String.format("%.2f", amount);
    }

    private String getPlayerName(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) return p.getName();
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        // Never return null: the result is used in Map.of(...) placeholder maps,
        // which throw NullPointerException on null values.
        return name != null ? name : "?";
    }

    private void runSync(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    private void playSound(Player player, String soundKey) {
        String soundName = config.getSound(soundKey);
        if (soundName != null && player.isOnline()) {
            try {
                player.playSound(Sound.sound(
                        NamespacedKey.fromString(soundName),
                        Sound.Source.MASTER, 1.0f, 1.0f));
            } catch (Exception ignored) {
                // Invalid sound key — fail silently
            }
        }
    }

    /** Send a lang message to all online members of a faction. */
    private void broadcastToFaction(String factionId, String langKey,
                                    Map<String, String> placeholders, UUID exclude) {
        Map<UUID, FactionMember> members = factionMembers.get(factionId);
        if (members == null) return;
        for (UUID memberUuid : members.keySet()) {
            if (memberUuid.equals(exclude)) continue;
            Player p = Bukkit.getPlayer(memberUuid);
            if (p != null && p.isOnline()) {
                p.sendMessage(lang.get(langKey, p, placeholders));
            }
        }
    }
}

