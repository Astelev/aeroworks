package net.aste.aeroworks.client.rain;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class RainConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.IntValue RAY_INTERVAL;
    static {
        var b=new ModConfigSpec.Builder();
        ENABLED=b.comment("Optimize Simple Clouds 0.7.3 precipitation without reducing strip density or render distance.").define("enabled",true);
        RAY_INTERVAL=b.comment("Ticks between distant precipitation raycasts. Nearby rays (within 8 blocks of the camera) and changed angles update every tick. 1 disables raycast reuse.").defineInRange("distant_ray_interval",3,1,6);
        SPEC=b.build();
    }
    private RainConfig(){}
}
