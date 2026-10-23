package net.aste.aeroworks.wind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;

/** Server-thread terrain cache. Only reads chunks already present; never issues a chunk request. */
final class MountainFlowCatalog {
    private record Cell(int x, int z) {}
    private record Entry(double checked, MountainFlow flow) {}
    private final Map<Cell, Entry> cache = new HashMap<>();
    private int budget;
    private WindEffectsParameters settings;
    static final MountainFlow[] EMPTY = new MountainFlow[0];
    void begin(WindEffectsParameters p, double now) {
        budget = 32;
        if (!p.equals(settings)) { cache.clear(); settings=p; }
        if (cache.size()>2048) cache.entrySet().removeIf(e -> now-e.getValue().checked()>120);
        if (cache.size()>4096) cache.clear();
    }
    MountainFlow[] nearby(ServerLevel level, long seed, double x, double z, double now, WindEffectsParameters p) {
        if (!p.mountainEnabled()) return EMPTY;
        int cx=Mth.floor(x/p.spacing()), cz=Mth.floor(z/p.spacing());
        // Cover every column whose drifting centre can reach this position, even with custom sizes.
        int reach=(int)Math.ceil((p.diameter()/2+p.driftRadius())/p.spacing());
        var flows=new ArrayList<MountainFlow>();
        for(int dx=-reach;dx<=reach;dx++) for(int dz=-reach;dz<=reach;dz++) {
            var cell=new Cell(cx+dx,cz+dz);
            var entry=cache.get(cell);
            if ((entry==null || (entry.flow()==null && now-entry.checked()>=5)) && budget>0) {
                budget--;
                entry=new Entry(now,discover(level,seed,cell,now,p)); cache.put(cell,entry);
            }
            if(entry!=null && entry.flow()!=null) {
                flows.add(entry.flow());
                cache.put(cell,new Entry(now,entry.flow()));
            }
        }
        return flows.toArray(EMPTY);
    }
    private static Integer height(ServerLevel level,int x,int z) {
        var chunk=level.getChunkSource().getChunkNow(x>>4,z>>4);
        return chunk==null ? null : chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x&15,z&15);
    }
    private static MountainFlow discover(ServerLevel level,long seed,Cell cell,double now,WindEffectsParameters p) {
        int x=Mth.floor((cell.x()+.5)*p.spacing()), z=Mth.floor((cell.z()+.5)*p.spacing());
        Integer h=height(level,x,z);
        if(h==null || h<p.minimumMountainHeight()) return null;
        var chunk=level.getChunkSource().getChunkNow(x>>4,z>>4);
        if(chunk==null) return null;
        int y=Mth.clamp(h,level.getMinBuildHeight(),level.getMaxBuildHeight()-1);
        boolean mountain=chunk.getNoiseBiome(x>>2,y>>2,z>>2).is(BiomeTags.IS_MOUNTAIN);
        if(!mountain) {
            for(int[] d:new int[][]{{32,0},{-32,0},{0,32},{0,-32}}) {
                Integer other=height(level,x+d[0],z+d[1]);
                if(other!=null && h-other>=p.minimumRelief()) { mountain=true; break; }
            }
        }
        if(!mountain) return null;
        long key=seed^(cell.x()*0x632be59bd9b4e019L)^(cell.z()*0x9e3779b97f4a7c15L);
        return new MountainFlow(x,h,z,key,now);
    }
}
