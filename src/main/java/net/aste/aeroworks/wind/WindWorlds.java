package net.aste.aeroworks.wind;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.aste.aeroworks.Aeroworks;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Bounded metadata work on the server thread; immutable maps published to physics threads. */
@EventBusSubscriber(modid = Aeroworks.MODID)
public final class WindWorlds {
    private static final Map<ServerLevel, World> WORLDS = new ConcurrentHashMap<>();
    private static final ThreadLocal<ZonalWindModel.Result> RESULT = ThreadLocal.withInitial(ZonalWindModel.Result::new);
    private static final int MAX_WEATHER_READS_PER_TICK = 16;
    private record BodyWeather(double checked, WindWeatherBlend weather) {}
    private record Snapshot(double time, double phase, double rate, Int2ObjectOpenHashMap<BodyWeather> bodies) {}
    private static final class World {
        final long seed;
        int cursor;
        volatile Snapshot snapshot;
        World(ServerLevel level, WindParameters p) {
            seed = level.getSeed() ^ ZonalWindModel.typeKey(level.dimension().location().toString());
            double time = level.getGameTime() / 20.0;
            snapshot = new Snapshot(time, time / p.fallbackPeriod(), 1 / p.fallbackPeriod(), new Int2ObjectOpenHashMap<>());
        }
    }
    private WindWorlds() {}

    static void sample(WindSample sample, WindParameters p, org.joml.Vector3d out) {
        var world = sample.level() == null ? null : WORLDS.get(sample.level());
        var result = RESULT.get();
        if (world == null) {
            ZonalWindModel.sample(p, 0, sample.timeSeconds() / p.fallbackPeriod(), sample.x(), sample.y(), sample.z(),
                    WindWeatherBlend.CLEAR, sample.timeSeconds(), result);
        } else {
            var snapshot = world.snapshot;
            var body = snapshot.bodies().get(sample.bodyId());
            double phase = snapshot.phase() + Math.max(0, sample.timeSeconds() - snapshot.time()) * snapshot.rate();
            ZonalWindModel.sample(p, world.seed, phase, sample.x(), sample.y(), sample.z(),
                    body == null ? WindWeatherBlend.CLEAR : body.weather(), sample.timeSeconds(), result);
        }
        out.set(result.x, 0, result.z);
    }

    @SubscribeEvent
    public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var config = WindSettings.current();
        if (!config.enabled() || !config.dynamic() || !WindFields.usesDefault()) { WORLDS.remove(level); return; }
        var p = config.field();
        var world = WORLDS.computeIfAbsent(level, value -> new World(value, p));
        var old = world.snapshot;
        double now = level.getGameTime() / 20.0;
        boolean rollback = now < old.time();
        double phase = rollback ? now / p.fallbackPeriod() : old.phase() + (now - old.time()) * old.rate();
        double rate = old.rate();
        var manager = CloudManager.get(level);
        if (rollback || level.getGameTime() % 20 == 0) rate = 1 / SimpleCloudsWind.period(manager, p);
        var container = SubLevelContainer.getContainer(level);
        if (container == null) return;
        var bodies = new Int2ObjectOpenHashMap<BodyWeather>();
        int reads = 0;
        var all = container.getAllSubLevels();
        int size = all.size();
        int start = size == 0 ? 0 : Math.floorMod(world.cursor, size);
        int next = start;
        for (int offset = 0; offset < size; offset++) {
            int index = (start + offset) % size;
            var body = all.get(index);
            if (body.isRemoved() || (body.getPlot().getLiftProviders().isEmpty() && body.getPlot().getContraptions().isEmpty())) continue;
            int id = body.getRuntimeId();
            var previous = rollback ? null : old.bodies().get(id);
            if ((previous == null || now - previous.checked() >= 1) && reads < MAX_WEATHER_READS_PER_TICK) {
                var pos = body.logicalPose().position();
                var weather = SimpleCloudsWind.read(level, manager, pos.x(), pos.z(), world.seed, p);
                var blend = previous == null ? WindWeatherBlend.CLEAR : previous.weather();
                bodies.put(id, new BodyWeather(now, blend.retarget(weather, now, p.transitionSeconds())));
                reads++;
                next = (index + 1) % size;
            } else if (previous != null) bodies.put(id, previous);
        }
        world.cursor = next;
        // Never mutate a published map. Removed bodies disappear at the next tick.
        world.snapshot = new Snapshot(now, phase, rate, bodies);
    }
    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) WORLDS.remove(level);
    }
    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) { WORLDS.clear(); }
}
