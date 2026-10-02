package io.github.mrbuggi.laglens.report;

import io.github.mrbuggi.laglens.config.LagLensConfig;
import io.github.mrbuggi.laglens.model.ChunkHotspot;
import io.github.mrbuggi.laglens.model.Problem;
import io.github.mrbuggi.laglens.model.ServerSnapshot;
import io.github.mrbuggi.laglens.model.WorldStats;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Превращает снимок сервера в список проблем с текстовыми рекомендациями.
 * <p>
 * Это эвристики, а не диагноз: плагин находит аномальные скопления и подсказывает,
 * что с ними обычно делают. Точную причину лага показывает профайлер (Spark) - сюда
 * мы не лезем.
 */
public final class Recommendations {

    private Recommendations() {
        // Утилитарный класс - экземпляры не нужны.
    }

    /**
     * Собирает проблемы: сначала «горячие» чанки (обычно бьют по тику сильнее),
     * затем перегруженные сущностями миры.
     */
    public static List<Problem> detect(ServerSnapshot snapshot, LagLensConfig config) {
        List<Problem> problems = new ArrayList<>();

        int totalTileEntities = snapshot.scan().totalTileEntities();
        for (ChunkHotspot chunk : snapshot.scan().topChunks()) {
            problems.add(describeChunk(chunk, totalTileEntities, config));
        }

        snapshot.metrics().worlds().stream()
                .filter(world -> world.totalEntities() >= config.getWorldEntitiesThreshold())
                .sorted(Comparator.comparingInt(WorldStats::totalEntities).reversed())
                .limit(config.getTopEntityClusters())
                .forEach(world -> problems.add(describeWorld(world, config)));

        return problems;
    }

    private static Problem describeChunk(ChunkHotspot chunk, int totalTileEntities, LagLensConfig config) {
        int sharePercent = totalTileEntities > 0
                ? Math.round(chunk.tileEntities() * 100.0f / totalTileEntities)
                : 0;

        String title = String.format("Чанк (%d, %d) в %s - %d тайл-энтити (%d%% всех на сервере)",
                chunk.chunkX(), chunk.chunkZ(), chunk.worldName(), chunk.tileEntities(), sharePercent);

        String detail = chunk.hoppers() > 0
                ? String.format("из них %d хопперов", chunk.hoppers())
                : "";

        boolean hopperHeavy = chunk.hoppers() >= config.getChunkHoppersThreshold();
        String recommendation = hopperHeavy
                ? "Сократить число хопперов или заменить связки на воронки-фильтры; разнести сортировщики по разным чанкам."
                : "Много блоков-механизмов в одном чанке - разнести их по разным чанкам или проверить, нет ли дублей ферм.";

        return new Problem(title, detail, recommendation);
    }

    private static Problem describeWorld(WorldStats world, LagLensConfig config) {
        String title = String.format("Мир %s - %d сущностей", world.worldName(), world.totalEntities());

        boolean manyItems = world.droppedItems() >= config.getWorldDroppedItemsThreshold();
        String detail = manyItems
                ? String.format("из них %d брошенных предметов", world.droppedItems())
                : "";

        String recommendation = manyItems
                ? "Настроить despawn предметов (item-despawn-rate), проверить незакрытые ловушки мобов."
                : "Проверить фермы мобов и лимиты спавна (spawn-limits) в конфиге сервера.";

        return new Problem(title, detail, recommendation);
    }
}
