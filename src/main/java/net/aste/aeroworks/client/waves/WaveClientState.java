package net.aste.aeroworks.client.waves;

import net.aste.aeroworks.waves.WaveParameters;
import net.aste.aeroworks.waves.WaveStatePayload;
import net.minecraft.world.level.Level;

public final class WaveClientState {
    private static Level world;
    private static WaveParameters parameters=WaveParameters.CALM;
    private WaveClientState(){}
    public static void receive(Level level,WaveStatePayload payload){
        if(level==null||!level.dimension().location().equals(payload.dimension()))return;
        world=level;parameters=payload.parameters();
    }
    public static WaveParameters current(Level level){return level!=null&&world==level?parameters:WaveParameters.CALM;}
    public static void clear(Level old){if(world==old){world=null;parameters=WaveParameters.CALM;}}
}
