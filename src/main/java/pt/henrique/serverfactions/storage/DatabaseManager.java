package pt.henrique.serverfactions.storage;

import pt.henrique.serverfactions.ServerFactions;
import pt.henrique.serverfactions.model.Faction;
import pt.henrique.serverfactions.model.FactionInvite;
import pt.henrique.serverfactions.model.FactionMember;
import pt.henrique.serverfactions.model.FactionRole;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * SQLite persistence layer. All write operations are async (single-thread executor).
 * Load operations are synchronous (called during startup).
 */
public class DatabaseManager {

    private final ServerFactions plugin;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ServerFactions-DB");
        t.setDaemon(true);
        return t;
    });
    private Connection connection;

    public DatabaseManager(ServerFactions plugin) {
        this.plugin = plugin;
        connect();
        createTables();
    }

    private void connect() {
        try {
            File dbFile = new File(plugin.getDataFolder(), "factions.db");
            if (!dbFile.getParentFile().exists()) {
                dbFile.getParentFile().mkdirs();
            }
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL;");
                stmt.execute("PRAGMA foreign_keys=ON;");
            }
            plugin.getLogger().info("SQLite database connected.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to connect to SQLite: " + e.getMessage());
        }
    }

    private void createTables() {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS factions (
                    faction_id TEXT PRIMARY KEY,
                    name TEXT NOT NULL UNIQUE,
                    tag TEXT NOT NULL UNIQUE,
                    leader_uuid TEXT NOT NULL,
                    primary_color TEXT DEFAULT '<white>',
                    secondary_color TEXT DEFAULT '<gray>',
                    is_public BOOLEAN DEFAULT FALSE,
                    member_limit INTEGER DEFAULT 0,
                    member_limit_enabled BOOLEAN DEFAULT FALSE,
                    bank_account_id TEXT,
                    created_at BIGINT NOT NULL
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS faction_members (
                    player_uuid TEXT NOT NULL,
                    faction_id TEXT NOT NULL,
                    role_id TEXT NOT NULL,
                    joined_at BIGINT NOT NULL,
                    PRIMARY KEY (player_uuid),
                    FOREIGN KEY (faction_id) REFERENCES factions(faction_id)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS faction_roles (
                    role_id TEXT PRIMARY KEY,
                    faction_id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    priority INTEGER NOT NULL,
                    can_invite BOOLEAN DEFAULT FALSE,
                    can_kick BOOLEAN DEFAULT FALSE,
                    can_edit_settings BOOLEAN DEFAULT FALSE,
                    can_manage_roles BOOLEAN DEFAULT FALSE,
                    can_promote BOOLEAN DEFAULT FALSE,
                    can_demote BOOLEAN DEFAULT FALSE,
                    can_access_bank BOOLEAN DEFAULT FALSE,
                    FOREIGN KEY (faction_id) REFERENCES factions(faction_id)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS faction_invites (
                    invited_uuid TEXT NOT NULL,
                    faction_id TEXT NOT NULL,
                    invited_by_uuid TEXT NOT NULL,
                    created_at BIGINT NOT NULL,
                    expires_at BIGINT NOT NULL,
                    PRIMARY KEY (invited_uuid, faction_id)
                )
            """);

            plugin.getLogger().info("Database tables initialized.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create tables: " + e.getMessage());
        }
    }

    // ======================== FACTION CRUD ========================

    public CompletableFuture<Void> saveFaction(Faction f) {
        return runAsync(() -> {
            String sql = """
                INSERT OR REPLACE INTO factions
                (faction_id, name, tag, leader_uuid, primary_color, secondary_color,
                 is_public, member_limit, member_limit_enabled, bank_account_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, f.getFactionId());
                ps.setString(2, f.getName());
                ps.setString(3, f.getTag());
                ps.setString(4, f.getLeaderUuid().toString());
                ps.setString(5, f.getPrimaryColor());
                ps.setString(6, f.getSecondaryColor());
                ps.setBoolean(7, f.isPublic());
                ps.setInt(8, f.getMemberLimit());
                ps.setBoolean(9, f.isMemberLimitEnabled());
                ps.setString(10, f.getBankAccountId());
                ps.setLong(11, f.getCreatedAt());
                ps.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> updateFaction(Faction f) {
        return saveFaction(f); // INSERT OR REPLACE handles update
    }

    public CompletableFuture<Void> deleteFaction(String factionId) {
        return runAsync(() -> {
            // Delete in order due to foreign keys
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM faction_invites WHERE faction_id = ?")) {
                ps.setString(1, factionId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM faction_members WHERE faction_id = ?")) {
                ps.setString(1, factionId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM faction_roles WHERE faction_id = ?")) {
                ps.setString(1, factionId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM factions WHERE faction_id = ?")) {
                ps.setString(1, factionId);
                ps.executeUpdate();
            }
        });
    }

    public List<Faction> loadAllFactions() {
        List<Faction> list = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM factions")) {
            while (rs.next()) {
                Faction f = new Faction();
                f.setFactionId(rs.getString("faction_id"));
                f.setName(rs.getString("name"));
                f.setTag(rs.getString("tag"));
                f.setLeaderUuid(UUID.fromString(rs.getString("leader_uuid")));
                f.setPrimaryColor(rs.getString("primary_color"));
                f.setSecondaryColor(rs.getString("secondary_color"));
                f.setPublic(rs.getBoolean("is_public"));
                f.setMemberLimit(rs.getInt("member_limit"));
                f.setMemberLimitEnabled(rs.getBoolean("member_limit_enabled"));
                f.setBankAccountId(rs.getString("bank_account_id"));
                f.setCreatedAt(rs.getLong("created_at"));
                list.add(f);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load factions: " + e.getMessage());
        }
        return list;
    }

    // ======================== MEMBER CRUD ========================

    public CompletableFuture<Void> saveMember(FactionMember m) {
        return runAsync(() -> {
            String sql = """
                INSERT OR REPLACE INTO faction_members
                (player_uuid, faction_id, role_id, joined_at) VALUES (?, ?, ?, ?)
            """;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, m.getPlayerUuid().toString());
                ps.setString(2, m.getFactionId());
                ps.setString(3, m.getRoleId());
                ps.setLong(4, m.getJoinedAt());
                ps.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> deleteMember(UUID playerUuid) {
        return runAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM faction_members WHERE player_uuid = ?")) {
                ps.setString(1, playerUuid.toString());
                ps.executeUpdate();
            }
        });
    }

    public List<FactionMember> loadAllMembers() {
        List<FactionMember> list = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM faction_members")) {
            while (rs.next()) {
                FactionMember m = new FactionMember();
                m.setPlayerUuid(UUID.fromString(rs.getString("player_uuid")));
                m.setFactionId(rs.getString("faction_id"));
                m.setRoleId(rs.getString("role_id"));
                m.setJoinedAt(rs.getLong("joined_at"));
                list.add(m);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load members: " + e.getMessage());
        }
        return list;
    }

    // ======================== ROLE CRUD ========================

    public CompletableFuture<Void> saveRole(FactionRole r) {
        return runAsync(() -> {
            String sql = """
                INSERT OR REPLACE INTO faction_roles
                (role_id, faction_id, name, priority, can_invite, can_kick,
                 can_edit_settings, can_manage_roles, can_promote, can_demote, can_access_bank)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, r.getRoleId());
                ps.setString(2, r.getFactionId());
                ps.setString(3, r.getName());
                ps.setInt(4, r.getPriority());
                ps.setBoolean(5, r.isCanInvite());
                ps.setBoolean(6, r.isCanKick());
                ps.setBoolean(7, r.isCanEditSettings());
                ps.setBoolean(8, r.isCanManageRoles());
                ps.setBoolean(9, r.isCanPromote());
                ps.setBoolean(10, r.isCanDemote());
                ps.setBoolean(11, r.isCanAccessBank());
                ps.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> deleteRole(String roleId) {
        return runAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM faction_roles WHERE role_id = ?")) {
                ps.setString(1, roleId);
                ps.executeUpdate();
            }
        });
    }

    public List<FactionRole> loadAllRoles() {
        List<FactionRole> list = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM faction_roles")) {
            while (rs.next()) {
                FactionRole r = new FactionRole();
                r.setRoleId(rs.getString("role_id"));
                r.setFactionId(rs.getString("faction_id"));
                r.setName(rs.getString("name"));
                r.setPriority(rs.getInt("priority"));
                r.setCanInvite(rs.getBoolean("can_invite"));
                r.setCanKick(rs.getBoolean("can_kick"));
                r.setCanEditSettings(rs.getBoolean("can_edit_settings"));
                r.setCanManageRoles(rs.getBoolean("can_manage_roles"));
                r.setCanPromote(rs.getBoolean("can_promote"));
                r.setCanDemote(rs.getBoolean("can_demote"));
                r.setCanAccessBank(rs.getBoolean("can_access_bank"));
                list.add(r);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load roles: " + e.getMessage());
        }
        return list;
    }

    // ======================== INVITE CRUD ========================

    public CompletableFuture<Void> saveInvite(FactionInvite inv) {
        return runAsync(() -> {
            String sql = """
                INSERT OR REPLACE INTO faction_invites
                (invited_uuid, faction_id, invited_by_uuid, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?)
            """;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, inv.getInvitedUuid().toString());
                ps.setString(2, inv.getFactionId());
                ps.setString(3, inv.getInvitedByUuid().toString());
                ps.setLong(4, inv.getCreatedAt());
                ps.setLong(5, inv.getExpiresAt());
                ps.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> deleteInvite(UUID invitedUuid, String factionId) {
        return runAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM faction_invites WHERE invited_uuid = ? AND faction_id = ?")) {
                ps.setString(1, invitedUuid.toString());
                ps.setString(2, factionId);
                ps.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> deleteExpiredInvites() {
        return runAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM faction_invites WHERE expires_at < ?")) {
                ps.setLong(1, System.currentTimeMillis());
                ps.executeUpdate();
            }
        });
    }

    public List<FactionInvite> loadAllInvites() {
        List<FactionInvite> list = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM faction_invites")) {
            while (rs.next()) {
                FactionInvite inv = new FactionInvite();
                inv.setInvitedUuid(UUID.fromString(rs.getString("invited_uuid")));
                inv.setFactionId(rs.getString("faction_id"));
                inv.setInvitedByUuid(UUID.fromString(rs.getString("invited_by_uuid")));
                inv.setCreatedAt(rs.getLong("created_at"));
                inv.setExpiresAt(rs.getLong("expires_at"));
                list.add(inv);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load invites: " + e.getMessage());
        }
        return list;
    }

    // ======================== UTILITY ========================

    private CompletableFuture<Void> runAsync(SqlTask task) {
        return CompletableFuture.runAsync(() -> {
            try {
                task.run();
            } catch (SQLException e) {
                plugin.getLogger().severe("Database error: " + e.getMessage());
            }
        }, executor);
    }

    public void shutdown() {
        executor.shutdown();
        // Wait for queued async writes to finish before closing the connection.
        // Without this, pending writes on the DB thread race with connection.close()
        // on the main thread during onDisable and the last changes are silently lost.
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Pending database writes did not finish within 10s.");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Error closing database: " + e.getMessage());
        }
    }

    @FunctionalInterface
    private interface SqlTask {
        void run() throws SQLException;
    }
}

