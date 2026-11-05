package net.aste.aeroworks;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
public final class DeepSeasTargetsTest {
    private static ClassNode read(InputStream stream)throws Exception{try(stream){var n=new ClassNode();new ClassReader(stream.readAllBytes()).accept(n,0);return n;}}
    private static ClassNode entry(JarFile jar,String path)throws Exception{return read(jar.getInputStream(jar.getJarEntry(path+".class")));}
    private static MethodNode method(ClassNode c,String name,String desc){return c.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(desc)).findFirst().orElseThrow(()->new AssertionError(c.name+"."+name+desc));}
    private static int calls(MethodNode m,String owner,String name){int n=0;for(var i:m.instructions)if(i instanceof MethodInsnNode c&&c.owner.equals(owner)&&c.name.equals(name))n++;return n;}
    public static void main(String[] args)throws Exception{
        String hash=java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(args[0]))));
        if(!hash.equals("fc7b9c6023978e9d31c901f532c5e54d1d13e0f2431c20b5919b2a3368ad9c08"))throw new AssertionError("Deep Seas JAR differs from attached 3.4.7: "+hash);
        try(var jar=new JarFile(args[0])){
            var manager=entry(jar,"com/maxenonyme/highseas/wind/WindManager");
            String result="Lcom/maxenonyme/highseas/wind/WindSample;";
            method(manager,"getWind","(Lnet/minecraft/world/level/Level;DDD)"+result);
            var overload=method(manager,"getWind","(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;)"+result);
            if(calls(overload,manager.name,"getWind")!=1)throw new AssertionError("Vector overload no longer delegates");
            var sample=entry(jar,"com/maxenonyme/highseas/wind/WindSample");method(sample,"<init>","(Lnet/minecraft/world/phys/Vec3;DDDDD)V");
            var system=entry(jar,"com/maxenonyme/highseas/sail/SailWindSystem");
            var pull=method(system,"pull","(Lnet/minecraft/server/level/ServerLevel;Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Ldev/ryanhcode/sable/companion/math/Pose3dc;Lcom/maxenonyme/highseas/sail/SailGroup;DDLcom/maxenonyme/highseas/sail/SailWindSystem$Drive;)V");
            if(calls(pull,manager.name,"getWind")!=1)throw new AssertionError("Expected exactly one group wind call");
            if(calls(pull,"dev/ryanhcode/sable/companion/math/Pose3dc","transformPosition")!=1)throw new AssertionError("Sail coordinates must already be world coordinates");
            for(String type:new String[]{"client/SailShaderState","client/WindVaneGoggle","client/WindVaneAngle"}){
                int count=0;for(var m:entry(jar,"com/maxenonyme/highseas/"+type).methods)count+=calls(m,manager.name,"getWind");
                if(count==0)throw new AssertionError("Missing visual wind path "+type);
            }
            var sail=entry(jar,"com/maxenonyme/highseas/block/BoatSailBlock");
            for(String name:new String[]{"sable$getLiftScalar","sable$getParallelDragScalar"}){
                var m=method(sail,name,"()F");boolean zero=false;for(var i:m.instructions)if(i.getOpcode()==org.objectweb.asm.Opcodes.FCONST_0)zero=true;
                if(!zero)throw new AssertionError("Recheck duplicate native aerodynamics: "+name);
            }
        }
        // Optional targets must never leak into eagerly loaded common bridge/network classes.
        for(String name:new String[]{"DeepSeasWind","DeepSeasWindPayload"}){
            var n=read(DeepSeasTargetsTest.class.getClassLoader().getResourceAsStream("net/aste/aeroworks/compat/deepseas/"+name+".class"));
            for(var m:n.methods)for(var i:m.instructions)if(i instanceof MethodInsnNode c&&(c.owner.startsWith("com/maxenonyme/")||c.owner.startsWith("net/minecraft/client/")))throw new AssertionError("Optional/side dependency leak: "+c.owner);
        }
        System.out.println("Deep Seas 3.4.7 SHA256, both wind entry points, world-space sail hook, client indicators and optional-side isolation verified");
    }
}
