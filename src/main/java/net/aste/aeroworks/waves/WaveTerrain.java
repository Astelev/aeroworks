package net.aste.aeroworks.waves;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Same loaded-water tests on both sides; no generation, fluid simulation or cloud noise. */
public final class WaveTerrain {
    private WaveTerrain(){}
    public static float cell(Level level,int x,int z,BlockPos.MutableBlockPos pos){
        float h=column(level,x,z,pos);
        if(h==ShoreField.DRY)return h;
        for(int dx=-2;dx<=2;dx+=4)for(int dz=-2;dz<=2;dz+=4)
            if(column(level,x+dx,z+dz,pos)!=h)return ShoreField.DRY;
        return h;
    }
    public static float column(Level level,int x,int z,BlockPos.MutableBlockPos pos){
        var chunk=level instanceof ServerLevel server ? server.getChunkSource().getChunkNow(x>>4,z>>4)
                : level.getChunkSource().getChunk(x>>4,z>>4,ChunkStatus.FULL,false);
        if(chunk==null)return ShoreField.DRY;
        int y=chunk.getHeight(Heightmap.Types.WORLD_SURFACE,x&15,z&15);
        if(y<level.getMinBuildHeight()+2)return ShoreField.DRY;
        pos.set(x,y,z);
        var state=terrainState(chunk,x,y,z);var fluid=state.getFluidState();
        if(!fluid.is(FluidTags.WATER)||!fluid.isSource()||!state.getCollisionShape(level,pos).isEmpty())return ShoreField.DRY;
        for(int d=1;d<=2;d++)if(!terrainState(chunk,x,y-d,z).getFluidState().is(FluidTags.WATER))return ShoreField.DRY;
        return y;
    }
    public static float depth(Level level,int x,int z,float waterBlockY){
        if(waterBlockY==ShoreField.DRY)return 0;
        var chunk=level instanceof ServerLevel server?server.getChunkSource().getChunkNow(x>>4,z>>4)
                :level.getChunkSource().getChunk(x>>4,z>>4,ChunkStatus.FULL,false);
        return chunk==null?0:Math.clamp(waterBlockY-chunk.getHeight(Heightmap.Types.OCEAN_FLOOR,x&15,z&15),0,128);
    }
    /** Deep Seas masks LevelChunk block reads inside hulls. Shorelines need physical terrain,
     * not that moving air pocket; section palettes are also its own realBlockState source. */
    private static BlockState terrainState(ChunkAccess chunk,int x,int y,int z){
        return chunk.getSection(chunk.getSectionIndex(y)).getBlockState(x&15,y&15,z&15);
    }
}
