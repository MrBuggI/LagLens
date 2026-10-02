package io.github.mrbuggi.laglens.storage;

import io.github.mrbuggi.laglens.model.ChunkHotspot;

import java.util.List;

/**
 * Одна запись истории: сжатый итог отчёта, который хранится в базе.
 * <p>
 * Здесь только числа и строки, без типов Bukkit: запись создаётся на главном потоке,
 * а пишется в базу на фоновом.
 *
 * @param createdAtMillis время отчёта, миллисекунды с начала эпохи
 * @param tps1m           1-минутный TPS
 * @param mspt            среднее время тика в мс; {@code -1}, если сервер его не отдаёт
 * @param problems        сколько проблем нашёл отчёт
 * @param loadedChunks    загружено чанков во всех мирах
 * @param totalEntities   сущностей во всех мирах
 * @param hotChunks       «горячие» чанки этого отчёта
 */
public record HistoryEntry(
        long createdAtMillis,
        double tps1m,
        double mspt,
        int problems,
        int loadedChunks,
        int totalEntities,
        List<ChunkHotspot> hotChunks
) {
}
