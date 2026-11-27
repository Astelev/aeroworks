package net.aste.aeroworks.compat.atmospheric;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.aste.aeroworks.Aeroworks;
import net.aste.aeroworks.wind.WindFields;
import net.aste.aeroworks.wind.WindSample;
import net.aste.aeroworks.wind.WindSettings;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.joml.Vector3d;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid=Aeroworks.MODID)
public final class AtmosphericWind {
    private AtmosphericWind() {}
    private record Reading(UUID id, long tick, double x, double y, double z) {}
    private static final class World {
        volatile Map<Integer,Reading> readings=Map.of();
        int cursor;
    }
    private static final Map<ServerLevel,World> WORLDS=new ConcurrentHashMap<>();
    public static boolean present() { return ModList.get()!=null && ModList.get().isLoaded("aeronauticswinds"); }

    /** Called from physics threads: no weather simulation, reflection or terrain access. */
    public static void sample(WindSample sample, Vector3d out) {
        out.zero();
        if(sample.level()==null)return;
        var world=WORLDS.get(sample.level());
        var reading=world==null?null:world.readings.get(sample.bodyId());
        if(reading!=null && sample.timeSeconds()*20-reading.tick()<=40
                && sample.timeSeconds()*20>=reading.tick()-1) {
            out.set(reading.x(),reading.y(),reading.z());
        } else if(sample.bodyId()<0 && sample.level()!=null && sample.level().getServer().isSameThread()) {
            // Ambient tools on the server thread can query their exact position.
            read(sample.level(),sample.x(),sample.y(),sample.z(),out);
        }
    }

    /** Optional API is resolved only after presence has been checked. */
    public static void read(Level level,double x,double y,double z,Vector3d out) {
        out.zero();
        if(!present() || level==null)return;
        Native.read(level,x,y,z,out);
        if(!Double.isFinite(out.x)||!Double.isFinite(out.y)||!Double.isFinite(out.z))out.zero();
    }

    private static final class Native {
        static void read(Level level,double x,double y,double z,Vector3d out) {
            var simulation=com.enzonic.aeronauticswinds.WindsSim.forLevel(level);
            if(simulation!=null)out.set(simulation.sample(level,x,y,z).wind());
        }
    }

    /** Only lift-provider aircraft use AeroWorks' flight model; ships keep Atmospheric's own rig forces. */
    public static boolean managesAircraft(ServerSubLevel body) {
        if(body==null || body.isRemoved() || !WindFields.usesDefault() || !WindSettings.current().enabled())return false;
        if(!body.getPlot().getLiftProviders().isEmpty())return true;
        for(var c:body.getPlot().getContraptions())
            if(c.sable$isValid()&&!c.sable$liftProviders().isEmpty())return true;
        return false;
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void tick(LevelTickEvent.Post event) {
        if(!present() || !(event.getLevel() instanceof ServerLevel level))return;
        if(!WindSettings.current().enabled()||!WindFields.usesDefault()){WORLDS.remove(level);return;}
        var container=SubLevelContainer.getContainer(level);
        if(container==null)return;
        var world=WORLDS.computeIfAbsent(level,k->new World());
        var all=container.getAllSubLevels();
        var next=new HashMap<Integer,Reading>();
        int size=all.size(),start=size==0?0:Math.floorMod(world.cursor,size),reads=0;
        long tick=level.getGameTime();
        var out=new Vector3d();
        for(int offset=0;offset<size;offset++) {
            int index=(start+offset)%size;
            var body=all.get(index);
            if(body.isRemoved())continue;
            var old=world.readings.get(body.getRuntimeId());
            if(old!=null&&(!old.id().equals(body.getUniqueId())||old.tick()>tick))old=null;
            if((old==null||tick-old.tick()>=5)&&reads<16) {
                var pos=body.logicalPose().position();
                read(level,pos.x(),pos.y(),pos.z(),out);
                old=new Reading(body.getUniqueId(),tick,out.x,out.y,out.z);
                reads++;world.cursor=index+1;
            }
            if(old!=null)next.put(body.getRuntimeId(),old);
        }
        world.readings=Map.copyOf(next);
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if(event.getLevel() instanceof ServerLevel level)WORLDS.remove(level);
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { WORLDS.clear(); }
}
