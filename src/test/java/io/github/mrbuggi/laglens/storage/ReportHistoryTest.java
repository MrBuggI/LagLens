package io.github.mrbuggi.laglens.storage;

import io.github.mrbuggi.laglens.model.ChunkHotspot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportHistoryTest {

    private static final ChunkHotspot SORTER = new ChunkHotspot("world", 312, -88, 267, 267);
    private static final ChunkHotspot FARM = new ChunkHotspot("world_nether", 4, 9, 80, 12);

    private ReportHistory history;

    @BeforeEach
    void openInMemory() throws SQLException {
        history = ReportHistory.open("jdbc:sqlite::memory:");
    }

    @AfterEach
    void close() throws SQLException {
        history.close();
    }

    private static HistoryEntry entry(long time, ChunkHotspot... chunks) {
        return new HistoryEntry(time, 19.5, 31.2, chunks.length, 1644, 2100, List.of(chunks));
    }

    @Test
    void savedReportIsReadBackWithItsChunks() throws SQLException {
        history.save(entry(1_000L, SORTER, FARM), 10);

        List<HistoryEntry> latest = history.latest(5);

        assertEquals(1, latest.size());
        HistoryEntry read = latest.get(0);
        assertEquals(1_000L, read.createdAtMillis());
        assertEquals(19.5, read.tps1m());
        assertEquals(31.2, read.mspt());
        assertEquals(2, read.problems());
        assertEquals(1644, read.loadedChunks());
        assertEquals(2100, read.totalEntities());
        // Чанки возвращаются от самого нагруженного.
        assertEquals(List.of(SORTER, FARM), read.hotChunks());
    }

    @Test
    void latestReturnsNewestFirstAndRespectsLimit() throws SQLException {
        history.save(entry(1L), 10);
        history.save(entry(2L), 10);
        history.save(entry(3L), 10);

        List<HistoryEntry> latest = history.latest(2);

        assertEquals(List.of(3L, 2L), latest.stream().map(HistoryEntry::createdAtMillis).toList());
    }

    @Test
    void oldReportsArePrunedTogetherWithTheirChunks() throws SQLException {
        history.save(entry(1L, SORTER), 2);
        history.save(entry(2L, SORTER), 2);
        history.save(entry(3L), 2);

        assertEquals(2, history.size());
        assertEquals(List.of(3L, 2L), history.latest(10).stream().map(HistoryEntry::createdAtMillis).toList());
        // Чанк первого отчёта удалён каскадом: сортировщик остался только в одном отчёте.
        assertTrue(history.repeatOffenders(5).isEmpty());
    }

    @Test
    void repeatOffendersCountsReportsNotRows() throws SQLException {
        history.save(entry(1L, SORTER, FARM), 10);
        history.save(entry(2L, SORTER), 10);
        history.save(entry(3L, SORTER), 10);

        List<RepeatOffender> offenders = history.repeatOffenders(5);

        // Ферма встретилась один раз - это ещё не закономерность.
        assertEquals(List.of(new RepeatOffender("world", 312, -88, 3)), offenders);
    }

    @Test
    void historySurvivesReopeningTheFile(@TempDir Path dir) throws SQLException {
        Path file = dir.resolve("history.db");
        try (ReportHistory first = ReportHistory.open(file)) {
            first.save(entry(42L, FARM), 10);
        }

        try (ReportHistory second = ReportHistory.open(file)) {
            List<HistoryEntry> latest = second.latest(1);
            assertEquals(42L, latest.get(0).createdAtMillis());
            assertEquals(List.of(FARM), latest.get(0).hotChunks());
        }
    }
}
