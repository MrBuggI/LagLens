package io.github.mrbuggi.laglens.report;

import io.github.mrbuggi.laglens.model.ChunkHotspot;
import io.github.mrbuggi.laglens.storage.HistoryEntry;
import io.github.mrbuggi.laglens.storage.HistoryService;
import io.github.mrbuggi.laglens.storage.RepeatOffender;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Собирает текст для {@code /lagreport history}: по строке на сохранённый отчёт и список
 * чанков, которые повторяются от отчёта к отчёту.
 */
public final class HistoryReportBuilder {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.systemDefault());

    public List<String> build(HistoryService.View view) {
        List<String> lines = new ArrayList<>();
        lines.add("§6§lLagLens §7- история отчётов");

        if (view.entries().isEmpty()) {
            lines.add("§7Пока пусто. Отчёт попадает в историю после команды /lagreport.");
            return lines;
        }

        for (HistoryEntry entry : view.entries()) {
            lines.add(formatEntry(entry));
        }

        if (!view.offenders().isEmpty()) {
            lines.add("");
            lines.add("§eПовторяются от отчёта к отчёту:");
            for (RepeatOffender offender : view.offenders()) {
                lines.add(String.format("  §fчанк (%d, %d) в %s §7- в %d сохранённых отчётах",
                        offender.chunkX(), offender.chunkZ(), offender.worldName(), offender.reports()));
            }
        }
        return lines;
    }

    private String formatEntry(HistoryEntry entry) {
        String mspt = entry.mspt() >= 0 ? formatNumber(entry.mspt()) : "н/д";
        String line = String.format("§7%s  §eTPS §f%s  §eMSPT §f%s  §7проблем: §f%d",
                TIME.format(Instant.ofEpochMilli(entry.createdAtMillis())),
                formatNumber(Math.min(20.0, entry.tps1m())),
                mspt,
                entry.problems());

        if (entry.hotChunks().isEmpty()) {
            return line;
        }
        // Чанки хранятся от самого нагруженного, первый - главный подозреваемый этого отчёта.
        ChunkHotspot worst = entry.hotChunks().get(0);
        return line + String.format("  §7худший чанк §f(%d, %d) %s",
                worst.chunkX(), worst.chunkZ(), worst.worldName());
    }

    /** Locale.US - чтобы разделитель был точкой (68.4), а не запятой из русской локали. */
    private static String formatNumber(double value) {
        return String.format(Locale.US, "%.1f", value);
    }
}
