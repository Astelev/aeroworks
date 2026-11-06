package net.aste.aeroworks.waves;

import java.util.function.Consumer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** No client classes on the dedicated-server registration path. */
public final class WaveNetwork {
    public static final String REVISION="wave-spectrum-r4";
    private static Consumer<WaveStatePayload> receiver=p->{};
    private WaveNetwork(){}
    public static void installClient(Consumer<WaveStatePayload> handler){receiver=handler;}
    public static void register(RegisterPayloadHandlersEvent event){
        event.registrar("3").playToClient(WaveStatePayload.TYPE,WaveStatePayload.CODEC,(p,c)->receiver.accept(p));
    }
}
