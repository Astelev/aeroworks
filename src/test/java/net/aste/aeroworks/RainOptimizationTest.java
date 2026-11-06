package net.aste.aeroworks;

import net.aste.aeroworks.client.rain.*;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import java.util.jar.JarFile;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public final class RainOptimizationTest {
    private static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    private static int calls(MethodNode method,String owner,String name){int count=0;for(var i:method.instructions)if(i instanceof MethodInsnNode c&&c.owner.equals(owner)&&c.name.equals(name))count++;return count;}
    private static ClassNode read(JarFile jar,String path)throws Exception{
        var n=new ClassNode();try(var in=jar.getInputStream(jar.getJarEntry(path+".class"))){new ClassReader(in).accept(n,0);}return n;
    }
    private static MethodNode method(ClassNode n,String name){return n.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();}
    public static void main(String[] args)throws Exception{
        var rng=RandomSource.create(0);int accepted=0;
        for(int i=0;i<200000;i++){
            long seed=BlockPos.asLong(i*173%60000000-30000000,i%384-64,i*-71%60000000);
            rng.setSeed(seed);boolean nativeResult=rng.nextInt(100)<=2;
            check(RainSpawn.accepted(seed)==nativeResult,"Candidate filter must exactly preserve Minecraft's distribution");
            if(nativeResult)accepted++;
        }
        check(accepted>5000&&accepted<7000,"Original 3% deterministic strip placement retained");
        check(RainRaySchedule.near(15,0,-15,0,0,0),"Slanted ray crossing the camera is always refreshed");
        check(!RainRaySchedule.near(30,30,40,40,0,0),"Distant ray may be reused");
        for(boolean near:new boolean[]{false,true})for(boolean changed:new boolean[]{false,true})
            check(RainRaySchedule.refresh(1,0,3,false,changed,near),"First ray always computed");
        check(!RainRaySchedule.refresh(11,10,3,true,false,false),"Distant unchanged ray reused");
        check(RainRaySchedule.refresh(13,10,3,true,false,false),"Distant cache bounded to configured ticks");
        check(RainRaySchedule.refresh(11,10,3,true,false,true),"Near camera exact every tick");
        check(RainRaySchedule.refresh(11,10,3,true,true,false),"Changed rain angle invalidates ray");
        check(RainRaySchedule.refresh(11,10,1,true,false,false),"Interval one restores full ray frequency");
        check(RainRaySchedule.behind(-10,-2,.5),"Fully behind-camera strip culled");
        check(!RainRaySchedule.behind(-20,4,.5),"Slanted ray crossing camera plane retained");
        check(!RainRaySchedule.behind(-.2,-.3,2),"Wide billboard crossing camera plane retained");
        try(var jar=new JarFile(args[0])){
            var world=read(jar,"dev/nonamecrackers2/simpleclouds/client/renderer/WorldEffects");
            var tick=method(world,"tick");var render=method(world,"renderRain");
            int positions=0,seeded=0;
            for(var i:tick.instructions)if(i instanceof MethodInsnNode c){
                if(c.owner.equals("net/minecraft/core/BlockPos")&&c.name.equals("<init>")&&c.desc.equals("(III)V"))positions++;
                if(c.owner.equals("net/minecraft/util/RandomSource")&&c.name.equals("create")&&c.desc.startsWith("(J)"))seeded++;
            }
            check(positions==1&&seeded==1,"Exact spawn constructor/factory hooks");
            check(calls(tick,"net/minecraft/world/level/biome/Biome","getPrecipitationAt")==1,"Candidate precipitation hook");
            check(calls(render,"net/minecraft/client/renderer/LevelRenderer","getLightColor")==1,"Native rain lighting hook");
            var quad=read(jar,"dev/nonamecrackers2/simpleclouds/client/renderer/rain/PrecipitationQuad");
            var update=method(quad,"tick");var draw=method(quad,"render");
            check(calls(update,"java/util/function/Function","apply")==1,"Single native raycast per tick");
            check(calls(draw,"org/joml/Quaternionf","<init>")==1&&calls(draw,"org/joml/Vector3f","<init>")==1,"Exact scratch-object reuse hooks");
            for(String field:new String[]{"position","blockPos","lifeSpan","initialWidth","raycaster","length","xRot","yRot","tickCount","widthO","width"})
                check(quad.fields.stream().anyMatch(f->f.name.equals(field)),"Native precipitation field changed: "+field);
        }
        System.out.println("Simple Clouds rain: 200000 exact spawn decisions, ray freshness/near-camera cases and 0.7.3 bytecode hooks passed; rejected candidates avoid position/RNG allocation");
    }
}
