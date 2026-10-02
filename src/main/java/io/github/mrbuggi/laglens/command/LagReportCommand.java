package io.github.mrbuggi.laglens.command;

import io.github.mrbuggi.laglens.LagLensPlugin;
import io.github.mrbuggi.laglens.config.LagLensConfig;
import io.github.mrbuggi.laglens.metrics.MetricsCollector;
import io.github.mrbuggi.laglens.model.ServerMetrics;
import io.github.mrbuggi.laglens.model.ServerSnapshot;
import io.github.mrbuggi.laglens.model.WorldStats;
import io.github.mrbuggi.laglens.report.HistoryReportBuilder;
import io.github.mrbuggi.laglens.report.Recommendations;
import io.github.mrbuggi.laglens.report.ReportBuilder;
import io.github.mrbuggi.laglens.scan.ChunkScanner;
import io.github.mrbuggi.laglens.storage.HistoryEntry;
import io.github.mrbuggi.laglens.storage.HistoryService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Обработчик команды {@code /lagreport}.
 * <p>
 * Без аргументов: проверить право → убедиться, что скан ещё не идёт → запустить посекторный
 * скан → по завершении собрать метрики, сохранить итог в историю, построить отчёт и отправить
 * его тому, кто вызвал команду.
 * <p>
 * {@code /lagreport history [число]} показывает последние сохранённые отчёты.
 */
public final class LagReportCommand implements TabExecutor {

    private static final String HISTORY = "history";

    private final LagLensPlugin plugin;
    private final LagLensConfig config;
    private final ChunkScanner chunkScanner;
    private final MetricsCollector metricsCollector;
    private final ReportBuilder reportBuilder;
    private final HistoryReportBuilder historyReportBuilder;
    /** {@code null}, если история выключена в конфиге или базу не удалось открыть. */
    private final HistoryService historyService;

    public LagReportCommand(LagLensPlugin plugin,
                            LagLensConfig config,
                            ChunkScanner chunkScanner,
                            MetricsCollector metricsCollector,
                            ReportBuilder reportBuilder,
                            HistoryReportBuilder historyReportBuilder,
                            HistoryService historyService) {
        this.plugin = plugin;
        this.config = config;
        this.chunkScanner = chunkScanner;
        this.metricsCollector = metricsCollector;
        this.reportBuilder = reportBuilder;
        this.historyReportBuilder = historyReportBuilder;
        this.historyService = historyService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Право есть и в plugin.yml, но проверяем сами - чтобы отказ был на понятном языке.
        if (!sender.hasPermission("laglens.use")) {
            sender.sendMessage("§cУ вас нет доступа к LagLens (требуется право laglens.use).");
            return true;
        }

        if (args.length == 0) {
            runReport(sender);
            return true;
        }
        if (args[0].equalsIgnoreCase(HISTORY)) {
            showHistory(sender, args);
            return true;
        }
        // Неизвестный аргумент: false заставит сервер показать usage из plugin.yml.
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && HISTORY.startsWith(args[0].toLowerCase())) {
            return List.of(HISTORY);
        }
        return List.of();
    }

    private void runReport(CommandSender sender) {
        // Пока идёт скан, второй не запускаем - иначе два обхода чанков сложатся в один лаг-спайк.
        if (chunkScanner.isRunning()) {
            sender.sendMessage("§eLagLens уже выполняет скан. Дождитесь его завершения.");
            return;
        }

        sender.sendMessage("§7LagLens: собираю данные, отчёт придёт через пару секунд...");
        chunkScanner.start(scanResult -> {
            // Колбэк выполняется в главном потоке - сбор метрик здесь безопасен.
            ServerMetrics metrics = metricsCollector.collect();
            ServerSnapshot snapshot = new ServerSnapshot(metrics, scanResult);

            // В историю отчёт попадает, даже если получатель уже вышел с сервера.
            if (historyService != null) {
                historyService.save(toHistoryEntry(snapshot));
            }
            deliver(sender, snapshot);
        });
    }

    private HistoryEntry toHistoryEntry(ServerSnapshot snapshot) {
        ServerMetrics metrics = snapshot.metrics();
        return new HistoryEntry(
                System.currentTimeMillis(),
                metrics.tps1m(),
                metrics.mspt(),
                Recommendations.detect(snapshot, config).size(),
                metrics.worlds().stream().mapToInt(WorldStats::loadedChunks).sum(),
                metrics.worlds().stream().mapToInt(WorldStats::totalEntities).sum(),
                snapshot.scan().topChunks());
    }

    private void showHistory(CommandSender sender, String[] args) {
        if (historyService == null) {
            sender.sendMessage("§eИстория отчётов выключена (history.enabled в config.yml) или база недоступна.");
            return;
        }

        int limit = config.getHistoryShown();
        if (args.length > 1) {
            try {
                limit = Math.clamp(Integer.parseInt(args[1]), 1, config.getHistoryKeep());
            } catch (NumberFormatException e) {
                sender.sendMessage("§cКоличество отчётов должно быть числом: /lagreport history 10");
                return;
            }
        }

        // Чтение идёт на фоновом потоке, сюда результат возвращается уже в главном.
        historyService.load(limit, view -> {
            if (!isReachable(sender)) {
                return;
            }
            if (view == null) {
                sender.sendMessage("§cНе удалось прочитать историю. Подробности в консоли сервера.");
                return;
            }
            for (String line : historyReportBuilder.build(view)) {
                sender.sendMessage(line);
            }
        });
    }

    private void deliver(CommandSender sender, ServerSnapshot snapshot) {
        if (!isReachable(sender)) {
            plugin.getLogger().info("Игрок " + sender.getName()
                    + " вышел до конца скана - отчёт не отправлен.");
            return;
        }

        for (String line : reportBuilder.build(snapshot)) {
            sender.sendMessage(line);
        }
    }

    /** Если команду вызвал игрок и он вышел, пока готовился ответ, отправлять его некуда. */
    private static boolean isReachable(CommandSender sender) {
        return !(sender instanceof Player player) || player.isOnline();
    }
}
