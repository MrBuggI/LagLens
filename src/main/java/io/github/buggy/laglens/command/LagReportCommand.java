package io.github.buggy.laglens.command;

import io.github.buggy.laglens.LagLensPlugin;
import io.github.buggy.laglens.metrics.MetricsCollector;
import io.github.buggy.laglens.model.ServerMetrics;
import io.github.buggy.laglens.model.ServerSnapshot;
import io.github.buggy.laglens.report.ReportBuilder;
import io.github.buggy.laglens.scan.ChunkScanner;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Обработчик команды {@code /lagreport}.
 * <p>
 * Порядок работы: проверить право → убедиться, что скан ещё не идёт → запустить
 * посекторный скан → по завершении собрать метрики, построить отчёт и отправить его тому,
 * кто вызвал команду.
 */
public final class LagReportCommand implements CommandExecutor {

    private final LagLensPlugin plugin;
    private final ChunkScanner chunkScanner;
    private final MetricsCollector metricsCollector;
    private final ReportBuilder reportBuilder;

    public LagReportCommand(LagLensPlugin plugin,
                            ChunkScanner chunkScanner,
                            MetricsCollector metricsCollector,
                            ReportBuilder reportBuilder) {
        this.plugin = plugin;
        this.chunkScanner = chunkScanner;
        this.metricsCollector = metricsCollector;
        this.reportBuilder = reportBuilder;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Право есть и в plugin.yml, но проверяем сами - чтобы отказ был на понятном языке.
        if (!sender.hasPermission("laglens.use")) {
            sender.sendMessage("§cУ вас нет доступа к LagLens (требуется право laglens.use).");
            return true;
        }

        // Пока идёт скан, второй не запускаем - иначе два обхода чанков сложатся в один лаг-спайк.
        if (chunkScanner.isRunning()) {
            sender.sendMessage("§eLagLens уже выполняет скан. Дождитесь его завершения.");
            return true;
        }

        sender.sendMessage("§7LagLens: собираю данные, отчёт придёт через пару секунд...");
        chunkScanner.start(scanResult -> {
            // Колбэк выполняется в главном потоке - сбор метрик здесь безопасен.
            ServerMetrics metrics = metricsCollector.collect();
            deliver(sender, new ServerSnapshot(metrics, scanResult));
        });
        return true;
    }

    private void deliver(CommandSender sender, ServerSnapshot snapshot) {
        // Если команду вызвал игрок и он вышел за время скана - отправлять отчёт некуда.
        if (sender instanceof Player player && !player.isOnline()) {
            plugin.getLogger().info("Игрок " + player.getName()
                    + " вышел до конца скана - отчёт не отправлен.");
            return;
        }

        for (String line : reportBuilder.build(snapshot)) {
            sender.sendMessage(line);
        }
    }
}
