package net.aste.aeroworks.aerodynamics;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import net.aste.aeroworks.Aeroworks;
import net.aste.aeroworks.Config;
import net.aste.aeroworks.wind.WindParticles;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Bounded handoff from physics workers to the server thread; never scan blocks or chunks. */
@EventBusSubscriber(modid=Aeroworks.MODID)
public final class StallVisuals {
    private record Sample(ServerLevel level,long tick,double x,double y,double z,double vx,double vy,double vz) {}
    private static final ConcurrentLinkedQueue<Sample> QUEUE=new ConcurrentLinkedQueue<>();
    private static final AtomicInteger SIZE=new AtomicInteger();
    private StallVisuals() {}
    public static boolean offer(ServerLevel level,double x,double y,double z,double vx,double vy,double vz) {
        if(!Double.isFinite(x+y+z+vx+vy+vz))return false;
        if(SIZE.incrementAndGet()>96) {SIZE.decrementAndGet();return false;}
        QUEUE.offer(new Sample(level,level.getGameTime(),x,y,z,vx,vy,vz));return true;
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        for(int i=0;i<16;i++) {
            Sample s=QUEUE.poll();if(s==null)break;SIZE.decrementAndGet();
            if(!Config.STALL_PARTICLES.get() || s.level.getGameTime()-s.tick>10)continue;
            // count=0 encodes one particle with the supplied velocity. Vanilla honors viewing distance/settings.
            s.level.sendParticles(WindParticles.STALL.get(),s.x,s.y,s.z,0,s.vx,s.vy,s.vz,1);
        }
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) {QUEUE.clear();SIZE.set(0);}
}
