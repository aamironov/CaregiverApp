package com.carebinder.backend;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;

public final class Database {
    private final String jdbcUrl;

    public Database(Path path) throws IOException {
        Path absolute = path.toAbsolutePath();
        if (absolute.getParent() != null) Files.createDirectories(absolute.getParent());
        this.jdbcUrl = "jdbc:sqlite:" + absolute;
    }

    public Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
        return connection;
    }

    public void initialize() throws SQLException {
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS users (
                  id TEXT PRIMARY KEY,
                  email TEXT NOT NULL UNIQUE,
                  password_hash TEXT NOT NULL,
                  password_salt TEXT NOT NULL,
                  preferred_language TEXT NOT NULL DEFAULT 'en',
                  created_at TEXT NOT NULL
                )
                """);
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sessions (
                  token_hash TEXT PRIMARY KEY,
                  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                  expires_at TEXT NOT NULL,
                  created_at TEXT NOT NULL
                )
                """);
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS recipients (
                  id TEXT PRIMARY KEY,
                  user_id TEXT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
                  display_name TEXT NOT NULL,
                  relationship TEXT NOT NULL,
                  created_at TEXT NOT NULL,
                  updated_at TEXT NOT NULL
                )
                """);
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS assets (
                  id TEXT PRIMARY KEY,
                  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                  recipient_id TEXT NOT NULL REFERENCES recipients(id) ON DELETE CASCADE,
                  content_type TEXT NOT NULL,
                  filename TEXT NOT NULL,
                  bytes BLOB,
                  expires_at TEXT NOT NULL,
                  created_at TEXT NOT NULL
                )
                """);
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS drafts (
                  id TEXT PRIMARY KEY,
                  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                  recipient_id TEXT NOT NULL REFERENCES recipients(id) ON DELETE CASCADE,
                  source_type TEXT NOT NULL,
                  asset_id TEXT REFERENCES assets(id) ON DELETE SET NULL,
                  payload_json TEXT NOT NULL,
                  created_at TEXT NOT NULL,
                  updated_at TEXT NOT NULL
                )
                """);
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS events (
                  id TEXT PRIMARY KEY,
                  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                  recipient_id TEXT NOT NULL REFERENCES recipients(id) ON DELETE CASCADE,
                  source_type TEXT NOT NULL,
                  asset_id TEXT REFERENCES assets(id) ON DELETE SET NULL,
                  event_summary TEXT NOT NULL,
                  family_update TEXT NOT NULL,
                  questions_json TEXT NOT NULL DEFAULT '[]',
                  occurred_on TEXT NOT NULL,
                  timing_mode TEXT NOT NULL DEFAULT 'ALL_DAY',
                  starts_at TEXT,
                  ends_at TEXT,
                  recurrence_frequency TEXT NOT NULL DEFAULT 'NONE',
                  recurrence_interval INTEGER NOT NULL DEFAULT 1,
                  recurrence_until TEXT,
                  icon_key TEXT NOT NULL DEFAULT 'note',
                  color_key TEXT NOT NULL DEFAULT 'teal',
                  confirmed_at TEXT NOT NULL
                )
                """);
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS tasks (
                  id TEXT PRIMARY KEY,
                  event_id TEXT NOT NULL REFERENCES events(id) ON DELETE CASCADE,
                  kind TEXT NOT NULL,
                  title TEXT NOT NULL,
                  due_date TEXT,
                  reminder_at TEXT,
                  source_text TEXT NOT NULL,
                  decision TEXT NOT NULL,
                  completed INTEGER NOT NULL DEFAULT 0,
                  completed_at TEXT
                )
                """);
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS exports (
                  id TEXT PRIMARY KEY,
                  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                  expires_at TEXT NOT NULL
                )
                """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_events_recipient ON events(recipient_id, confirmed_at DESC)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_tasks_event ON tasks(event_id)");
            if (!hasColumn(connection, "users", "google_sub")) {
                statement.executeUpdate("ALTER TABLE users ADD COLUMN google_sub TEXT");
            }
            addColumnIfMissing(connection, statement, "users", "preferred_language", "TEXT NOT NULL DEFAULT 'en'");
            addColumnIfMissing(connection, statement, "events", "timing_mode", "TEXT NOT NULL DEFAULT 'ALL_DAY'");
            addColumnIfMissing(connection, statement, "events", "starts_at", "TEXT");
            addColumnIfMissing(connection, statement, "events", "ends_at", "TEXT");
            addColumnIfMissing(connection, statement, "events", "recurrence_frequency", "TEXT NOT NULL DEFAULT 'NONE'");
            addColumnIfMissing(connection, statement, "events", "recurrence_interval", "INTEGER NOT NULL DEFAULT 1");
            addColumnIfMissing(connection, statement, "events", "recurrence_until", "TEXT");
            addColumnIfMissing(connection, statement, "events", "icon_key", "TEXT NOT NULL DEFAULT 'note'");
            addColumnIfMissing(connection, statement, "events", "color_key", "TEXT NOT NULL DEFAULT 'teal'");
            statement.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS idx_users_google_sub ON users(google_sub) WHERE google_sub IS NOT NULL");
        }
    }

    private void addColumnIfMissing(Connection connection, Statement statement, String table, String column, String definition) throws SQLException {
        if (!hasColumn(connection, table, column)) statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
    }

    private boolean hasColumn(Connection connection, String table, String column) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (result.next()) if (column.equals(result.getString("name"))) return true;
            return false;
        }
    }
}
