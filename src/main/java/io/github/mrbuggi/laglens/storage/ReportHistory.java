package io.github.mrbuggi.laglens.storage;

import io.github.mrbuggi.laglens.model.ChunkHotspot;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * История отчётов в файле SQLite.
 * <p>
 * Класс синхронный и не знает про Bukkit: его можно проверить обычным тестом.
 * Все методы блокируют поток на время запроса, поэтому на сервере их вызывает
 * {@link HistoryService} с фонового потока. Соединение одно, методы синхронизированы.
 * <p>
 * Схема: {@code reports} - по строке на отчёт, {@code hot_chunks} - «горячие» чанки отчёта
 * со ссылкой на него. Удаление отчёта каскадно удаляет его чанки.
 */
public final class ReportHistory implements AutoCloseable {

    private static final String[] SCHEMA = {
            """
            CREATE TABLE IF NOT EXISTS reports (
                id             INTEGER PRIMARY KEY AUTOINCREMENT,
                created_at     INTEGER NOT NULL,
                tps_1m         REAL    NOT NULL,
                mspt           REAL    NOT NULL,
                problems       INTEGER NOT NULL,
                loaded_chunks  INTEGER NOT NULL,
                total_entities INTEGER NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS hot_chunks (
                report_id     INTEGER NOT NULL REFERENCES reports (id) ON DELETE CASCADE,
                world         TEXT    NOT NULL,
                chunk_x       INTEGER NOT NULL,
                chunk_z       INTEGER NOT NULL,
                tile_entities INTEGER NOT NULL,
                hoppers       INTEGER NOT NULL
            )
            """,
            "CREATE INDEX IF NOT EXISTS hot_chunks_report ON hot_chunks (report_id)",
            "CREATE INDEX IF NOT EXISTS hot_chunks_place ON hot_chunks (world, chunk_x, chunk_z)"
    };

    private final Connection connection;

    private ReportHistory(Connection connection) {
        this.connection = connection;
    }

    /** Открывает базу в файле, создавая файл и таблицы при первом запуске. */
    public static ReportHistory open(Path file) throws SQLException {
        return open("jdbc:sqlite:" + file.toAbsolutePath());
    }

    /** Открывает базу по готовому JDBC-адресу. Для тестов: {@code jdbc:sqlite::memory:}. */
    static ReportHistory open(String jdbcUrl) throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            // В SQLite внешние ключи выключены по умолчанию и включаются на каждое соединение.
            statement.execute("PRAGMA foreign_keys = ON");
            for (String sql : SCHEMA) {
                statement.execute(sql);
            }
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
        return new ReportHistory(connection);
    }

    /**
     * Сохраняет отчёт вместе с его чанками и удаляет самые старые записи сверх {@code keep}.
     * Всё в одной транзакции: либо отчёт записан целиком, либо не записан вовсе.
     */
    public synchronized void save(HistoryEntry entry, int keep) throws SQLException {
        connection.setAutoCommit(false);
        try {
            long reportId = insertReport(entry);
            insertHotChunks(reportId, entry.hotChunks());
            prune(keep);
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private long insertReport(HistoryEntry entry) throws SQLException {
        String sql = """
                INSERT INTO reports (created_at, tps_1m, mspt, problems, loaded_chunks, total_entities)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, entry.createdAtMillis());
            statement.setDouble(2, entry.tps1m());
            statement.setDouble(3, entry.mspt());
            statement.setInt(4, entry.problems());
            statement.setInt(5, entry.loadedChunks());
            statement.setInt(6, entry.totalEntities());
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("База не вернула id сохранённого отчёта");
                }
                return keys.getLong(1);
            }
        }
    }

    private void insertHotChunks(long reportId, List<ChunkHotspot> chunks) throws SQLException {
        if (chunks.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO hot_chunks (report_id, world, chunk_x, chunk_z, tile_entities, hoppers)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (ChunkHotspot chunk : chunks) {
                statement.setLong(1, reportId);
                statement.setString(2, chunk.worldName());
                statement.setInt(3, chunk.chunkX());
                statement.setInt(4, chunk.chunkZ());
                statement.setInt(5, chunk.tileEntities());
                statement.setInt(6, chunk.hoppers());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void prune(int keep) throws SQLException {
        String sql = "DELETE FROM reports WHERE id NOT IN (SELECT id FROM reports ORDER BY id DESC LIMIT ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Math.max(1, keep));
            statement.executeUpdate();
        }
    }

    /** Последние отчёты, от нового к старому. */
    public synchronized List<HistoryEntry> latest(int limit) throws SQLException {
        String sql = """
                SELECT id, created_at, tps_1m, mspt, problems, loaded_chunks, total_entities
                FROM reports
                ORDER BY id DESC
                LIMIT ?
                """;
        List<HistoryEntry> entries = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Math.max(1, limit));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    entries.add(new HistoryEntry(
                            rows.getLong("created_at"),
                            rows.getDouble("tps_1m"),
                            rows.getDouble("mspt"),
                            rows.getInt("problems"),
                            rows.getInt("loaded_chunks"),
                            rows.getInt("total_entities"),
                            hotChunksOf(rows.getLong("id"))));
                }
            }
        }
        return entries;
    }

    private List<ChunkHotspot> hotChunksOf(long reportId) throws SQLException {
        String sql = """
                SELECT world, chunk_x, chunk_z, tile_entities, hoppers
                FROM hot_chunks
                WHERE report_id = ?
                ORDER BY tile_entities DESC
                """;
        List<ChunkHotspot> chunks = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, reportId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    chunks.add(new ChunkHotspot(
                            rows.getString("world"),
                            rows.getInt("chunk_x"),
                            rows.getInt("chunk_z"),
                            rows.getInt("tile_entities"),
                            rows.getInt("hoppers")));
                }
            }
        }
        return chunks;
    }

    /**
     * Чанки, которые встречаются в нескольких сохранённых отчётах, - от самых частых.
     * Разовый всплеск сюда не попадает: один отчёт ещё не закономерность.
     */
    public synchronized List<RepeatOffender> repeatOffenders(int limit) throws SQLException {
        String sql = """
                SELECT world, chunk_x, chunk_z, COUNT(DISTINCT report_id) AS reports
                FROM hot_chunks
                GROUP BY world, chunk_x, chunk_z
                HAVING reports > 1
                ORDER BY reports DESC, world, chunk_x, chunk_z
                LIMIT ?
                """;
        List<RepeatOffender> offenders = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Math.max(1, limit));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    offenders.add(new RepeatOffender(
                            rows.getString("world"),
                            rows.getInt("chunk_x"),
                            rows.getInt("chunk_z"),
                            rows.getInt("reports")));
                }
            }
        }
        return offenders;
    }

    /** Сколько отчётов сейчас хранится. */
    public synchronized int size() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM reports")) {
            return rows.next() ? rows.getInt(1) : 0;
        }
    }

    @Override
    public synchronized void close() throws SQLException {
        connection.close();
    }
}
