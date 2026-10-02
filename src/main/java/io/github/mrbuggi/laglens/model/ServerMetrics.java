package io.github.mrbuggi.laglens.model;

import java.util.List;

/**
 * Дешёвая часть отчёта: TPS, MSPT и статистика по мирам.
 * Собирается синхронно за один тик (см. {@link io.github.mrbuggi.laglens.metrics.MetricsCollector}).
 * Топ «горячих» чанков добавляется отдельно на этапе скана.
 *
 * @param tps1m 1-минутный TPS
 * @param tps5m 5-минутный TPS
 * @param tps15m 15-минутный TPS
 * @param mspt среднее время тика в мс; {@code -1}, если сервер не отдаёт эту метрику
 * @param worlds статистика по каждому загруженному миру
 */
public record ServerMetrics(
        double tps1m,
        double tps5m,
        double tps15m,
        double mspt,
        List<WorldStats> worlds
) {

    /** {@code false} на серверах без {@code getAverageTickTime()} (древний CraftBukkit). */
    public boolean isMsptAvailable() {
        return mspt >= 0;
    }
}
