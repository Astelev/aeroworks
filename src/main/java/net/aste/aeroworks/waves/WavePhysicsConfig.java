package net.aste.aeroworks.waves;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.event.config.ModConfigEvent;

public final class WavePhysicsConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue ENABLED,BUOYANCY;
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
        SPEC=b.build();
    }
    public static WaveParameters current(){return current;}
    public static void loading(ModConfigEvent.Loading e){update(e);}
    public static void reloading(ModConfigEvent.Reloading e){update(e);}
    private static void update(ModConfigEvent e){
        if(e.getConfig().getSpec()==SPEC) current=new WaveParameters(ENABLED.get(),BUOYANCY.get(),AMPLITUDE.get(),
                WAVELENGTH.get(),SPEED.get(),DIRECTION.get(),SHORE_FADE.get());
    }
    private WavePhysicsConfig(){}
}
