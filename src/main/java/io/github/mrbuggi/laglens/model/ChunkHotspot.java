package io.github.mrbuggi.laglens.model;

/**
 * «Горячий» чанк - кандидат в источники лагов.
 * <p>
 * Храним только координаты и числа, без ссылки на живой {@link org.bukkit.Chunk}:
 * к моменту вывода отчёта чанк может уже выгрузиться.
 *
 * @param worldName    имя мира
 * @param chunkX       X-координата чанка (в чанках, не в блоках)
 * @param chunkZ       Z-координата чанка
 * @param tileEntities всего тайл-энтити в чанке
 * @param hoppers      из них хопперов
 */
public record ChunkHotspot(
        String worldName,
        int chunkX,
        int chunkZ,
        int tileEntities,
        int hoppers
) {
}
