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
        try(var config=AtmosphericCompatTest.class.getResourceAsStream("/aeroworks.mixins.json")) {
            check(config!=null,"Missing packaged mixin configuration");
            var json=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(config,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var names=new java.util.HashSet<String>();
            for(String group:new String[]{"mixins","client"})for(var name:json.getAsJsonArray(group))
                check(names.add(name.getAsString()),"Duplicate mixin: "+name);
        }
        // Load the public bridges while denying both optional namespaces, as on a standalone install.
        var isolated = new ClassLoader(AtmosphericCompatTest.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
                if(name.startsWith("com.enzonic.aeronauticswinds.") || name.startsWith("com.maxenonyme."))
                    throw new ClassNotFoundException("Optional mod deliberately absent: "+name);
                if(!name.startsWith("net.aste.aeroworks.compat."))return super.loadClass(name,resolve);
                synchronized(getClassLoadingLock(name)) {
                    var found=findLoadedClass(name);
                    if(found!=null)return found;
                    try(var in=getParent().getResourceAsStream(name.replace('.','/')+".class")) {
                        if(in==null)throw new ClassNotFoundException(name);
                        byte[] data=in.readAllBytes();
                        var type=defineClass(name,data,0,data.length);
                        if(resolve)resolveClass(type);
                        return type;
                    } catch(java.io.IOException e) { throw new ClassNotFoundException(name,e); }
                }
            }
        };
        Class.forName("net.aste.aeroworks.compat.atmospheric.AtmosphericWind",true,isolated);
        Class.forName("net.aste.aeroworks.compat.deepseas.DeepSeasWind",true,isolated);
        String prefix="net.aste.aeroworks.mixin.";
        for(boolean aw:new boolean[]{false,true})for(boolean ds:new boolean[]{false,true}) {
            check(CompatibilityPolicy.apply(prefix+"atmospheric.AtmosphericWingSoaringMixin",aw,ds)==aw,"Atmospheric optional gate");
            check(CompatibilityPolicy.apply(prefix+"deepseas.DeepSeasSailWindMixin",aw,ds)==ds,"Deep Seas optional gate");
            check(CompatibilityPolicy.apply(prefix+"deepseas.DeepSeasWaveBuoyancyMixin",aw,ds)==(ds&&!aw),"One wave physics owner");
            check(CompatibilityPolicy.apply(prefix+"client.WaveShaderMixin",aw,ds)==!aw,"One water shader owner");
            check(CompatibilityPolicy.apply(prefix+"waves.SolidWaveBuoyancyMixin",aw,ds)==!aw,"One solid buoyancy owner");
            check(CompatibilityPolicy.apply(prefix+"wind.ServerSubLevelWindMixin",aw,ds),"Own aircraft model remains available");
        }
        var actual=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(args[0]))));
        check(actual.equals("ed5b869358f95a955c1e1ebe2a1c87131ec60644897b4211bae16a3a09912382"),"Atmospherics differs from the supplied 2.1.0 JAR: "+actual);
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
