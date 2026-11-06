package net.aste.aeroworks.waves;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.aste.aeroworks.Aeroworks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid=Aeroworks.MODID)
public final class ServerWaves {
    public static final int SAMPLE_BUDGET=256,MAX_BODIES=128,MAX_COLUMNS=32768;
    private static final Map<ServerLevel,World> WORLDS=new ConcurrentHashMap<>();
    private static final ThreadLocal<WaveBuoyancyStep> STEP=ThreadLocal.withInitial(WaveBuoyancyStep::new);
    private static final ThreadLocal<Entry> CURRENT=new ThreadLocal<>();
    private record Column(long tick,float height){}
    private static final class Entry {
        ServerSubLevel body;long seen,lastBuilt;volatile long ready=-1;
        int centerX,centerZ,size;double fade;boolean queued;
        long applies,nextObserve;volatile WaveBuoyancyStep.Observation observation;
        volatile WaveShorePatch patch;
        Entry(ServerSubLevel body,long tick){this.body=body;seen=tick;}
    }
    private static final class Job {
        final Entry entry;final int x,z,size,cx,cz;final double fade;
        final float[] heights;int cursor;
        Job(Entry e,int cx,int cz,int size,double fade){
            entry=e;this.cx=cx;this.cz=cz;this.size=size;this.fade=fade;
            x=cx-(size-1)*2;z=cz-(size-1)*2;heights=new float[size*size];
        }
    }
    private static final class World {
        long scans,applies;
        final Map<UUID,Entry> bodies=new ConcurrentHashMap<>();
        final ArrayDeque<Job> jobs=new ArrayDeque<>();
        final LinkedHashMap<Long,Column> columns=new LinkedHashMap<>(){
            @Override protected boolean removeEldestEntry(Map.Entry<Long,Column> e){return size()>MAX_COLUMNS;}
        };
        final BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
    }
    private ServerWaves(){}

    /** Called from Deep Seas' existing main-thread hull scan; does not inspect any extra hull blocks. */
    public static void track(ServerLevel level,SubLevel body,boolean hasHull){
        WORLDS.computeIfAbsent(level,k->new World()).scans++;
        register(level,body,hasHull);
    }
    private static void register(ServerLevel level,SubLevel body,boolean hasHull){
        var state=WORLDS.computeIfAbsent(level,k->new World());
        if(!hasHull||!(body instanceof ServerSubLevel server)||body.isRemoved()){
            state.bodies.remove(body.getUniqueId());return;
        }
        var entry=state.bodies.get(body.getUniqueId());
        if(entry==null&&state.bodies.size()<MAX_BODIES){entry=new Entry(server,level.getGameTime());state.bodies.put(body.getUniqueId(),entry);}
        if(entry!=null){entry.body=server;entry.seen=level.getGameTime();}
    }
    public static void begin(ServerSubLevel body){
        var level=body.getLevel();var world=WORLDS.get(level);
        var entry=world==null?null:world.bodies.get(body.getUniqueId());
        // Register from the actual force path too, rather than relying exclusively on a scan callback.
        if(entry==null){register(level,body,true);world=WORLDS.get(level);entry=world.bodies.get(body.getUniqueId());}
        world.applies++;
        var physics=SubLevelPhysicsSystem.getCurrentlySteppingSystem();
        double partial=physics!=null&&physics.getLevel()==level?physics.getPartialPhysicsTick():0;
        double ticks=level.getGameTime()+partial;
        double gain=entry==null||entry.ready<0?0:WaveMath.smooth((ticks-entry.ready)/20.0);
        STEP.get().begin(WavePhysicsConfig.current(),entry==null?null:entry.patch,ticks/20.0,gain);
        CURRENT.set(entry);
        if(entry!=null){
            entry.applies++;
            if(level.getGameTime()>=entry.nextObserve){entry.nextObserve=level.getGameTime()+20;STEP.get().observe(true);}
        }
    }
    public static WaveBuoyancyStep step(){return STEP.get();}
    public static void end(){
        var step=STEP.get();var entry=CURRENT.get();
        if(entry!=null&&step.observing())entry.observation=step.observation();
        step.end();CURRENT.remove();
    }

    public static String diagnostics(ServerLevel level,double x,double y,double z){
        var p=WavePhysicsConfig.current();
        if(!p.enabled()||!p.buoyancy()||p.amplitude()==0)return "disabled in waves-common (enabled/buoyancy/amplitude)";
        var world=WORLDS.get(level);
        if(world==null)return "native hull scan/force hook not reached";
        Entry nearest=null;double best=256*256;
        for(var e:world.bodies.values()){
            var b=e.body.boundingBox();double dx=(b.minX()+b.maxX())*.5-x,dy=(b.minY()+b.maxY())*.5-y,dz=(b.minZ()+b.maxZ())*.5-z;
            double d=dx*dx+dy*dy+dz*dz;if(d<best){best=d;nearest=e;}
        }
        if(nearest==null)return "no native buoyant hull nearby; scans="+world.scans+", applies="+world.applies;
        var o=nearest.observation;
        String field=nearest.patch==null?"warming":"ready";
        if(o==null)return "field="+field+", native applies="+nearest.applies+", awaiting cell sample";
        return String.format(java.util.Locale.ROOT,"field=%s applies=%d cells=%d wet-wave=%d changed=%d max-dY=%.3f",
                field,nearest.applies,o.samples(),o.weighted(),o.changed(),o.maximumOffset());
    }
    @SubscribeEvent public static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent event){
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("aerowaves").executes(context->{
            var source=context.getSource();var pos=source.getPosition();
            String message=WaveNetwork.REVISION+"; "+diagnostics(source.getLevel(),pos.x,pos.y,pos.z)+"; "+WavePhysicsConfig.current();
            source.sendSuccess(()->net.minecraft.network.chat.Component.literal(message),false);return 1;
        }));
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void tick(ServerTickEvent.Post event){
        var p=WavePhysicsConfig.current();
        for(var level:event.getServer().getAllLevels()){
            long tick=level.getGameTime();
            if(tick%20==0)for(var player:level.players())
                PacketDistributor.sendToPlayer(player,new WaveStatePayload(level.dimension().location(),p,WaveNetwork.REVISION,
                        diagnostics(level,player.getX(),player.getY(),player.getZ())));
            var state=WORLDS.get(level);if(state==null)continue;
            state.bodies.entrySet().removeIf(e->e.getValue().body.isRemoved()||tick-e.getValue().seen>100);
            if(!p.enabled()||!p.buoyancy()||p.amplitude()==0){state.jobs.clear();for(var e:state.bodies.values())e.queued=false;continue;}
            for(var e:state.bodies.values()){
                if(e.queued)continue;
                var bounds=e.body.boundingBox();
                int cx=(int)Math.floor((bounds.minX()+bounds.maxX())/32)*16;
                int cz=(int)Math.floor((bounds.minZ()+bounds.maxZ())/32)*16;
                double span=Math.max(bounds.maxX()-bounds.minX(),bounds.maxZ()-bounds.minZ());
                int size=Math.clamp((int)Math.ceil((span+2*(p.shoreFade()+32))/8)*2+1,33,129);
                if(e.patch!=null&&tick-e.lastBuilt<100&&cx==e.centerX&&cz==e.centerZ&&size==e.size&&p.shoreFade()==e.fade)continue;
                e.queued=true;state.jobs.addLast(new Job(e,cx,cz,size,p.shoreFade()));
            }
            int queries=0,work=0;
            while(queries<SAMPLE_BUDGET&&work<4096&&!state.jobs.isEmpty()){
                var job=state.jobs.removeFirst();var e=job.entry;
                if(state.bodies.get(e.body.getUniqueId())!=e){e.queued=false;continue;}
                for(int quantum=0;quantum<32&&job.cursor<job.heights.length&&queries<SAMPLE_BUDGET&&work<4096;quantum++,work++){
                    int x=job.x+job.cursor%job.size*4,z=job.z+job.cursor/job.size*4;
                    long key=((long)x<<32)^(z&0xffffffffL);var column=state.columns.get(key);
                    if(column==null||tick-column.tick>40){
                        column=new Column(tick,WaveTerrain.cell(level,x,z,state.pos));state.columns.put(key,column);queries++;
                    }
                    job.heights[job.cursor++]=column.height;
                }
                if(job.cursor==job.heights.length){
                    var patch=new WaveShorePatch(job.x,job.z,job.size,job.heights,job.fade);
                    if(e.ready<0)e.ready=tick;
                    e.patch=patch;e.centerX=job.cx;e.centerZ=job.cz;e.size=job.size;e.fade=job.fade;e.lastBuilt=tick;e.queued=false;
                }else state.jobs.addLast(job);
            }
        }
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload e){if(e.getLevel() instanceof ServerLevel s)WORLDS.remove(s);}
    @SubscribeEvent public static void stop(ServerStoppedEvent e){WORLDS.clear();STEP.remove();CURRENT.remove();}
}
