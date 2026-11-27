package net.aste.aeroworks.waves;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;

/** Shape queries in absolute plot-storage coordinates. Missing neighbours stay empty;
 * they must never turn a buoyancy scan into a synchronous chunk-load request. */
final class LoadedWaveBlockView implements BlockGetter {
    @FunctionalInterface interface Chunks { BlockGetter find(int x,int z); }
    private final Chunks chunks;
    private final int minY,height;
    LoadedWaveBlockView(Chunks chunks,int minY,int height){
        this.chunks=chunks;this.minY=minY;this.height=height;
    }
    static LoadedWaveBlockView of(ServerLevel level){
        var source=level.getChunkSource();
        return new LoadedWaveBlockView(source::getChunkNow,level.getMinBuildHeight(),level.getHeight());
    }
    private BlockGetter chunk(BlockPos pos){
        if(pos.getY()<minY||(long)pos.getY()-minY>=height)return null;
        return chunks.find(pos.getX()>>4,pos.getZ()>>4);
    }
    @Override public BlockState getBlockState(BlockPos pos){
        var chunk=chunk(pos);
        return chunk==null?Blocks.AIR.defaultBlockState():chunk.getBlockState(pos);
    }
    @Override public FluidState getFluidState(BlockPos pos){return getBlockState(pos).getFluidState();}
    @Override public BlockEntity getBlockEntity(BlockPos pos){
        var chunk=chunk(pos);
        // Only already existing block entities: no construction/onLoad callbacks during shape scans.
        if(chunk instanceof LevelChunk loaded)return loaded.getBlockEntities().get(pos);
        return chunk==null?null:chunk.getBlockEntity(pos);
    }
    @Override public int getHeight(){return height;}
    @Override public int getMinBuildHeight(){return minY;}
}
