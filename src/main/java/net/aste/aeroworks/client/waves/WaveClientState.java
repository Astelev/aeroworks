package net.aste.aeroworks.client.waves;

import net.aste.aeroworks.waves.WaveParameters;
import net.aste.aeroworks.waves.WaveStatePayload;
import net.minecraft.world.level.Level;

public final class WaveClientState {
    private static Level world;
    private static WaveParameters parameters=WaveParameters.CALM;
    private static String revision="waiting",diagnostics="waiting for server";
    private WaveClientState(){}
    public static void receive(Level level,WaveStatePayload payload){
        if(level==null||!level.dimension().location().equals(payload.dimension()))return;
        world=level;parameters=payload.parameters();revision=payload.revision();diagnostics=payload.diagnostics();
    }
    public static WaveParameters current(Level level){return level!=null&&world==level?parameters:WaveParameters.CALM;}
    public static String revision(Level level){return world==level?revision:"waiting";}
    public static String diagnostics(Level level){return world==level?diagnostics:"waiting for server";}
    public static void clear(Level old){if(world==old){world=null;parameters=WaveParameters.CALM;}}
}
