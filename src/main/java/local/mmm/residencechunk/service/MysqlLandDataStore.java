package local.mmm.residencechunk.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import local.mmm.residencechunk.config.PluginSettings.MysqlStorageSettings;
import local.mmm.residencechunk.model.ChunkBounds;
import local.mmm.residencechunk.model.ManagedClaim;

public final class MysqlLandDataStore implements LandDataStore {

    private final MysqlStorageSettings settings;
    private final YamlLandDataStore yamlMigrationSource;
    private final String tableName;
    private final Map<String, ManagedClaim> claims = new LinkedHashMap<>();

    public MysqlLandDataStore(MysqlStorageSettings settings, YamlLandDataStore yamlMigrationSource) {
        this.settings = settings;
        this.yamlMigrationSource = yamlMigrationSource;
        this.tableName = sanitizeIdentifier(settings.tablePrefix()) + "claims";
    }

    @Override
    public void load() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            initializeSchema();
            if (settings.migrateFromYamlIfEmpty() && isTableEmpty()) {
                migrateFromYaml();
            }
            loadCache();
        } catch (ClassNotFoundException exception) {
            throw new RuntimeException("MySQL JDBC driver not found. Please make sure mysql-connector-j is available.", exception);
        }
    }

    @Override
    public void save() {
        // MySQL writes are applied immediately in put/remove.
    }

    @Override
    public Collection<ManagedClaim> allClaims() {
        return Collections.unmodifiableCollection(claims.values());
    }

    @Override
    public ManagedClaim find(String residenceName) {
        return claims.get(residenceName.toLowerCase());
    }

    @Override
    public List<ManagedClaim> findOwnedBy(UUID ownerUuid) {
        return claims.values().stream()
            .filter(claim -> claim.ownerUuid().equals(ownerUuid))
            .toList();
    }

    @Override
    public void put(ManagedClaim claim) {
        String sql = """
            INSERT INTO %s (
                residence_name, display_name, owner_uuid, owner_name, world_name,
                min_chunk_x, max_chunk_x, min_chunk_z, max_chunk_z, public_teleport
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                display_name = VALUES(display_name),
                owner_uuid = VALUES(owner_uuid),
                owner_name = VALUES(owner_name),
                world_name = VALUES(world_name),
                min_chunk_x = VALUES(min_chunk_x),
                max_chunk_x = VALUES(max_chunk_x),
                min_chunk_z = VALUES(min_chunk_z),
                max_chunk_z = VALUES(max_chunk_z),
                public_teleport = VALUES(public_teleport),
                updated_at = CURRENT_TIMESTAMP
            """.formatted(quotedTableName());
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindClaim(statement, claim);
            statement.executeUpdate();
            claims.put(claim.residenceName().toLowerCase(), claim);
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to save claim to MySQL: " + claim.residenceName(), exception);
        }
    }

    @Override
    public void remove(String residenceName) {
        String sql = "DELETE FROM " + quotedTableName() + " WHERE residence_name = ?";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, residenceName);
            statement.executeUpdate();
            claims.remove(residenceName.toLowerCase());
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to delete claim from MySQL: " + residenceName, exception);
        }
    }

    private void initializeSchema() {
        String sql = """
            CREATE TABLE IF NOT EXISTS %s (
                residence_name VARCHAR(128) NOT NULL,
                display_name VARCHAR(128) NOT NULL,
                owner_uuid CHAR(36) NOT NULL,
                owner_name VARCHAR(32) NOT NULL,
                world_name VARCHAR(128) NOT NULL,
                min_chunk_x INT NOT NULL,
                max_chunk_x INT NOT NULL,
                min_chunk_z INT NOT NULL,
                max_chunk_z INT NOT NULL,
                public_teleport BOOLEAN NOT NULL DEFAULT FALSE,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                PRIMARY KEY (residence_name),
                INDEX idx_owner_uuid (owner_uuid),
                INDEX idx_world_name (world_name)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """.formatted(quotedTableName());
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to initialize MySQL claim table.", exception);
        }
    }

    private boolean isTableEmpty() {
        String sql = "SELECT COUNT(*) FROM " + quotedTableName();
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() && resultSet.getLong(1) == 0L;
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to check MySQL claim table.", exception);
        }
    }

    private void migrateFromYaml() {
        yamlMigrationSource.load();
        for (ManagedClaim claim : yamlMigrationSource.allClaims()) {
            put(claim);
        }
    }

    private void loadCache() {
        claims.clear();
        String sql = "SELECT * FROM " + quotedTableName();
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                ManagedClaim claim = readClaim(resultSet);
                claims.put(claim.residenceName().toLowerCase(), claim);
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to load claims from MySQL.", exception);
        }
    }

    private ManagedClaim readClaim(ResultSet resultSet) throws SQLException {
        return new ManagedClaim(
            resultSet.getString("residence_name"),
            resultSet.getString("display_name"),
            UUID.fromString(resultSet.getString("owner_uuid")),
            resultSet.getString("owner_name"),
            resultSet.getString("world_name"),
            new ChunkBounds(
                resultSet.getInt("min_chunk_x"),
                resultSet.getInt("max_chunk_x"),
                resultSet.getInt("min_chunk_z"),
                resultSet.getInt("max_chunk_z")
            ),
            resultSet.getBoolean("public_teleport")
        );
    }

    private void bindClaim(PreparedStatement statement, ManagedClaim claim) throws SQLException {
        statement.setString(1, claim.residenceName());
        statement.setString(2, claim.displayName());
        statement.setString(3, claim.ownerUuid().toString());
        statement.setString(4, claim.ownerName());
        statement.setString(5, claim.worldName());
        statement.setInt(6, claim.bounds().minChunkX());
        statement.setInt(7, claim.bounds().maxChunkX());
        statement.setInt(8, claim.bounds().minChunkZ());
        statement.setInt(9, claim.bounds().maxChunkZ());
        statement.setBoolean(10, claim.publicTeleport());
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(settings.jdbcUrl(), settings.username(), settings.password());
    }

    private String quotedTableName() {
        return "`" + tableName + "`";
    }

    private String sanitizeIdentifier(String input) {
        String value = input == null ? "" : input;
        String sanitized = value.replaceAll("[^A-Za-z0-9_]", "_");
        if (sanitized.length() > 48) {
            return sanitized.substring(0, 48);
        }
        return sanitized;
    }
}
