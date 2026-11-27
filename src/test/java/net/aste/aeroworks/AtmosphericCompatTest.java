package net.aste.aeroworks;

import net.aste.aeroworks.compat.CompatibilityPolicy;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

public final class AtmosphericCompatTest {
    private static void check(boolean result,String message) { if(!result)throw new AssertionError(message); }
    private static ClassNode read(JarFile jar,String name) throws Exception {
        var entry=jar.getJarEntry(name+".class");
        check(entry!=null,"Missing class: "+name);
        try(var in=jar.getInputStream(entry)){var node=new ClassNode();new ClassReader(in.readAllBytes()).accept(node,0);return node;}
    }
    private static MethodNode method(ClassNode node,String name,String desc) {
        return node.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(desc)).findFirst()
                .orElseThrow(()->new AssertionError(node.name+"."+name+desc));
    }
    public static void main(String[] args) throws Exception {
        String prefix="net.aste.aeroworks.mixin.";
        for(boolean aw:new boolean[]{false,true})for(boolean ds:new boolean[]{false,true}) {
            check(CompatibilityPolicy.apply(prefix+"atmospheric.AtmosphericWingSoaringMixin",aw,ds)==aw,"Atmospheric optional gate");
            check(CompatibilityPolicy.apply(prefix+"deepseas.DeepSeasSailWindMixin",aw,ds)==ds,"Deep Seas optional gate");
            check(CompatibilityPolicy.apply(prefix+"deepseas.DeepSeasWaveBuoyancyMixin",aw,ds)==(ds&&!aw),"One wave physics owner");
            check(CompatibilityPolicy.apply(prefix+"client.WaveShaderMixin",aw,ds)==!aw,"One water shader owner");
            check(CompatibilityPolicy.apply(prefix+"waves.SolidWaveBuoyancyMixin",aw,ds)==!aw,"One solid buoyancy owner");
            check(CompatibilityPolicy.apply(prefix+"wind.ServerSubLevelWindMixin",aw,ds),"Own aircraft model remains available");
        }
        try(var jar=new JarFile(args[0])) {
            String root="com/enzonic/aeronauticswinds/",body="Ldev/ryanhcode/sable/sublevel/ServerSubLevel;",
                    level="Lnet/minecraft/server/level/ServerLevel;",sim="L"+root+"WindsSim$Simulation;",
                    sample="L"+root+"wind/WindSample;",vec="Lorg/joml/Vector3d;",cache="L"+root+"sail/SailTracker$SailCache;";
            method(read(jar,root+"WindsSim"),"forLevel","(Lnet/minecraft/world/level/Level;)"+sim);
            method(read(jar,root+"WindsSim$Simulation"),"sample","(Lnet/minecraft/world/level/Level;DDD)"+sample);
            method(read(jar,root+"wind/WindSample"),"wind","()"+vec);
            method(read(jar,root+"physics/CloudTurbulence"),"apply","("+body+level+sim+vec+vec+"D)V");
            method(read(jar,root+"physics/FlightTurbulence"),"applyToShip","("+body+level+sample+vec+"DD)V");
            method(read(jar,root+"physics/WingSoaring"),"apply","("+body+level+cache+sample+vec+vec+"D)V");
            method(read(jar,root+"physics/SailTurbulencePhysics"),"computeTurbulence","("+body+cache+sample+vec+"D"+vec+vec+")D");
            var field=read(jar,root+"wind/WindField");
            method(field,"upperAtmosphereMultiplier","(DDDJ)D");
            method(field,"aloft","(D)D");
        }
        System.out.println("Atmospherics 2.1.0: optional-mod matrix, native wind API and all four flight override contracts verified");
    }
}
