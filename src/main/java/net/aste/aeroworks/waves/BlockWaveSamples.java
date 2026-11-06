package net.aste.aeroworks.waves;

import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import dev.ryanhcode.sable.api.physics.collider.SableCollisionContext;
import dev.ryanhcode.sable.physics.chunk.VoxelNeighborhoodState;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import java.util.Arrays;

/** Immutable bounded sample set. Only Builder reads block data, on the server tick. */
public record BlockWaveSamples(double[] cells,boolean complex,double weight) {
    public static final int MAX_SAMPLES=4096;
    static final class Builder {
        final ServerSubLevel body;
        final int minX,minY,minZ,sx,sy,sz;
        final long total;
        long cursor,count;
        int used;
        final double[] cells=new double[MAX_SAMPLES*4];
        final BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
        Builder(ServerSubLevel body){
            this.body=body;var b=body.getPlot().getBoundingBox();
            minX=b.minX();minY=b.minY();minZ=b.minZ();
            sx=b.width();sy=b.height();sz=b.length();
            total=sx<=0||sy<=0||sz<=0?0:(long)sx*sy*sz;
        }
        boolean sameBounds(){
            var b=body.getPlot().getBoundingBox();
            return minX==b.minX()&&minY==b.minY()&&minZ==b.minZ()
                    &&sx==b.width()&&sy==b.height()&&sz==b.length();
        }
        boolean done(){return cursor>=total;}
        void advance(){
            long i=cursor++;
            int x=minX+(int)(i%sx),y=minY+(int)(i/sx%sy),z=minZ+(int)(i/((long)sx*sy));
            var chunk=body.getLevel().getChunkSource().getChunkNow(x>>4,z>>4);
            if(chunk==null)return; // No loading or generation, including plot storage chunks.
            pos.set(x,y,z);
            var state=chunk.getSection(chunk.getSectionIndex(y)).getBlockState(x&15,y&15,z&15);
            if(state.isAir()||VoxelNeighborhoodState.isLiquid(state))return;
            double volume=PhysicsBlockPropertyHelper.getVolume(state);
            if(!Double.isFinite(volume)||volume<=0)return;
            var view=body.getPlot().getEmbeddedLevelAccessor();
            var shape=state.getBlock() instanceof BlockSubLevelCollisionShape custom
                    ?custom.getSubLevelCollisionShape(view,state):state.getCollisionShape(view,pos,SableCollisionContext.get());
            if(shape.isEmpty())return;
            long seen=++count;
            int slot;
            if(used<MAX_SAMPLES)slot=used++;
            else {
                // Deterministic reservoir: bounded work/memory without excluding thin/sparse hulls.
                long hash=seen*0x9e3779b97f4a7c15L;hash=(hash^(hash>>>30))*0xbf58476d1ce4e5b9L;
                hash=(hash^(hash>>>27))*0x94d049bb133111ebL;hash^=hash>>>31;
                long selected=Long.remainderUnsigned(hash,seen);
                if(selected>=MAX_SAMPLES)return;slot=(int)selected;
            }
            int at=slot*4;cells[at]=x+.5;cells[at+1]=y+.5;cells[at+2]=z+.5;cells[at+3]=volume;
        }
        BlockWaveSamples finish(){return new BlockWaveSamples(Arrays.copyOf(cells,used*4),sx+sy+sz-3<10,
                used==0?0:(double)count/used);}
    }
}
