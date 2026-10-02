package io.github.mrbuggi.laglens;

import io.github.mrbuggi.laglens.command.LagReportCommand;
import io.github.mrbuggi.laglens.config.LagLensConfig;
import io.github.mrbuggi.laglens.metrics.MetricsCollector;
import io.github.mrbuggi.laglens.report.ReportBuilder;
import io.github.mrbuggi.laglens.scan.ChunkScanner;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Точка входа плагина. Сервер находит этот класс по полю {@code main} в plugin.yml.
 * <p>
 * Задача {@code onEnable}: загрузить конфиг, собрать зависимости (сканер, сборщик метрик,
 * построитель отчёта) и повесить обработчик на команду {@code /lagreport}.
 */
public final class LagLensPlugin extends JavaPlugin {

    private LagLensConfig lagLensConfig;
    private ChunkScanner chunkScanner;

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
        command.setExecutor(new LagReportCommand(
                this,
                chunkScanner,
                new MetricsCollector(),
                new ReportBuilder(lagLensConfig)));

        getLogger().info("LagLens включён. Порция скана: "
                + lagLensConfig.getChunksPerTick() + " чанков/тик.");
    }

    @Override
    public void onDisable() {
        // Останавливаем скан, если он не успел завершиться, - чтобы задача не тикала после выключения.
        if (chunkScanner != null) {
            chunkScanner.cancel();
        }
        getLogger().info("LagLens выключен.");
    }
}
