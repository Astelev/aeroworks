package net.aste.aeroworks.waves;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.aste.aeroworks.wind.*;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;

/** Main-thread, bounded metadata sampling; physics only reads immutable grids. */
public final class WaveWindStates {
    private static final Map<ServerLevel,WaveWindGrid> STATES=new ConcurrentHashMap<>();
    private WaveWindStates(){}
    public static WaveWindGrid current(ServerLevel level){return STATES.getOrDefault(level,WaveWindGrid.EMPTY);}
    public static double stormSpeed(WindParameters p,double y){
        double f=WaveMath.smooth((y-p.surfaceHeight())/(p.maximumHeight()-p.surfaceHeight()));
        return p.surfaceStorm()+(p.aloftStorm()-p.surfaceStorm())*f;
    }
    public static void update(ServerLevel level){
        if(level.getGameTime()%20!=0&&STATES.containsKey(level))return;
        if(!WavePhysicsConfig.current().enabled()||!WavePhysicsConfig.current().detail().enabled()) {STATES.remove(level);return;}
        var wanted=new LinkedHashSet<Long>();
        for(var player:level.players())add(wanted,player.getX(),player.getZ());
        var bodies=SubLevelContainer.getContainer(level);
        if(bodies!=null)for(var body:bodies.getAllSubLevels()){
            if(wanted.size()>=WaveWindGrid.MAX_CELLS)break;
            var b=body.boundingBox();add(wanted,(b.minX()+b.maxX())*.5,(b.minZ()+b.maxZ())*.5);
        }
        var old=current(level);var cells=new HashMap<Long,WaveWindGrid.Cell>();
        var settings=WindSettings.current();var p=settings.field();var velocity=new Vector3d();double now=level.getGameTime()/20.0;
        for(long key:wanted){
            int x=(int)(key>>32),z=(int)key;
            // Same height interpolation as the wind model; all queries read its prepared weather cache.
            var chunk=level.getChunkSource().getChunkNow((x*64)>>4,(z*64)>>4);
            double y=chunk==null?p.surfaceHeight():chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,0,0)+1;
            WindFields.sample(new WindSample(level,x*64.0,y,z*64.0,now),velocity);
            double reference=stormSpeed(p,y);
            float target=reference<=1e-9?0:(float)Math.clamp(Math.hypot(velocity.x,velocity.z)/reference,0,1.5);
            var previous=old.cells().get(key);float from=previous==null?0:(float)previous.at(now);
            cells.put(key,new WaveWindGrid.Cell(x,z,from,target,now));
        }
        STATES.put(level,new WaveWindGrid((int)(level.getSeed()^(level.getSeed()>>>32)),cells));
    }
    private static void add(Set<Long> wanted,double x,double z){
        int cx=(int)Math.floor(x/64),cz=(int)Math.floor(z/64);
        for(int radius=0;radius<=WaveWindGrid.CLIENT_RADIUS;radius++)for(int dz=-radius;dz<=radius;dz++)for(int dx=-radius;dx<=radius;dx++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            if(wanted.size()>=WaveWindGrid.MAX_CELLS)return;
            wanted.add(WaveWindGrid.key(cx+dx,cz+dz));
        }
    }
    public static void unload(ServerLevel level){STATES.remove(level);}
    public static void clear(){STATES.clear();}
}
