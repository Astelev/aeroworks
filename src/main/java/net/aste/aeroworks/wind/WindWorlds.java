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
    private record BodyWeather(double checked, WindWeatherBlend weather, double exposure, double minY, double maxY,
            int sails, MountainFlow[] flows, TurbulenceEpisode episode) {}
    private static final class Flight {
        final java.util.UUID uuid;
        final TurbulenceTracker tracker;
        double x,y,z;
        Flight(java.util.UUID uuid,long seed,double x,double y,double z) {
            this.uuid=uuid; tracker=new TurbulenceTracker(seed^uuid.getMostSignificantBits()^uuid.getLeastSignificantBits());
            this.x=x; this.y=y; this.z=z;
        }
    }
    private record Snapshot(double time, double phase, double rate, Int2ObjectOpenHashMap<BodyWeather> bodies) {}
    private static final class World {
        final long seed;
        int cursor, visualCursor;
        final MountainFlowCatalog terrain=new MountainFlowCatalog();
        final SimpleCloudsWind.Cache clouds=new SimpleCloudsWind.Cache();
        Int2ObjectOpenHashMap<Flight> flights=new Int2ObjectOpenHashMap<>();
        volatile Snapshot snapshot;
        World(ServerLevel level, WindParameters p) {
            seed = level.getSeed() ^ ZonalWindModel.typeKey(level.dimension().location().toString());
            double time = level.getGameTime() / 20.0;
            snapshot = new Snapshot(time, time / p.fallbackPeriod(), 1 / p.fallbackPeriod(), new Int2ObjectOpenHashMap<>());
        }
    }
    private WindWorlds() {}

    /** O(1) read of the same immutable episode snapshot that drives physics, without a weather/noise query. */
    public static boolean isTurbulent(ServerLevel level,int bodyId) {
        var world=WORLDS.get(level);
        if(world==null)return false;
        var body=world.snapshot.bodies().get(bodyId);
        return body!=null && body.episode()!=null && WindSettings.current().effects().turbulenceEnabled();
    }

    static void sample(WindSample sample, WindParameters p, org.joml.Vector3d out, org.joml.Vector3d torque) {
        var world = sample.level() == null ? null : WORLDS.get(sample.level());
        var result = RESULT.get();
        result.y=result.torqueX=result.torqueY=result.torqueZ=0;
        if (world == null) {
            ZonalWindModel.sample(p, 0, sample.timeSeconds() / p.fallbackPeriod(), sample.x(), sample.y(), sample.z(),
                    WindWeatherBlend.CLEAR, sample.timeSeconds(), result);
        } else {
            var snapshot = world.snapshot;
            var body = snapshot.bodies().get(sample.bodyId());
            double elapsed=Math.max(0,sample.timeSeconds()-snapshot.time());
            double phase = snapshot.phase()+elapsed*snapshot.rate();
            ZonalWindModel.sample(p, world.seed, phase, sample.x(), sample.y(), sample.z(),
                    body == null ? WindWeatherBlend.CLEAR : body.weather(), sample.timeSeconds(), result);
            if(body!=null) FlightWindEffects.apply(WindSettings.current().effects(),sample.timeSeconds(),
                    sample.x(),sample.y(),sample.z(),body.flows(),body.episode(),body.sails(),result);
        }
        out.set(result.x, result.y, result.z);
        if(torque!=null) torque.set(result.torqueX,result.torqueY,result.torqueZ);
    }

    @SubscribeEvent
    public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var config = WindSettings.current();
        if (!config.enabled() || !config.dynamic() || !WindFields.usesDefault()) {
            WORLDS.remove(level);
            if(level.getGameTime()%5==0) for(var player:level.players()) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        new net.aste.aeroworks.network.WindVisualPayload(level.dimension().location(),null,java.util.List.of()));
            }
            return;
        }
        var p = config.field();
        var effects=config.effects();
        var world = WORLDS.computeIfAbsent(level, value -> new World(value, p));
        var old = world.snapshot;
        double now = level.getGameTime() / 20.0;
        boolean rollback = now < old.time();
        double phase = rollback ? now / p.fallbackPeriod() : old.phase() + (now - old.time()) * old.rate();
        double rate = old.rate();
        var manager = CloudManager.get(level);
        if (rollback || level.getGameTime() % 20 == 0) {
            // One shared clock keeps co-located aircraft in the same horizontal wind field.
            // Use the strongest SC thunder sampled in an active flight area, not vanilla global rain.
            double storm=0;
            if(!rollback) for(var weather:old.bodies().values()) storm=Math.max(storm,weather.weather().storm(now));
            rate=FlightWindEffects.directionRate(1/SimpleCloudsWind.period(manager,p),storm,effects);
        }
        var container = SubLevelContainer.getContainer(level);
        if (container == null) return;
        var bodies = new Int2ObjectOpenHashMap<BodyWeather>();
        var flights=new Int2ObjectOpenHashMap<Flight>();
        world.terrain.begin(effects,now);
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
            var pos=body.logicalPose().position();
            var flight=rollback ? null : world.flights.get(id);
            var previous=rollback ? null : old.bodies().get(id);
            if(flight==null || !flight.uuid.equals(body.getUniqueId())) {
                flight=new Flight(body.getUniqueId(),world.seed,pos.x(),pos.y(),pos.z()); previous=null;
            }
            double dx=pos.x()-flight.x, dy=pos.y()-flight.y, dz=pos.z()-flight.z;
            double distance=Math.sqrt(dx*dx+dy*dy+dz*dz);
            flight.x=pos.x(); flight.y=pos.y(); flight.z=pos.z();
            if(!Double.isFinite(distance) || distance>256) { flight.tracker.reset(); distance=0; previous=null; }
            flights.put(id,flight);
            if ((previous == null || now - previous.checked() >= 1) && reads < MAX_WEATHER_READS_PER_TICK) {
                int sails=body.getPlot().getLiftProviders().size();
                for(var contraption:body.getPlot().getContraptions()) {
                    if(contraption.sable$isValid()) sails+=contraption.sable$liftProviders().size();
                }
                var reading = SimpleCloudsWind.read(level, manager, pos.x(), pos.y(), pos.z(), world.seed, p,
                        world.clouds,now,effects.turbulenceEnabled() && sails>0);
                var blend = previous == null ? WindWeatherBlend.CLEAR : previous.weather();
                var flows=sails==0 ? MountainFlowCatalog.EMPTY : world.terrain.nearby(level,world.seed,pos.x(),pos.z(),now,effects);
                previous=new BodyWeather(now,blend.retarget(reading.weather(),now,p.transitionSeconds()),
                        reading.exposure(),reading.minY(),reading.maxY(),sails,flows,null);
                reads++;
                next = (index + 1) % size;
            }
            if(previous!=null) {
                double exposure=pos.y()>=previous.minY() && pos.y()<=previous.maxY() && previous.sails()>0
                        ? previous.exposure():0;
                if(previous.sails()==0) flight.tracker.reset();
                else flight.tracker.advance(distance,body.latestLinearVelocity.length(),exposure,previous.weather().storm(now),now,effects);
                bodies.put(id,new BodyWeather(previous.checked(),previous.weather(),previous.exposure(),previous.minY(),
                        previous.maxY(),previous.sails(),previous.flows(),flight.tracker.snapshot()));
            }
        }
        world.cursor = next;
        world.flights=flights;
        // Never mutate a published map. Removed bodies disappear at the next tick.
        world.snapshot = new Snapshot(now, phase, rate, bodies);
        if(level.getGameTime()%5==0) publishVisuals(level,world,now,effects,bodies);
    }
    private static void publishVisuals(ServerLevel level,World world,double now,WindEffectsParameters p,
            Int2ObjectOpenHashMap<BodyWeather> bodies) {
        var players=level.players();
        if(players.isEmpty()) return;
        int start=Math.floorMod(world.visualCursor++,players.size());
        for(int offset=0;offset<players.size();offset++) {
            var player=players.get((start+offset)%players.size());
            var body=dev.ryanhcode.sable.Sable.HELPER.getTrackingOrVehicleSubLevel(player);
            var state=body instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel serverBody && !body.isRemoved()
                    ? bodies.get(serverBody.getRuntimeId()):null;
            net.aste.aeroworks.network.WindVisualPayload.Shake shake=null;
            if(p.turbulenceEnabled() && state!=null && state.sails()>0 && state.episode()!=null) {
                var e=state.episode();
                shake=new net.aste.aeroworks.network.WindVisualPayload.Shake(body.getUniqueId(),e.seed(),
                        Math.max(0,now-e.started()),p.rockingPeriod(),(float)e.envelope(p));
            }
            var pos=dev.ryanhcode.sable.Sable.HELPER.projectOutOfSubLevel(level,player.position());
            var marks=new java.util.ArrayList<net.aste.aeroworks.network.WindVisualPayload.Flow>();
            for(var flow:world.terrain.visible(level,world.seed,pos.x,pos.z,now,p)) {
                if(!flow.rising() || p.verticalSpeed()<=0) continue;
                double x=flow.centerX(p,now), z=flow.centerZ(p,now);
                if((x-pos.x)*(x-pos.x)+(z-pos.z)*(z-pos.z)>384*384) continue;
                double y=flow.groundY()+.55*(flow.top(p,now)-flow.groundY());
                double strength=ZonalWindModel.clamp(flow.velocity(p,now,x,y,z)/p.verticalSpeed());
                if(strength<.02) continue;
                marks.add(new net.aste.aeroworks.network.WindVisualPayload.Flow(x,y,z,(float)(p.diameter()*.3),(float)strength,flow.seed()));
            }
            marks.sort(java.util.Comparator.comparingDouble(f -> (f.x()-pos.x)*(f.x()-pos.x)+(f.z()-pos.z)*(f.z()-pos.z)));
            if(marks.size()>net.aste.aeroworks.network.WindVisualPayload.MAX_FLOWS) {
                marks.subList(net.aste.aeroworks.network.WindVisualPayload.MAX_FLOWS,marks.size()).clear();
            }
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new net.aste.aeroworks.network.WindVisualPayload(level.dimension().location(),shake,marks));
        }
    }
    /** Read-only diagnostics for the nearest aircraft; no terrain/cloud work on command invocation. */
    @SubscribeEvent
    public static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("aeroworks")
                .then(net.minecraft.commands.Commands.literal("wind").executes(context -> {
                    var source=context.getSource();
                    var level=source.getLevel();
                    var container=SubLevelContainer.getContainer(level);
                    dev.ryanhcode.sable.sublevel.ServerSubLevel nearest=null;
                    double best=128*128;
                    if(container!=null) for(var body:container.getAllSubLevels()) {
                        if(body.isRemoved()) continue;
                        var pos=body.logicalPose().position();
                        var origin=source.getPosition();
                        double dx=pos.x()-origin.x,dy=pos.y()-origin.y,dz=pos.z()-origin.z;
                        double distance=dx*dx+dy*dy+dz*dz;
                        if(distance<best) { best=distance; nearest=body; }
                    }
                    if(nearest==null) {
                        source.sendSuccess(()->net.minecraft.network.chat.Component.literal("Нет аппарата в радиусе 128 блоков."),false);
                        return 0;
                    }
                    var pos=nearest.logicalPose().position();
                    var velocity=new org.joml.Vector3d();
                    var torque=new org.joml.Vector3d();
                    WindFields.sampleForStep(new WindSample(level,pos.x(),pos.y(),pos.z(),level.getGameTime()/20.0,
                            nearest.getRuntimeId()),velocity,torque);
                    var world=WORLDS.get(level);
                    var state=world==null ? null : world.snapshot.bodies().get(nearest.getRuntimeId());
                    var episode=state==null ? null : state.episode();
                    String remaining=episode==null ? "нет" : String.format(java.util.Locale.ROOT,"ещё %.0f блоков",episode.length()-episode.progress());
                    String message=String.format(java.util.Locale.ROOT,
                            "Ветер X %.2f / Y %.2f / Z %.2f м/с; парусов %d; горных потоков рядом %d; турбулентность: %s; момент %.2f",
                            velocity.x,velocity.y,velocity.z,state==null?0:state.sails(),state==null?0:state.flows().length,remaining,torque.length());
                    source.sendSuccess(()->net.minecraft.network.chat.Component.literal(message),false);
                    return 1;
                })));
    }
    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) WORLDS.remove(level);
    }
    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) { WORLDS.clear(); }
}
