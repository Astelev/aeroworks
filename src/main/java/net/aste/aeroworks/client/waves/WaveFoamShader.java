package net.aste.aeroworks.client.waves;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.regex.Pattern;

/** Tint before native fog and preserve Deep Seas' fragment-depth discard verbatim. */
public final class WaveFoamShader {
    private static final Pattern COLOR=Pattern.compile("\\bcolor\\s*\\*=\\s*v_Color\\s*;");
    private static final Pattern MAIN=Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)");
    private static final String CODE=read();
    private WaveFoamShader(){}
    private static String read(){
        try(var in=WaveFoamShader.class.getResourceAsStream("/assets/aste_aeroworks/shaders/waves/foam.glsl")){
            if(in==null)throw new IOException("Missing foam shader");return new String(in.readAllBytes(),StandardCharsets.UTF_8);
        }catch(IOException e){throw new IllegalStateException(e);}
    }
    public static String patch(String source){
        if(source.contains("aste_applyFoam")||source.contains("gl_Position")||!source.contains("u_BlockTex")||!source.contains("v_Material"))return source;
        var color=COLOR.matcher(source);if(!color.find())return source;
        String modified=source.substring(0,color.end())+"\n    aste_applyFoam(color);"+source.substring(color.end());
        var main=MAIN.matcher(modified);if(!main.find())return source;
        return modified.substring(0,main.start())+CODE+"\n"+modified.substring(main.start());
    }
}
