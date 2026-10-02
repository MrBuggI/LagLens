package io.github.mrbuggi.laglens;

import io.github.mrbuggi.laglens.command.LagReportCommand;
import io.github.mrbuggi.laglens.config.LagLensConfig;
import io.github.mrbuggi.laglens.metrics.MetricsCollector;
import io.github.mrbuggi.laglens.report.HistoryReportBuilder;
import io.github.mrbuggi.laglens.report.ReportBuilder;
import io.github.mrbuggi.laglens.scan.ChunkScanner;
import io.github.mrbuggi.laglens.storage.HistoryService;
import io.github.mrbuggi.laglens.storage.ReportHistory;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.logging.Level;

/**
 * Точка входа плагина. Сервер находит этот класс по полю {@code main} в plugin.yml.
 * <p>
 * Задача {@code onEnable}: загрузить конфиг, собрать зависимости (сканер, сборщик метрик,
 * построитель отчёта, история) и повесить обработчик на команду {@code /lagreport}.
 */
public final class LagLensPlugin extends JavaPlugin {

    private static final String HISTORY_FILE = "history.db";

    private LagLensConfig lagLensConfig;
    private ChunkScanner chunkScanner;
    private HistoryService historyService;

    @Override
    public void onEnable() {
        // Копирует config.yml из JAR в папку плагина при первом запуске. Существующий файл не трогает.
        saveDefaultConfig();
        this.lagLensConfig = LagLensConfig.load(this);
        this.chunkScanner = new ChunkScanner(this, lagLensConfig);

        PluginCommand command = getCommand("lagreport");
        if (command == null) {
            // Сюда попадаем, только если команда не описана в plugin.yml.
            getLogger().severe("Команда /lagreport не найдена в plugin.yml. Плагин отключается.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.historyService = openHistory();
        command.setExecutor(new LagReportCommand(
                this,
                lagLensConfig,
                chunkScanner,
                new MetricsCollector(),
                new ReportBuilder(lagLensConfig),
                new HistoryReportBuilder(),
                historyService));

        getLogger().info("LagLens включён. Порция скана: "
                + lagLensConfig.getChunksPerTick() + " чанков/тик.");
    }

    /**
     * Открывает базу истории. Без неё плагин остаётся рабочим: отчёты строятся,
     * просто не сохраняются, поэтому ошибка здесь - предупреждение, а не отключение.
     */
    private HistoryService openHistory() {
        if (!lagLensConfig.isHistoryEnabled()) {
            return null;
        }
        try {
            ReportHistory history = ReportHistory.open(getDataFolder().toPath().resolve(HISTORY_FILE));
            return new HistoryService(this, history, lagLensConfig.getHistoryKeep());
        } catch (SQLException e) {
            getLogger().log(Level.WARNING, "Не удалось открыть " + HISTORY_FILE
                    + ". История отчётов выключена до перезапуска.", e);
            return null;
        }
    }

    @Override
    public void onDisable() {
        // Останавливаем скан, если он не успел завершиться, - чтобы задача не тикала после выключения.
        if (chunkScanner != null) {
            chunkScanner.cancel();
        }
        if (historyService != null) {
            historyService.shutdown();
        }
        getLogger().info("LagLens выключен.");
    }
}
