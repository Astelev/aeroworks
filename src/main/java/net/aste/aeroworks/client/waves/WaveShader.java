package net.aste.aeroworks.client.waves;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import net.aste.aeroworks.Aeroworks;

public final class WaveShader {
    private static final String ANCHOR = "vec3 position = _vert_position + translation;";
    private static final String CODE = read();
    private static boolean warned;
    private WaveShader() {}
    private static String read() {
        try (var in = WaveShader.class.getResourceAsStream("/assets/aste_aeroworks/shaders/waves/displacement.glsl")) {
            if (in == null) throw new IOException("Missing wave shader");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) { throw new IllegalStateException(e); }
    }
    public static String patch(String source) {
        if (source.contains("aste_wavePosition")) return source;
        if (!source.contains(ANCHOR) || !source.contains("void main()")) {
            if (!warned) { warned = true; Aeroworks.LOGGER.warn("Sodium water waves disabled: unfamiliar terrain vertex shader"); }
            return source;
        }
        return source.replace("void main()", CODE + "\nvoid main()")
                .replace(ANCHOR, ANCHOR + "\n    position = aste_wavePosition(position, _vert_tex_diffuse_coord);");
    }
}
