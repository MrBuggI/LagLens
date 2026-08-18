package io.github.buggy.laglens.model;

/**
 * Полный снимок состояния сервера: дешёвые метрики плюс результат скана чанков.
 * Готовый вход для построения отчёта.
 *
 * @param metrics TPS, MSPT и статистика по мирам
 * @param scan    результат посекторного скана чанков
 */
public record ServerSnapshot(
        ServerMetrics metrics,
        ScanResult scan
) {
}
