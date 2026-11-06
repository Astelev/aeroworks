package net.aste.aeroworks.client.waves;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class WaveConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.DoubleValue AMPLITUDE, WAVELENGTH, SPEED, DIRECTION, SHORE_FADE;
    static {
        var b = new ModConfigSpec.Builder();
        ENABLED = b.comment("Visual waves on large open water. Sodium terrain renderer; shader packs are not modified.")
                .define("enabled", true);
        AMPLITUDE = b.comment("Maximum vertical displacement in blocks (crest to trough is twice this).")
                .defineInRange("amplitude", .5, 0, 2);
        WAVELENGTH = b.comment("Distance between crests, in blocks.").defineInRange("wavelength", 32.0, 8, 256);
        SPEED = b.comment("Wave travel speed, blocks per second.").defineInRange("speed", 4.0, 0, 32);
        DIRECTION = b.comment("Direction in degrees: 0 = +X, 90 = +Z.").defineInRange("direction", 35.0, -360, 360);
        SHORE_FADE = b.comment("Shore fade distance in blocks. Shallow water and small ponds stay flat.")
                .defineInRange("shore_fade", 16.0, 4, 64);
        SPEC = b.build();
    }
    private WaveConfig() {}
}
