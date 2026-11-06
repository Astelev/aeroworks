package net.aste.aeroworks.client.waves;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Visual preference only. Physical/profile values live in waves-common and are synchronized. */
public final class WaveConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    static {
        var b=new ModConfigSpec.Builder();
        ENABLED=b.comment("Render server-owned water waves. Physics is independent of this visual preference.",
                "Amplitude, wavelength, speed, direction and shore_fade moved to aste_aeroworks-waves-common.toml.")
                .define("enabled",true);
        SPEC=b.build();
    }
    private WaveConfig(){}
}
