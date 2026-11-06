package net.aste.aeroworks.waves;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.event.config.ModConfigEvent;

public final class WavePhysicsConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue ENABLED,BUOYANCY,DYNAMIC;
    private static final ModConfigSpec.DoubleValue FULL_DEPTH,NOISE,NOISE_SCALE,NOISE_PERIOD,CREST_DISTORTION,CHOP_THRESHOLD,CHOP_FRACTION,CHOP_LENGTH,CHOP_SPEED;
    private static final ModConfigSpec.DoubleValue AMPLITUDE,WAVELENGTH,SPEED,DIRECTION,SHORE_FADE;
    private static volatile WaveParameters current=WaveParameters.CALM;
    static {
        var b=new ModConfigSpec.Builder();
        ENABLED=b.comment("Enable server-owned waves. Profile is synchronized to clients.").define("enabled",true);
        BUOYANCY=b.comment("Use wave height in Deep Seas' existing displaced-volume calculation.").define("buoyancy",true);
        AMPLITUDE=b.comment("Maximum vertical displacement in blocks.").defineInRange("amplitude",.5,0,2);
        WAVELENGTH=b.defineInRange("wavelength",32.0,8,256);
        SPEED=b.comment("Blocks per second.").defineInRange("speed",4.0,0,32);
        DIRECTION=b.comment("Degrees: 0 = +X, 90 = +Z.").defineInRange("direction",35.0,-360,360);
        SHORE_FADE=b.defineInRange("shore_fade",16.0,4,64);
        b.push("detail");
        DYNAMIC=b.comment("Wind/depth response, coherent noise and strong-wind chop. False restores the original sine.").define("enabled",true);
        FULL_DEPTH=b.comment("Depth in blocks at which waves reach full strength; uses the loaded chunk ocean-floor heightmap.").defineInRange("full_depth",16.0,3,128);
        NOISE=b.comment("Bounded main-wave amplitude and phase modulation; 0 disables this noise.").defineInRange("noise_strength",.2,0,.5);
        NOISE_SCALE=b.defineInRange("noise_scale",48.0,8,512);
        NOISE_PERIOD=b.comment("Seconds for the broad noise to change.").defineInRange("noise_period",24.0,2,300);
        CREST_DISTORTION=b.comment("Horizontal domain-warp displacement as a fraction of main wavelength. Bends crest lines independently of amplitude noise; 0 disables. Reuses the broad noise scale/period.").defineInRange("crest_distortion",.22,0,.35);
        CHOP_THRESHOLD=b.comment("Fraction of the configured local STORM wind speed; .65 means 65%, not metres/second.").defineInRange("chop_threshold",.65,0,.99);
        CHOP_FRACTION=b.comment("Small-wave amplitude as a fraction of the main amplitude.").defineInRange("chop_fraction",.35,0,.75);
        CHOP_LENGTH=b.comment("Small-wave wavelength as a fraction of main wavelength.").defineInRange("chop_length",.25,.125,.5);
        CHOP_SPEED=b.comment("Small-wave speed multiplier.").defineInRange("chop_speed",1.7,.25,4);
        b.pop();
        SPEC=b.build();
    }
    public static WaveParameters current(){return current;}
    public static void loading(ModConfigEvent.Loading e){update(e);}
    public static void reloading(ModConfigEvent.Reloading e){update(e);}
    private static void update(ModConfigEvent e){
        if(e.getConfig().getSpec()==SPEC) current=new WaveParameters(ENABLED.get(),BUOYANCY.get(),AMPLITUDE.get(),
                WAVELENGTH.get(),SPEED.get(),DIRECTION.get(),SHORE_FADE.get(),new WaveDetail(DYNAMIC.get(),FULL_DEPTH.get(),NOISE.get(),NOISE_SCALE.get(),NOISE_PERIOD.get(),CHOP_THRESHOLD.get(),CHOP_FRACTION.get(),CHOP_LENGTH.get(),CHOP_SPEED.get(),CREST_DISTORTION.get()));
    }
    private WavePhysicsConfig(){}
}
