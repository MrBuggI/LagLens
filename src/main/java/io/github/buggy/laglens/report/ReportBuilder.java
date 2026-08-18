package io.github.buggy.laglens.report;

import io.github.buggy.laglens.config.LagLensConfig;
import io.github.buggy.laglens.model.Problem;
import io.github.buggy.laglens.model.ServerMetrics;
import io.github.buggy.laglens.model.ServerSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Собирает текст отчёта из снимка сервера. Каждая строка отправляется в чат отдельным
 * сообщением. Форматированием занимается только этот класс - логика проблем живёт в
 * {@link Recommendations}.
 */
public final class ReportBuilder {

    private final LagLensConfig config;

    public ReportBuilder(LagLensConfig config) {
        this.config = config;
    }

    public List<String> build(ServerSnapshot snapshot) {
        List<String> lines = new ArrayList<>();
        ServerMetrics metrics = snapshot.metrics();

        lines.add("§6§lLagLens");
        lines.add(buildHeader(metrics));
        lines.add("");

        List<Problem> problems = Recommendations.detect(snapshot, config);
        if (problems.isEmpty()) {
            lines.add("§aПроблем не найдено - сервер в порядке.");
        } else {
            lines.add(String.format("§eНайдено §f%d §e%s:", problems.size(), pluralizeProblems(problems.size())));
            lines.add("");

            int number = 1;
            for (Problem problem : problems) {
                lines.add(String.format("§c[%d] §f%s", number++, problem.title()));
                if (!problem.detail().isBlank()) {
                    lines.add("    §7" + problem.detail());
                }
                lines.add("    §a→ §f" + problem.recommendation());
                lines.add("");
            }
        }

        lines.add(buildLoadedChunksFooter(metrics));
        return lines;
    }

    private String buildHeader(ServerMetrics metrics) {
        String mspt = metrics.isMsptAvailable() ? formatNumber(metrics.mspt()) : "н/д";
        return String.format("§eTPS §f%s §7(1м) / §f%s §7(5м) / §f%s §7(15м)   §eMSPT §f%s",
                formatTps(metrics.tps1m()),
                formatTps(metrics.tps5m()),
                formatTps(metrics.tps15m()),
                mspt);
    }

    private String buildLoadedChunksFooter(ServerMetrics metrics) {
        String worlds = metrics.worlds().stream()
                .map(world -> world.worldName() + " " + world.loadedChunks())
                .collect(Collectors.joining(" §7| §f"));
        return "§7Загружено чанков: §f" + worlds;
    }

    /** TPS не бывает выше 20 - обрезаем, чтобы не показывать 20.01 из-за погрешности. */
    private String formatTps(double value) {
        return formatNumber(Math.min(20.0, value));
    }

    /** Locale.US - чтобы разделитель был точкой (68.4), а не запятой из русской локали. */
    private String formatNumber(double value) {
        return String.format(Locale.US, "%.1f", value);
    }

    /** Русская форма слова «проблема» в зависимости от числа. */
    private String pluralizeProblems(int count) {
        int lastTwoDigits = count % 100;
        if (lastTwoDigits >= 11 && lastTwoDigits <= 14) {
            return "проблем";
        }
        int lastDigit = count % 10;
        if (lastDigit == 1) {
            return "проблема";
        }
        if (lastDigit >= 2 && lastDigit <= 4) {
            return "проблемы";
        }
        return "проблем";
    }
}
