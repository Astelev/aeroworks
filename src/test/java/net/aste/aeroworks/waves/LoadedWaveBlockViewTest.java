package net.aste.aeroworks.waves;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import java.util.*;

/** Reproduces a neighbour-dependent shape query at far-away Sable storage coordinates. */
public final class LoadedWaveBlockViewTest {
    private static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        // This JavaExec regression runs outside the NeoForge game launcher.
        net.neoforged.fml.loading.LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        var sail=new BlockPos(-268435441,64,268435454); // x=15 and z=14 in their storage chunks
        var support=sail.east(); // crosses a storage-chunk boundary
        var blocks=new HashMap<BlockPos,BlockState>();blocks.put(support,Blocks.OAK_PLANKS.defaultBlockState());
        var read=new ArrayList<BlockPos>();var queried=new ArrayList<String>();
        BlockGetter chunk=new BlockGetter(){
            @Override public BlockState getBlockState(BlockPos p){read.add(p.immutable());return blocks.getOrDefault(p,Blocks.AIR.defaultBlockState());}
            @Override public BlockEntity getBlockEntity(BlockPos p){return null;}
            @Override public FluidState getFluidState(BlockPos p){return getBlockState(p).getFluidState();}
            @Override public int getHeight(){return 384;}
            @Override public int getMinBuildHeight(){return -64;}
        };
        var view=new LoadedWaveBlockView((x,z)->{
            queried.add(x+","+z);
            // Only the support's chunk is loaded; lookup never generates another chunk.
            return x==(support.getX()>>4)&&z==(support.getZ()>>4)?chunk:null;
        },-64,384);
        check(view.getBlockState(sail.east()).is(Blocks.OAK_PLANKS),"Neighbour support is read at its original absolute storage coordinate");
        check(read.equals(List.of(support)),"No second plot-centre offset");
        check(view.getBlockState(sail.west()).isAir(),"Missing neighbouring chunk remains empty");
        check(view.getFluidState(sail.west()).isEmpty()&&view.getBlockEntity(sail.west())==null,"Fluid/entity reads cannot load missing chunks");
        int requests=queried.size();
        check(view.getBlockState(new BlockPos(sail.getX(),320,sail.getZ())).isAir(),"Above-world support is empty");
        check(view.getBlockState(new BlockPos(sail.getX(),-65,sail.getZ())).isAir(),"Below-world support is empty");
        check(queried.size()==requests,"Out-of-height queries never reach chunk sections");
        // Tripwire against restoring the blocking embedded accessor on either shape path.
        for(String name:new String[]{"BlockWaveSamples$Builder","LoadedWaveBlockView"}){
            var node=new ClassNode();
            try(var in=LoadedWaveBlockViewTest.class.getResourceAsStream("/net/aste/aeroworks/waves/"+name+".class")){new ClassReader(in).accept(node,0);}
            for(var m:node.methods)for(var insn:m.instructions)if(insn instanceof MethodInsnNode call)
                check(!call.name.equals("getEmbeddedLevelAccessor")&&!call.name.equals("getChunk"),"No blocking or coordinate-translating shape accessor: "+call.owner+"."+call.name);
        }
        System.out.println("Wave shape access: absolute storage neighbours, missing chunks, height bounds and non-loading geometry contract passed");
    }
}
