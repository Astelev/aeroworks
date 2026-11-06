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
    private static JarFile sodium(Path path)throws Exception {
        var jar=new JarFile(path.toFile());
        if(jar.getJarEntry("net/caffeinemc/mods/sodium/client/gl/shader/ShaderLoader.class")!=null) return jar;
        System.out.println("Inspecting Sodium archive "+path);
        var entries=jar.stream().filter(e->e.getName().endsWith(".jar")).toList();
        for(var e:entries) {
            Path nested=Files.createTempFile("sodium-contract-",".jar");
            try(var in=jar.getInputStream(e)) { Files.copy(in,nested,StandardCopyOption.REPLACE_EXISTING); }
            var candidate=new JarFile(nested.toFile());
            if(candidate.getJarEntry("net/caffeinemc/mods/sodium/client/gl/shader/ShaderLoader.class")!=null) {
                System.out.println("Sodium terrain renderer is bundled in "+e.getName());
                jar.close(); return candidate;
            }
            candidate.close(); Files.delete(nested);
        }
        jar.stream().filter(e->e.getName().contains("Shader")||e.getName().endsWith(".jar")).forEach(e->System.out.println(e.getName()));
        throw new AssertionError("Sodium terrain renderer class not found");
    }
    private static void check(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
    private static String veilFormat(String source,Path sable)throws Exception {
        byte[] veil;
        try(var jar=new JarFile(sable.toFile())) {
            veil=jar.getInputStream(jar.getJarEntry("META-INF/jarjar/veil-neoforge-1.21.1-4.3.2.jar")).readAllBytes();
        }
        Path parser=Files.createTempFile("wave-veil-parser-",".jar");
        boolean found=false;
        try(var nested=new java.util.jar.JarInputStream(new java.io.ByteArrayInputStream(veil))) {
            java.util.jar.JarEntry e;
            while((e=nested.getNextJarEntry())!=null) if(e.getName().endsWith("glsl-processor-0.2.3.jar")) {
                Files.write(parser,nested.readAllBytes()); found=true; break;
            }
        }
        check(found,"Exact Veil GLSL processor missing");
        try(var loader=new java.net.URLClassLoader(new java.net.URL[]{parser.toUri().toURL()},WaveTest.class.getClassLoader())) {
            var type=loader.loadClass("io.github.ocelot.glslprocessor.api.GlslParser");
            var tree=type.getMethod("preprocessParse",String.class,java.util.Map.class).invoke(null,source,java.util.Map.of());
            return (String)tree.getClass().getMethod("toSourceString").invoke(tree);
        } finally { Files.delete(parser); }
    }
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
        float[] h=new float[size*size]; Arrays.fill(h,63f);
        float[] field=ShoreField.build(h,size,4,16);
        check(field[(20*size+20)*2+1]==1,"Open water has no waves");
        check(field[(1*size+20)*2+1]==0,"Map/shore margin moves");
        h[20*size+20]=ShoreField.DRY;
        field=ShoreField.build(h,size,4,16);
        check(field[(20*size+20)*2+1]==0 && field[(20*size+21)*2+1]==0,"Island shore moves");
        Arrays.fill(h,ShoreField.DRY);
        for(int z=18;z<=22;z++) for(int x=18;x<=22;x++) h[z*size+x]=63f;
        field=ShoreField.build(h,size,4,16);
        check(field[(20*size+20)*2+1]==0,"Small pond moves");
        Arrays.fill(h,63f);
        for(int z=0;z<size;z++) for(int x=21;x<size;x++) h[z*size+x]=70f;
        field=ShoreField.build(h,size,4,16);
        check(field[(20*size+20)*2+1]==0,"Different water levels blend together");
        try(var jar=sodium(Path.of(args[0]))) {
            // Pin the exact final-source upload invocation, not merely a loader method that may be bypassed.
            var shaderClass=new ClassNode();
            try(var in=jar.getInputStream(jar.getJarEntry("net/caffeinemc/mods/sodium/client/gl/shader/GlShader.class"))) {
                new ClassReader(in.readAllBytes()).accept(shaderClass,0);
            }
            int uploads=0;
            for(var m:shaderClass.methods) if(m.name.equals("<init>")) for(var i:m.instructions)
                if(i instanceof org.objectweb.asm.tree.MethodInsnNode c
                        && c.owner.equals("net/caffeinemc/mods/sodium/client/gl/shader/ShaderWorkarounds")
                        && c.name.equals("safeShaderSource") && c.desc.equals("(ILjava/lang/String;)V")) uploads++;
            check(uploads==1,"Final-source upload hook changed: "+uploads);
            String pass="(Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;)V";
            target(jar,"render/chunk/ShaderChunkRenderer","begin",pass);
            target(jar,"render/chunk/ShaderChunkRenderer","end",pass);
            String vertex=read(jar,"assets/sodium/shaders/blocks/block_layer_opaque.vsh");
            String patched=WaveShader.compiledSource(vertex);
            check(!patched.equals(vertex),"Sodium shader was not patched");
            check(WaveShader.patch(patched).equals(patched),"Wave shader patched twice");
            String fragment=read(jar,"assets/sodium/shaders/blocks/block_layer_opaque.fsh");
            check(WaveShader.compiledSource(fragment).equals(fragment),"Fragment source changed");
            String hullFragment=com.maxenonyme.createsubmarine.submarine.compat.sodium.WaterOcclusionPatch.apply(fragment,false);
            check(hullFragment.contains("SableCloseSampler") && hullFragment.contains("discard"),"Deep Seas hull clipping not inserted");
            check(WaveShader.compiledSource(hullFragment).equals(hullFragment),"Deep Seas hull fragment source changed");
            check(WaveShader.compiledSource("#version 330\nvoid main(){gl_Position=vec4(0);}")
                    .equals("#version 330\nvoid main(){gl_Position=vec4(0);}"),"Unrelated vertex shader changed");
            Files.createDirectories(Path.of("build/wave-test"));
            Files.writeString(Path.of("build/wave-test/terrain.vsh"),expand(jar,patched).replace("#version 330 core","#version 330 core\n#define USE_VERTEX_COMPRESSION\n#define USE_FOG"));
            // The final source is already expanded/reformatted by Veil. Test without resource names or loader calls.
            String processed=expand(jar,vertex).replace("vec3 position = _vert_position + translation;",
                    "vec3\n position= ( _vert_position+\ntranslation ) ;").replace("void main()","void main ( void )");
            String processedPatched=WaveShader.compiledSource(processed);
            check(!processedPatched.equals(processed),"Processed vertex source was not patched");
            check(WaveShader.compiledSource(processedPatched).equals(processedPatched),"Compiled source patched twice");
            Files.writeString(Path.of("build/wave-test/terrain-processed.vsh"),processedPatched.replace("#version 330 core",
                    "#version 330 core\n#define USE_VERTEX_COMPRESSION\n#define USE_FOG"));
            String veil=veilFormat(expand(jar,vertex).replace("#version 330 core",
                    "#version 330 core\n#define USE_VERTEX_COMPRESSION\n#define USE_FOG"),Path.of(args[2]));
            String veilPatched=WaveShader.compiledSource(veil);
            check(!veilPatched.equals(veil),"Actual Veil-formatted terrain shader was not patched");
            Files.writeString(Path.of("build/wave-test/terrain-veil.vsh"),veilPatched);
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
