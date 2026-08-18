package io.github.buggy.laglens.model;

import org.bukkit.entity.EntityType;

import java.util.Map;

/**
 * Снимок одного мира: сколько загружено чанков, сколько сущностей всего и по типам.
 * <p>
 * Собирается на главном потоке. Внутри - только числа и типы, никаких ссылок на живой
 * {@link org.bukkit.World} или {@link org.bukkit.entity.Entity}: за них нельзя держаться
 * между тиками.
 *
 * @param worldName        имя мира (world, world_nether, ...)
 * @param loadedChunks     количество загруженных чанков в этом мире
 * @param totalEntities    всего сущностей в мире
 * @param entityCountByType количество сущностей по каждому типу
 */
public record WorldStats(
        String worldName,
        int loadedChunks,
        int totalEntities,
        Map<EntityType, Integer> entityCountByType
) {

    /**
     * Количество брошенных предметов. В 1.21 тип называется {@code ITEM}
     * (в старых версиях - {@code DROPPED_ITEM}). Частый признак незакрытой ловушки мобов.
     */
    public int droppedItems() {
        return entityCountByType.getOrDefault(EntityType.ITEM, 0);
    }
}
