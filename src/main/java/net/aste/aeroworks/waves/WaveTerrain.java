package net.aste.aeroworks.waves;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
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
        var state=chunk.getBlockState(pos.set(x,y,z));var fluid=state.getFluidState();
        if(!fluid.is(FluidTags.WATER)||!fluid.isSource()||!state.getCollisionShape(level,pos).isEmpty())return ShoreField.DRY;
        for(int d=1;d<=2;d++)if(!chunk.getFluidState(pos.set(x,y-d,z)).is(FluidTags.WATER))return ShoreField.DRY;
        return y;
    }
}
