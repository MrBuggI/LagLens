package io.github.mrbuggi.laglens.storage;

/**
 * Чанк, который попадал в «горячие» больше одного раза.
 *
 * @param worldName имя мира
 * @param chunkX    X-координата чанка
 * @param chunkZ    Z-координата чанка
 * @param reports   в скольких сохранённых отчётах он встретился
 */
public record RepeatOffender(
        String worldName,
        int chunkX,
        int chunkZ,
        int reports
) {
}
