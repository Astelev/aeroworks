package net.aste.aeroworks.waves;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
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
    private static final ThreadLocal<BlockWaveStep> BLOCK_STEP=ThreadLocal.withInitial(BlockWaveStep::new);
    private static final ThreadLocal<Entry> CURRENT=new ThreadLocal<>();
    private record Column(long tick,float height,float depth){}
    private static final class Entry {
        ServerSubLevel body;long seen,lastBuilt;volatile long ready=-1;
        int centerX,centerZ,size;double fade;boolean queued;
        long applies,nextObserve;volatile WaveBuoyancyStep.Observation observation;
        volatile WaveShorePatch patch;
        volatile BlockWaveSamples blocks;
        volatile long geometryVersion;
        long builtVersion=-1;
        BlockWaveSamples.Builder building;
        long buildingVersion;
        volatile int blockSamples,blockChanges;
        Entry(ServerSubLevel body,long tick){this.body=body;seen=tick;}
    }
    private static final class Job {
        final Entry entry;final int x,z,size,cx,cz;final double fade;
        final float[] heights,depths;int cursor;
        Job(Entry e,int cx,int cz,int size,double fade){
            entry=e;this.cx=cx;this.cz=cz;this.size=size;this.fade=fade;
            x=cx-(size-1)*2;z=cz-(size-1)*2;heights=new float[size*size];depths=new float[size*size];
        }
    }
    private static final class World {
        long scans,applies;int blockCursor;
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
        register(level,body,true); // Native solid-block buoyancy also exists without a sealed hull.
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
        STEP.get().begin(WavePhysicsConfig.current(),entry==null?null:entry.patch,ticks/20.0,gain,WaveWindStates.current(level));
        CURRENT.set(entry);
        if(entry!=null){
            entry.body=body;entry.seen=level.getGameTime();entry.applies++;
            if(level.getGameTime()>=entry.nextObserve){entry.nextObserve=level.getGameTime()+20;STEP.get().observe(true);}
        }
    }
    public static WaveBuoyancyStep step(){return STEP.get();}
    public static void end(){
        var step=STEP.get();var entry=CURRENT.get();
        if(entry!=null&&step.observing())entry.observation=step.observation();
        step.end();CURRENT.remove();
    }

    /** Plot edits invalidate only cached geometry, never the water field. */
    public static void changed(SubLevel body){
        if(!(body instanceof ServerSubLevel server))return;
        var world=WORLDS.get(server.getLevel());if(world==null)return;
        var entry=world.bodies.get(body.getUniqueId());
        if(entry!=null){entry.geometryVersion++;entry.blocks=null;}
    }
    public static void solidStep(ServerSubLevel body,RigidBodyHandle handle,double dt){
        var p=WavePhysicsConfig.current();
        if(!p.enabled()||!p.buoyancy()||p.amplitude()==0)return;
        var world=WORLDS.get(body.getLevel());if(world==null)return;
        var e=world.bodies.get(body.getUniqueId());if(e==null||e.blocks==null||e.patch==null)return;
        var center=body.getMassTracker().getCenterOfMass();if(center==null)return;
        var physics=SubLevelPhysicsSystem.getCurrentlySteppingSystem();
        double ticks=body.getLevel().getGameTime()+(physics==null?0:physics.getPartialPhysicsTick());
        double gain=e.ready<0?0:WaveMath.smooth((ticks-e.ready)/20.0);
        var step=BLOCK_STEP.get();
        step.calculate(e.blocks,e.patch,p,body.logicalPose(),center,ticks/20.0,gain,dt,WaveWindStates.current(body.getLevel()));
        e.blockSamples=step.samples;e.blockChanges=step.changed;
        if(step.impulse.lengthSquared()+step.torque.lengthSquared()>1e-18)
            handle.applyLinearAndAngularImpulse(step.impulse,step.torque,true);
    }
    private static void geometry(World world){
        var entries=new ArrayList<>(world.bodies.values());
        int budget=2048,n=entries.size();if(n==0)return;
        int start=Math.floorMod(world.blockCursor,n);
        for(int turn=0;turn<n&&budget>0;turn++){
            int index=(start+turn)%n;var e=entries.get(index);world.blockCursor=index+1;
            if(e.blocks!=null&&e.builtVersion==e.geometryVersion)continue;
            if(e.building==null||e.buildingVersion!=e.geometryVersion||!e.building.sameBounds()){
                e.building=new BlockWaveSamples.Builder(e.body);e.buildingVersion=e.geometryVersion;
            }
            int quantum=Math.min(256,budget);
            while(quantum-->0&&!e.building.done()){e.building.advance();budget--;}
            if(e.building.done()){
                e.blocks=e.building.finish();e.builtVersion=e.buildingVersion;e.building=null;
            }
        }
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
        String solids=" solid="+(nearest.blocks==null?"warming":nearest.blocks.cells().length/4)+"/"+nearest.blockSamples+"/"+nearest.blockChanges;
        String field=nearest.patch==null?"warming":"ready";
        if(o==null)return "field="+field+", native applies="+nearest.applies+", awaiting cell sample"+solids;
        return String.format(java.util.Locale.ROOT,"field=%s applies=%d cells=%d wet-wave=%d changed=%d max-dY=%.3f",
                field,nearest.applies,o.samples(),o.weighted(),o.changed(),o.maximumOffset())+solids;
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
            WaveWindStates.update(level);
            if(tick%20==0)for(var player:level.players())
                PacketDistributor.sendToPlayer(player,new WaveStatePayload(level.dimension().location(),p,WaveNetwork.REVISION,
                        diagnostics(level,player.getX(),player.getY(),player.getZ()),WaveWindStates.current(level).around(player.getX(),player.getZ())));
            if(p.enabled()&&p.buoyancy()&&p.amplitude()>0){
                var container=SubLevelContainer.getContainer(level);
                if(container!=null)for(var body:container.getAllSubLevels())register(level,body,true);
            }
            var state=WORLDS.get(level);if(state==null)continue;
            state.bodies.entrySet().removeIf(e->e.getValue().body.isRemoved()||tick-e.getValue().seen>100);
            if(!p.enabled()||!p.buoyancy()||p.amplitude()==0){state.jobs.clear();for(var e:state.bodies.values())e.queued=false;continue;}
            geometry(state);
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
                        float h=WaveTerrain.cell(level,x,z,state.pos);
                        column=new Column(tick,h,WaveTerrain.depth(level,x,z,h));state.columns.put(key,column);queries++;
                    }
                    job.heights[job.cursor]=column.height;job.depths[job.cursor++]=column.depth;
                }
                if(job.cursor==job.heights.length){
                    var patch=new WaveShorePatch(job.x,job.z,job.size,job.heights,job.depths,job.fade);
                    if(e.ready<0)e.ready=tick;
                    e.patch=patch;e.centerX=job.cx;e.centerZ=job.cz;e.size=job.size;e.fade=job.fade;e.lastBuilt=tick;e.queued=false;
                }else state.jobs.addLast(job);
            }
        }
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload e){if(e.getLevel() instanceof ServerLevel s){WORLDS.remove(s);WaveWindStates.unload(s);}}
    @SubscribeEvent public static void stop(ServerStoppedEvent e){WORLDS.clear();WaveWindStates.clear();STEP.remove();BLOCK_STEP.remove();CURRENT.remove();}
}
