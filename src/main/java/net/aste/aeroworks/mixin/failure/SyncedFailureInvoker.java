package net.aste.aeroworks.mixin.failure;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

/** sendData is declared here, not in KineticBlockEntity: bind the accessor to its actual owner. */
@Pseudo
@Mixin(targets="com.simibubi.create.foundation.blockEntity.SyncedBlockEntity",remap=false)
public interface SyncedFailureInvoker {
    @Invoker(value="sendData",remap=false)
    void aste_aeroworks$sendFailureData();
}
