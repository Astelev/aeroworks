package net.aste.aeroworks;

import dev.ryanhcode.sable.companion.math.Pose3d;
import net.aste.aeroworks.waves.*;
import org.joml.Vector3d;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.util.Arrays;
import java.util.jar.JarFile;

public final class SolidWaveTest {
    private static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    private static void near(double a,double b,String why){check(Math.abs(a-b)<2e-6,why+": "+a+" != "+b);}
    // Independent sum of native do_float's axis-aligned cube/water-voxel intersections.
    private static double nativeLift(double x,double y,double z,double half,double surface,double strength){
        double sum=0;
        for(int ix=(int)Math.floor(x-half);ix<=Math.floor(x+half);ix++)
            for(int iz=(int)Math.floor(z-half);iz<=Math.floor(z+half);iz++)
                for(int iy=50;iy<surface;iy++){
                    double dx=Math.max(0,Math.min(x+half,ix+1)-Math.max(x-half,ix));
                    double dz=Math.max(0,Math.min(z+half,iz+1)-Math.max(z-half,iz));
                    double dy=Math.max(0,Math.min(y+half,Math.min(iy+1,surface))-Math.max(y-half,iy));
                    sum+=10.5*dx*dy*dz*strength;
                }
        return sum;
    }
    public static void main(String[] args)throws Exception{
        for(double half:new double[]{.25,.5})for(double y:new double[]{62,63.6,64,64.4,66})
            for(double dy:new double[]{-.5,-.13,0,.21,.5})for(double strength:new double[]{.2,1,2}){
                // Native voxel integral for the continuous flat ocean, with its top moved by dy.
                // Evaluate via shifted body against the original integer sea surface.
                double expected=nativeLift(-2.13,y-dy,3.91,half,64,strength)-nativeLift(-2.13,y,3.91,half,64,strength);
                near(BlockWaveMath.delta(y,half,64,dy,strength),expected,"Wave delta equals displaced native volume");
            }
        float[] ocean=new float[65*65];Arrays.fill(ocean,63);
        var shore=new WaveShorePatch(-128,-128,65,ocean,16);
        var p=new WaveParameters(true,true,.5,32,4,0,16);
        var block=new BlockWaveSamples(new double[]{0,64,0,1},true,1);
        var step=new BlockWaveStep();var pose=new Pose3d();pose.position().set(8,0,0);
        step.calculate(block,shore,p,pose,new Vector3d(0,64,0),0,1,.05);
        check(step.impulse.y>0&&step.samples==8,"Single solid block without an air compartment reacts to crest");
        double crest=step.impulse.y;
        step.calculate(block,shore,p,pose,new Vector3d(0,64,0),4,1,.05);
        near(step.impulse.y,-crest,"Trough subtracts native block buoyancy instead of adding another lift");
        step.calculate(block,shore,p,pose,new Vector3d(0,64,0),0,1,.025);
        near(step.impulse.y,crest/2,"Substep-independent impulse");
        var wing=new BlockWaveSamples(new double[]{-4,64,0,1,4,64,0,1},false,1);
        pose.position().zero();pose.orientation().rotationY(.6);
        step.calculate(wing,shore,p,pose,new Vector3d(0,64,0),0,1,.05);
        check(step.torque.length()>0,"Wave slope produces rocking on a rotated solid hull");
        for(var disabled:new WaveParameters[]{new WaveParameters(false,true,.5,32,4,0,16),
                new WaveParameters(true,false,.5,32,4,0,16),new WaveParameters(true,true,0,32,4,0,16)}){
            step.calculate(wing,shore,disabled,pose,new Vector3d(0,64,0),0,1,.05);
            check(step.impulse.lengthSquared()==0&&step.torque.lengthSquared()==0,"Disabled correction is exactly zero");
        }
        for(double y:new double[]{55,70}){
            var dryOrDeep=new BlockWaveSamples(new double[]{0,y,0,1},true,1);
            step.calculate(dryOrDeep,shore,p,pose,new Vector3d(0,y,0),0,1,.05);
            check(step.impulse.lengthSquared()==0&&step.torque.lengthSquared()==0,"Dry/fully submerged blocks retain native force");
        }
        float[] land=new float[65*65];Arrays.fill(land,ShoreField.DRY);
        step.calculate(block,new WaveShorePatch(-128,-128,65,land,16),p,pose,new Vector3d(),0,1,.05);
        check(step.impulse.lengthSquared()==0,"No wave lift over land or unloaded terrain");
        // Neither step math class may read worlds, chunks, states, config or native APIs.
        for(String name:new String[]{"BlockWaveMath","BlockWaveStep"}){
            var node=new ClassNode();
            try(var in=SolidWaveTest.class.getResourceAsStream("/net/aste/aeroworks/waves/"+name+".class")){
                new ClassReader(in).accept(node,0);
            }
            for(var m:node.methods)for(var insn:m.instructions)if(insn instanceof MethodInsnNode call)
                check(!call.owner.startsWith("net/minecraft/")&&!call.owner.contains("Rapier3D"),"No per-step world/native queries");
        }
        try(var outer=new JarFile(args[0])){
            var server=new ClassNode();new ClassReader(outer.getInputStream(outer.getJarEntry("dev/ryanhcode/sable/sublevel/ServerSubLevel.class"))).accept(server,0);
            check(server.methods.stream().anyMatch(m->m.name.equals("prePhysicsTick")&&m.desc.equals("(Ldev/ryanhcode/sable/sublevel/system/SubLevelPhysicsSystem;Ldev/ryanhcode/sable/api/physics/handle/RigidBodyHandle;D)V")),"Exact Sable solid wave hook");
            var plot=new ClassNode();new ClassReader(outer.getInputStream(outer.getJarEntry("dev/ryanhcode/sable/sublevel/plot/ServerLevelPlot.class"))).accept(plot,0);
            check(plot.methods.stream().anyMatch(m->m.name.equals("onBlockChange")&&m.desc.equals("(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V")),"Geometry invalidation hook");
        }
        System.out.println("Solid waves: native voxel-volume differential, single block, crest/trough, timestep, rocking, dry/deep/disabled and hook contracts passed");
    }
}
