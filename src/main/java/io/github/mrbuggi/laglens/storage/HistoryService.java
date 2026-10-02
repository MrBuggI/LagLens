package io.github.mrbuggi.laglens.storage;

import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Связывает {@link ReportHistory} с сервером: запросы к базе уходят на отдельный поток,
 * результат возвращается в главный.
 * <p>
 * Диск может ответить за миллисекунду, а может за секунду. Ждать его на главном потоке
 * значит самим создать лаг, который плагин должен находить. Поток один, поэтому запросы
 * выполняются по очереди, в порядке отправки.
 */
public final class HistoryService {

    /** Итог чтения истории: последние отчёты и чанки, которые в них повторяются. */
    public record View(List<HistoryEntry> entries, List<RepeatOffender> offenders) {
    }

    private static final int OFFENDERS_SHOWN = 3;

    private final Plugin plugin;
    private final ReportHistory history;
    private final int keep;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "LagLens-History");
        thread.setDaemon(true);
        return thread;
    });

    public HistoryService(Plugin plugin, ReportHistory history, int keep) {
        this.plugin = plugin;
        this.history = history;
        this.keep = keep;
    }

    /** Сохраняет отчёт в фоне. Ошибка записи попадает в лог и не мешает самому отчёту. */
    public void save(HistoryEntry entry) {
        executor.execute(() -> {
            try {
                history.save(entry, keep);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Не удалось сохранить отчёт в историю", e);
            }
        });
    }

    /**
     * Читает последние отчёты в фоне и передаёт их в {@code onResult} на главном потоке.
     * При ошибке чтения в {@code onResult} приходит {@code null}.
     */
    public void load(int limit, Consumer<View> onResult) {
        executor.execute(() -> {
            View view;
            try {
                view = new View(history.latest(limit), history.repeatOffenders(OFFENDERS_SHOWN));
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Не удалось прочитать историю отчётов", e);
                view = null;
            }
            View result = view;
            // Плагин мог выключиться, пока шёл запрос: тогда планировщик задачу уже не примет.
            if (plugin.isEnabled()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> onResult.accept(result));
            }
        });
    }

    /** Дожидается записей, стоящих в очереди, и закрывает базу. Вызывается при выключении плагина. */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("История отчётов не успела дописаться за 5 секунд.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            history.close();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Не удалось закрыть базу истории", e);
        }
    }
}
