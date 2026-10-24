package net.aste.aeroworks.mixin.failure;

import java.util.List;
import net.aste.aeroworks.failure.FailureAccess;
import net.aste.aeroworks.failure.FailureState;
import net.aste.aeroworks.failure.FailurePersistence;
import net.aste.aeroworks.failure.FailureSystem;
import net.aste.aeroworks.failure.FailureVisuals;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hide functional rotation, preserving Create's stored speed, source and network for repair. */
@Pseudo
@Mixin(targets="com.simibubi.create.content.kinetics.base.KineticBlockEntity",remap=false)
public abstract class KineticFailureMixin extends BlockEntity implements FailureAccess {
    @Unique private final FailureState aste_aeroworks$failure=new FailureState();
    @Unique private long aste_aeroworks$lastFailureCheck=Long.MIN_VALUE;
    @Shadow public abstract float getSpeed();
    protected KineticFailureMixin(BlockEntityType<?> type,BlockPos pos,BlockState state) { super(type,pos,state); }
    @Override public FailureState aste_aeroworks$failureState() { return aste_aeroworks$failure; }
    @Override public void aste_aeroworks$syncFailure() { setChanged();((SyncedFailureInvoker)this).aste_aeroworks$sendFailureData(); }
    @Inject(method="tick",at=@At("RETURN"),require=1)
    private void aste_aeroworks$checkFailure(CallbackInfo ci) {
        if(!(level instanceof ServerLevel server)) {
            if(level!=null && level.isClientSide && aste_aeroworks$failure.failed())FailureVisuals.tick(this);
            return;
        }
        // Stagger the one-second checks across positions instead of processing every machine together.
        long now=server.getGameTime();
        if(Math.floorMod(now+Integer.toUnsignedLong(worldPosition.hashCode()),20)!=0)return;
        if(aste_aeroworks$lastFailureCheck!=Long.MIN_VALUE && now>=aste_aeroworks$lastFailureCheck
                && now-aste_aeroworks$lastFailureCheck<20)return;
        aste_aeroworks$lastFailureCheck=now;
        FailureSystem.check(this,this,getSpeed());
    }
    @Inject(method="getSpeed",at=@At("HEAD"),cancellable=true,require=1)
    private void aste_aeroworks$stoppedSpeed(CallbackInfoReturnable<Float> cir) {
        if(aste_aeroworks$failure.failed() && FailureSystem.stopped(this))cir.setReturnValue(0f);
    }
    @Inject(method="write",at=@At("RETURN"),require=1)
    private void aste_aeroworks$writeFailure(CompoundTag tag,HolderLookup.Provider registries,boolean clientPacket,CallbackInfo ci) {
        FailurePersistence.write(tag,aste_aeroworks$failure);
    }
    @Inject(method="read",at=@At("RETURN"),require=1)
    private void aste_aeroworks$readFailure(CompoundTag tag,HolderLookup.Provider registries,boolean clientPacket,CallbackInfo ci) {
        // RETURN also covers Create's early return after moving/assembling a block entity.
        FailurePersistence.read(tag,aste_aeroworks$failure);
    }
    @Inject(method="addToGoggleTooltip",at=@At("HEAD"),cancellable=true,require=1)
    private void aste_aeroworks$failureTooltip(List<Component> tooltip,boolean sneaking,CallbackInfoReturnable<Boolean> cir) {
        if(!FailureSystem.stopped(this))return;
        tooltip.add(Component.translatable("aste_aeroworks.failure.failed").withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("aste_aeroworks.failure.repair_hint").withStyle(ChatFormatting.GRAY));
        cir.setReturnValue(true);
    }
}
