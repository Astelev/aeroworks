package net.aste.aeroworks;

import dev.ryanhcode.sable.companion.math.Pose3d;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.jar.JarFile;
import net.aste.aeroworks.waves.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3d;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public final class WavePhysicsTest {
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static void near(double a,double b,double eps,String why){check(Math.abs(a-b)<=eps,why+": "+a+" != "+b);}
    private static WaveShorePatch ocean(){float[] h=new float[65*65];Arrays.fill(h,63);return new WaveShorePatch(-128,-128,65,h,16);}
    private static void basis(WaveBuoyancyStep s,Pose3d pose){
        for(var p:new Vector3d[]{new Vector3d(),new Vector3d(1,0,0),new Vector3d(0,1,0),new Vector3d(0,0,1)}){
            pose.transformPosition(p);s.transform(p.x,p.z);
        }
    }
    private static WaveParameters profile(boolean enabled,boolean physical,double amplitude){return new WaveParameters(enabled,physical,amplitude,32,4,0,16);}
    public static void main(String[] args)throws Exception{
        var shore=ocean();var step=new WaveBuoyancyStep();var pose=new Pose3d();
        step.begin(profile(true,true,.5),shore,0,1);basis(step,pose);
        double bow=Math.clamp(step.depth(.5,4,0,0,63),0,1),stern=Math.clamp(step.depth(.5,-4,0,0,63),0,1);
        near(bow+stern,1,1e-6,"Symmetric wave changes lift centre without duplicating total lift");
        double centre=(4*bow-4*stern)/(bow+stern);
        check(centre>2,"Wave must move centre of buoyancy towards the submerged bow");
        step.observe(true);step.depth(.5,4,0,0,63);step.depth(3,4,0,0,63);
        check(step.observation().samples()==2 && step.observation().weighted()==2 && step.observation().changed()==1,"Runtime diagnostics distinguish real immersion change from fully submerged cells");
        step.begin(profile(true,true,.5),shore,4,1);basis(step,pose);
        double opposite=4*Math.clamp(step.depth(.5,4,0,0,63),0,1)-4*Math.clamp(step.depth(.5,-4,0,0,63),0,1);
        near(opposite,-centre,1e-6,"Half period reverses pitch moment");
        for(var p:new WaveParameters[]{profile(false,true,.5),profile(true,false,.5),profile(true,true,0)}){
            step.begin(p,shore,0,1);basis(step,pose);
            check(Double.doubleToLongBits(step.depth(.123,4,0,0,63))==Double.doubleToLongBits(.123),"Disabled waves must preserve native depth exactly");
        }
        step.begin(profile(true,true,.5),shore,0,1);basis(step,pose);
        near(step.depth(.5,4,0,0,70),.5,0,"No waves from a different lake level");
        near(step.depth(.5,200,0,0,63),.5,0,"No extrapolation outside loaded shore patch");
        near(Math.clamp(step.depth(3,4,0,0,63),0,1),1,0,"Fully submerged cells retain original maximum volume");
        near(Math.clamp(step.depth(-2,4,0,0,63),0,1),0,0,"No force on dry cells above crest");
        var rotated=new Pose3d();rotated.position().set(23,64,-21);rotated.rotationPoint().set(1000000,50,-2000000);
        rotated.scale().set(.5,1.5,.7);rotated.orientation().rotateXYZ(.2,.6,-.1);
        var local=new Vector3d(1000004,50,-1999997);var world=rotated.transformPosition(new Vector3d(local));
        step.begin(profile(true,true,.5),shore,7,1);basis(step,rotated);
        double expected=WaveMath.height(WaveMath.phase(world.x,world.z,7,32,4,0),.5,shore.weight(world.x,world.z,63));
        near(step.depth(.5,local.x,local.y,local.z,63)-.5,expected,.001,"Rotation, scale and distant storage pivot use world-space wave phase");
        for(double a=-100000;a<100000;a+=.713)near(WaveMath.fastSin(a),Math.sin(a),3.1e-7,"Fast sine must agree with displayed wave");
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try{
            var payload=new WaveStatePayload(ResourceLocation.fromNamespaceAndPath("minecraft","overworld"),profile(true,true,.8),WaveNetwork.REVISION,"field=ready applies=100 cells=64 wet-wave=64 changed=12 max-dY=0.500");
            WaveStatePayload.CODEC.encode(buf,payload);check(buf.readableBytes()<512,"Only small profile packets, no per-cell forces");
            check(WaveStatePayload.CODEC.decode(buf).equals(payload)&&buf.readableBytes()==0,"Visual/physics profile synchronization");
        }finally{buf.release();}
        boolean rejected=false;try{profile(true,true,Double.NaN);}catch(IllegalArgumentException e){rejected=true;}
        check(rejected,"Reject malformed wave parameters");
        try(var jar=new JarFile(args[0])){
            var node=new ClassNode();try(var in=jar.getInputStream(jar.getJarEntry("com/maxenonyme/highseas/BoatBuoyancySystem.class"))){new ClassReader(in.readAllBytes()).accept(node,0);}
            var apply=node.methods.stream().filter(m->m.name.equals("apply")).findFirst().orElseThrow();
            int transforms=0,clamps=0,lift=0;
            for(var i:apply.instructions)if(i instanceof MethodInsnNode c){
                if(c.owner.endsWith("Pose3dc")&&c.name.equals("transformPosition"))transforms++;
                if(c.owner.equals("net/minecraft/util/Mth")&&c.name.equals("clamp")&&c.desc.equals("(DDD)D"))clamps++;
                if(c.name.equals("applyImpulseAtPoint"))lift++;
            }
            check(transforms==4&&clamps==1&&lift==1,"Native cached-volume pass/affine transforms/single buoyancy impulse changed");
            for(int slot:new int[]{11,19,26,28,30})check(apply.localVariables.stream().anyMatch(v->v.index==slot&&v.desc.equals("D")),"Captured cell/plane slot changed: "+slot);
            var scan=node.methods.stream().filter(m->m.name.equals("scan")).findFirst().orElseThrow();
            check(scan.desc.startsWith("(Lnet/minecraft/server/level/ServerLevel;Ldev/ryanhcode/sable/sublevel/SubLevel;Ljava/util/UUID;)"),"Hull scan tracking contract changed");
        }
        var stepClass=new ClassNode();try(var in=WavePhysicsTest.class.getClassLoader().getResourceAsStream("net/aste/aeroworks/waves/WaveBuoyancyStep.class")){new ClassReader(in.readAllBytes()).accept(stepClass,0);}
        for(var m:stepClass.methods)for(var i:m.instructions)if(i instanceof MethodInsnNode c)
            check(!c.owner.startsWith("net/minecraft/")&&!c.owner.startsWith("dev/ryanhcode/sable/"),"World/physics API query leaked into the per-cell math path");
        System.out.println("Wave buoyancy: native single-force contract, alternating lift centre, dry/submerged/disabled cases, world pose, coast bounds, sine accuracy and synchronized profile verified");
    }
}
