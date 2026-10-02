package io.github.mrbuggi.laglens.config;

import io.github.mrbuggi.laglens.LagLensPlugin;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Типобезопасный снимок настроек из config.yml.
 * <p>
 * Читаем конфиг один раз при загрузке и складываем в неизменяемые поля - так остальной код
 * не дёргает медленный доступ к YAML на каждый тик скана и работает с обычными числами.
 * Значения по умолчанию продублированы здесь на случай, если параметр удалили из файла.
 */
public final class LagLensConfig {

    private final int chunksPerTick;
    private final int chunkTileEntitiesThreshold;
    private final int chunkHoppersThreshold;
    private final int worldEntitiesThreshold;
    private final int worldDroppedItemsThreshold;
    private final double tpsWarning;
    private final double msptWarning;
    private final int topChunks;
    private final int topEntityClusters;

    private LagLensConfig(int chunksPerTick,
                          int chunkTileEntitiesThreshold,
                          int chunkHoppersThreshold,
                          int worldEntitiesThreshold,
                          int worldDroppedItemsThreshold,
                          double tpsWarning,
                          double msptWarning,
                          int topChunks,
                          int topEntityClusters) {
        this.chunksPerTick = chunksPerTick;
        this.chunkTileEntitiesThreshold = chunkTileEntitiesThreshold;
        this.chunkHoppersThreshold = chunkHoppersThreshold;
        this.worldEntitiesThreshold = worldEntitiesThreshold;
        this.worldDroppedItemsThreshold = worldDroppedItemsThreshold;
        this.tpsWarning = tpsWarning;
        this.msptWarning = msptWarning;
        this.topChunks = topChunks;
        this.topEntityClusters = topEntityClusters;
    }

    /**
     * Читает {@code config.yml} плагина и возвращает проверенный снимок настроек.
     * Значения зажимаются в разумные границы: например, 0 чанков за тик означал бы,
     * что скан никогда не завершится, поэтому минимум - 1.
     */
    public static LagLensConfig load(LagLensPlugin plugin) {
        FileConfiguration config = plugin.getConfig();

        return new LagLensConfig(
                Math.max(1, config.getInt("scan.chunks-per-tick", 200)),
                Math.max(1, config.getInt("thresholds.chunk-tile-entities", 50)),
                Math.max(1, config.getInt("thresholds.chunk-hoppers", 100)),
                Math.max(1, config.getInt("thresholds.world-entities", 1000)),
                Math.max(1, config.getInt("thresholds.world-dropped-items", 500)),
                config.getDouble("thresholds.tps-warning", 18.0),
                config.getDouble("thresholds.mspt-warning", 45.0),
                Math.max(1, config.getInt("report.top-chunks", 5)),
                Math.max(1, config.getInt("report.top-entity-clusters", 3))
        );
    }

    public int getChunksPerTick() {
        return chunksPerTick;
    }

    public int getChunkTileEntitiesThreshold() {
        return chunkTileEntitiesThreshold;
    }

    public int getChunkHoppersThreshold() {
        return chunkHoppersThreshold;
    }

    public int getWorldEntitiesThreshold() {
        return worldEntitiesThreshold;
    }

    public int getWorldDroppedItemsThreshold() {
        return worldDroppedItemsThreshold;
    }

    public double getTpsWarning() {
        return tpsWarning;
    }

    public double getMsptWarning() {
        return msptWarning;
    }

    public int getTopChunks() {
        return topChunks;
    }

    public int getTopEntityClusters() {
        return topEntityClusters;
    }
}
