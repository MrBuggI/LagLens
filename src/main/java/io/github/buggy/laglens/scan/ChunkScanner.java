package io.github.buggy.laglens.scan;

import io.github.buggy.laglens.LagLensPlugin;
import io.github.buggy.laglens.config.LagLensConfig;
import io.github.buggy.laglens.model.ChunkHotspot;
import io.github.buggy.laglens.model.ScanResult;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Посекторный сканер загруженных чанков - ядро плагина.
 * <p>
 * Обход {@code getTileEntities()} по всем чанкам сразу сам вызвал бы лаг-спайк, поэтому
 * работа разбита на порции по {@code chunks-per-tick} и размазана по тикам через планировщик.
 * За раз выполняется только один скан; повторный запуск игнорируется, пока текущий не завершится.
 */
public final class ChunkScanner {

    private final LagLensPlugin plugin;
    private final LagLensConfig config;

    /** Задача текущего скана. {@code null}, когда скан не идёт. */
    private BukkitTask task;

    public ChunkScanner(LagLensPlugin plugin, LagLensConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /** Идёт ли скан прямо сейчас. */
    public boolean isRunning() {
        return task != null;
    }

    /**
     * Запускает скан. Вызывать из главного потока.
     *
     * @param onComplete колбэк с результатом; будет вызван в главном потоке по завершении
     * @return {@code false}, если скан уже идёт (второй не запускается)
     */
    public boolean start(Consumer<ScanResult> onComplete) {
        if (isRunning()) {
            return false;
        }

        // Снимок загруженных чанков берём один раз. Новые чанки, загруженные во время скана,
        // в этот проход не попадут - это осознанный компромисс ради предсказуемости.
        List<Chunk> chunks = collectLoadedChunks();
        ScanTask scanTask = new ScanTask(chunks, onComplete);

        // Период 1 тик: каждый тик обрабатываем очередную порцию чанков.
        this.task = scanTask.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    /** Останавливает текущий скан без вызова колбэка. Используется при выключении плагина. */
    public void cancel() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private List<Chunk> collectLoadedChunks() {
        List<Chunk> chunks = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            Collections.addAll(chunks, world.getLoadedChunks());
        }
        return chunks;
    }

    /**
     * Задача одного скана. Хранит своё состояние (позицию в очереди и накопленные данные),
     * поэтому на каждый скан создаётся новый экземпляр.
     */
    private final class ScanTask extends BukkitRunnable {

        private final List<Chunk> chunks;
        private final Consumer<ScanResult> onComplete;
        private final List<ChunkHotspot> candidates = new ArrayList<>();

        private int index = 0;
        private int totalTileEntities = 0;

        private ScanTask(List<Chunk> chunks, Consumer<ScanResult> onComplete) {
            this.chunks = chunks;
            this.onComplete = onComplete;
        }

        @Override
        public void run() {
            int processed = 0;
            int perTick = config.getChunksPerTick();

            while (index < chunks.size() && processed < perTick) {
                Chunk chunk = chunks.get(index++);
                processed++;

                // Чанк мог выгрузиться за время скана - тогда пропускаем его,
                // но НЕ обращаемся к нему, чтобы случайно не загрузить обратно.
                if (chunk.isLoaded()) {
                    scanChunk(chunk);
                }
            }

            if (index >= chunks.size()) {
                finish();
            }
        }

        private void scanChunk(Chunk chunk) {
            // false - не создаём снапшоты блоков: нам нужны только типы, а снапшоты дороги.
            BlockState[] tileEntities = chunk.getTileEntities(false);

            int hoppers = 0;
            for (BlockState tileEntity : tileEntities) {
                if (tileEntity instanceof Hopper) {
                    hoppers++;
                }
            }

            totalTileEntities += tileEntities.length;

            boolean worthReporting = tileEntities.length >= config.getChunkTileEntitiesThreshold()
                    || hoppers >= config.getChunkHoppersThreshold();
            if (worthReporting) {
                candidates.add(new ChunkHotspot(
                        chunk.getWorld().getName(),
                        chunk.getX(),
                        chunk.getZ(),
                        tileEntities.length,
                        hoppers));
            }
        }

        private void finish() {
            cancel();                     // останавливаем повтор задачи (метод BukkitRunnable)
            ChunkScanner.this.task = null;

            candidates.sort(Comparator.comparingInt(ChunkHotspot::tileEntities).reversed());
            List<ChunkHotspot> topChunks = candidates.stream()
                    .limit(config.getTopChunks())
                    .toList();

            onComplete.accept(new ScanResult(totalTileEntities, topChunks));
        }
    }
}
