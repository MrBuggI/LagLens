package io.github.buggy.laglens.metrics;

import io.github.buggy.laglens.model.ServerMetrics;
import io.github.buggy.laglens.model.WorldStats;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Собирает дешёвую часть отчёта: TPS, MSPT и статистику по мирам.
 * <p>
 * Всё чтение идёт по Bukkit API, поэтому метод {@link #collect()} обязан вызываться
 * из главного потока сервера. Ничего не кэширует между вызовами - каждый вызов даёт
 * свежий снимок.
 */
public final class MetricsCollector {

    /**
     * Снимает метрики со всего сервера. Вызывать только из главного потока.
     */
    public ServerMetrics collect() {
        List<WorldStats> worlds = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            worlds.add(collectWorld(world));
        }

        double[] tps = readTps();
        return new ServerMetrics(tps[0], tps[1], tps[2], readMspt(), worlds);
    }

    private WorldStats collectWorld(World world) {
        List<Entity> entities = world.getEntities();

        // EnumMap - быстрый и компактный Map для enum-ключей.
        Map<EntityType, Integer> countByType = new EnumMap<>(EntityType.class);
        for (Entity entity : entities) {
            countByType.merge(entity.getType(), 1, Integer::sum);
        }

        return new WorldStats(
                world.getName(),
                world.getLoadedChunks().length,
                entities.size(),
                countByType
        );
    }

    /**
     * TPS за 1/5/15 минут. Метод есть в Spigot-API, поэтому доступен и на Paper.
     * На всякий случай защищаемся от массива нестандартной длины.
     */
    private double[] readTps() {
        double[] raw = Bukkit.getServer().getTPS();
        double[] result = new double[3];
        for (int i = 0; i < 3; i++) {
            result[i] = i < raw.length ? raw[i] : 0.0;
        }
        return result;
    }

    /**
     * MSPT - среднее время тика в мс. {@code getAverageTickTime()} есть в Spigot-API,
     * так что на Paper/Spigot он всегда на месте. Ловим {@link NoSuchMethodError} лишь
     * ради экзотических форков без этого метода: тогда возвращаем -1 и показываем «н/д»,
     * а не роняем команду.
     */
    private double readMspt() {
        try {
            return Bukkit.getServer().getAverageTickTime();
        } catch (NoSuchMethodError error) {
            return -1.0;
        }
    }
}
