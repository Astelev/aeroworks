package net.aste.aeroworks.client.waves;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import net.aste.aeroworks.Aeroworks;

public final class WaveShader {
    private static final Pattern POSITION = Pattern.compile("\\bvec3\\s+position\\s*=\\s*(?:\\(\\s*)*_vert_position\\s*(?:\\)\\s*)*\\+\\s*(?:\\(\\s*)*translation\\s*(?:\\)\\s*)*;");
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)");
    private static final String CODE = read();
    private static boolean warned;
    public static volatile boolean attached;
    public static volatile boolean compileHookSeen;
    private WaveShader() {}
    private static String read() {
        try (var in = WaveShader.class.getResourceAsStream("/assets/aste_aeroworks/shaders/waves/displacement.glsl")) {
            if (in == null) throw new IOException("Missing wave shader");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) { throw new IllegalStateException(e); }
    }
    public static String compiledSource(String source) {
        compileHookSeen = true;
        // Structural selection on final sources; foam runs before native fog and hull clipping.
        if (!source.contains("_vert_position") || !source.contains("u_RegionOffset") || !source.contains("gl_Position"))
            return WaveFoamShader.patch(source);
        return patch(source);
    }
    public static String patch(String source) {
        if (source.contains("aste_wavePosition")) { attached = true; return source; }
        var position = POSITION.matcher(source);
        var main = MAIN.matcher(source);
        if (!position.find() || !main.find()) {
            if (!warned) { warned = true; Aeroworks.LOGGER.warn("Sodium water waves disabled: unfamiliar terrain vertex shader"); }
            return source;
        }
        if (!attached) Aeroworks.LOGGER.info("AeroWorks waves: attached to final Sodium terrain vertex shader before compilation");
        attached = true;
        String displaced=source.substring(0,position.end())
                + "\n    position = aste_wavePosition(position, _vert_tex_diffuse_coord);"
                + source.substring(position.end());
        main=MAIN.matcher(displaced);
        if (!main.find()) throw new IllegalStateException("Sodium main function disappeared");
        return displaced.substring(0,main.start()) + CODE + "\n" + displaced.substring(main.start());
    }
}
