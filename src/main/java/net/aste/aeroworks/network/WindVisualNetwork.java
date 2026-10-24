package net.aste.aeroworks.network;

import java.util.function.Consumer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Client implementation is installed only by the client entry point; safe on dedicated servers. */
public final class WindVisualNetwork {
    private static Consumer<WindVisualPayload> receiver=payload -> {};
    private WindVisualNetwork() {}
    public static void setReceiver(Consumer<WindVisualPayload> clientReceiver) { receiver=clientReceiver; }
    public static void register(RegisterPayloadHandlersEvent event) {
        // MAIN is the default handler thread in NeoForge 1.21.1.
        event.registrar("1").playToClient(WindVisualPayload.TYPE,WindVisualPayload.CODEC,(payload,context)->receiver.accept(payload));
    }
}
