package io.github.buggy.laglens.model;

import java.util.List;

/**
 * Итог посекторного скана чанков.
 *
 * @param totalTileEntities всего тайл-энтити на сервере (по всем просканированным чанкам) -
 *                          нужно, чтобы посчитать долю «горячего» чанка
 * @param topChunks         топ чанков по количеству тайл-энтити, уже отсортированный
 */
public record ScanResult(
        int totalTileEntities,
        List<ChunkHotspot> topChunks
) {
}
