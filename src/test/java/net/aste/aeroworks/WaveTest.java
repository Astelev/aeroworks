package net.aste.aeroworks;

import java.nio.file.*;
import java.util.Arrays;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import net.aste.aeroworks.client.waves.WaveShader;
import net.aste.aeroworks.waves.ShoreField;
import net.aste.aeroworks.waves.WaveMath;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

public final class WaveTest {
    private static void check(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
    private static String read(JarFile jar,String path)throws Exception {
        try(var in=jar.getInputStream(jar.getJarEntry(path))) { return new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8); }
    }
    private static String expand(JarFile jar,String source)throws Exception {
        var m=Pattern.compile("#import <sodium:([^>]+)>").matcher(source);
        var out=new StringBuilder();
        while(m.find()) m.appendReplacement(out,java.util.regex.Matcher.quoteReplacement(expand(jar,read(jar,"assets/sodium/shaders/"+m.group(1)))));
        return m.appendTail(out).toString();
    }
    private static void target(JarFile jar,String type,String name,String desc)throws Exception {
        var node=new ClassNode();
        try(var in=jar.getInputStream(jar.getJarEntry("net/caffeinemc/mods/sodium/client/"+type+".class"))) {
            new ClassReader(in.readAllBytes()).accept(node,0);
        }
        check(node.methods.stream().anyMatch(m->m.name.equals(name)&&m.desc.equals(desc)),"Changed Sodium hook: "+name);
    }
    public static void main(String[] args)throws Exception {
        double p=WaveMath.phase(15,-8,3,32,4,.4);
        check(Math.abs(WaveMath.height(p,.5,0))<1e-10,"Dry shore moves");
        double shifted=WaveMath.phase(15+4*Math.cos(.4),-8+4*Math.sin(.4),4,32,4,.4);
        check(Math.abs(Math.sin(p)-Math.sin(shifted))<1e-10,"Wave does not travel with configured speed");
        for(int x=-500;x<500;x++) check(Math.abs(WaveMath.height(WaveMath.phase(x,0,7,32,4,.4),.5,.7))<=.35000001,"Amplitude exceeded");
        int size=41;
        float[] h=new float[size*size]; Arrays.fill(h,63.88789f);
        float[] field=ShoreField.build(h,size,4,16);
        check(field[(20*size+20)*2+1]==1,"Open water has no waves");
        check(field[(1*size+20)*2+1]==0,"Map/shore margin moves");
        h[20*size+20]=ShoreField.DRY;
        field=ShoreField.build(h,size,4,16);
        check(field[(20*size+20)*2+1]==0 && field[(20*size+21)*2+1]==0,"Island shore moves");
        Arrays.fill(h,ShoreField.DRY);
        for(int z=18;z<=22;z++) for(int x=18;x<=22;x++) h[z*size+x]=63.88789f;
        field=ShoreField.build(h,size,4,16);
        check(field[(20*size+20)*2+1]==0,"Small pond moves");
        Arrays.fill(h,63.88789f);
        for(int z=0;z<size;z++) for(int x=21;x<size;x++) h[z*size+x]=70.88789f;
        field=ShoreField.build(h,size,4,16);
        check(field[(20*size+20)*2+1]==0,"Different water levels blend together");
        try(var jar=new JarFile(args[0])) {
            target(jar,"gl/shader/ShaderLoader","getShaderSource","(Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/String;");
            String pass="(Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;)V";
            target(jar,"render/chunk/ShaderChunkRenderer","begin",pass);
            target(jar,"render/chunk/ShaderChunkRenderer","end",pass);
            String vertex=read(jar,"assets/sodium/shaders/blocks/block_layer_opaque.vsh");
            String patched=WaveShader.patch(vertex);
            check(!patched.equals(vertex),"Sodium shader was not patched");
            check(WaveShader.patch(patched).equals(patched),"Wave shader patched twice");
            String fragment=read(jar,"assets/sodium/shaders/blocks/block_layer_opaque.fsh");
            check(!fragment.contains("aste_wavePosition"),"Unexpected fragment modification");
            Files.createDirectories(Path.of("build/wave-test"));
            Files.writeString(Path.of("build/wave-test/terrain.vsh"),expand(jar,patched).replace("#version 330 core","#version 330 core\n#define USE_VERTEX_COMPRESSION\n#define USE_FOG"));
        }
        try(var jar=new JarFile(args[1])) {
            // Hull clipping still uses displaced gl_FragCoord.z, not the old flat surface depth.
            var entry=jar.getJarEntry("com/maxenonyme/createsubmarine/submarine/compat/sodium/WaterOcclusionPatch.class");
            String constants=new String(jar.getInputStream(entry).readAllBytes(),java.nio.charset.StandardCharsets.ISO_8859_1);
            check(constants.contains("gl_FragCoord.z") && constants.contains("SableCloseSampler")
                    && constants.contains("SableFarSampler") && constants.contains("discard"),"Deep Seas hull-depth clipping changed");
        }
        System.out.println("Wave travel, amplitude, shoreline, islands, ponds, water levels, Sodium targets and Deep Seas clipping verified");
    }
}
