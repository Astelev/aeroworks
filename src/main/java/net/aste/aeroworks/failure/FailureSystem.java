package net.aste.aeroworks.failure;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.aste.aeroworks.wind.WindWorlds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Vector3d;

/** Server tick only. No scan of loaded chunks or surfaces, no work inside a physics callback. */
public final class FailureSystem {
    private static final ThreadLocal<Vector3d> POSITION=ThreadLocal.withInitial(Vector3d::new);
    private FailureSystem() {}
    public static boolean stopped(Object blockEntity) {
        return blockEntity instanceof FailureAccess access && access.aste_aeroworks$failureState().failed()
                && FailureSettings.current().enabled();
    }
    public static void check(BlockEntity entity,FailureAccess access,float rpm) {
        if(!(entity.getLevel() instanceof ServerLevel level)||entity.isRemoved())return;
        var state=access.aste_aeroworks$failureState();
        if(state.graceTicks()>0) {
            state.elapse(20);entity.setChanged();return;
        }
        var settings=FailureSettings.current();
        if(!settings.enabled()||!state.canFail()||!settings.includes(entity.getBlockState())
                ||!Float.isFinite(rpm)||Math.abs(rpm)<.001)return;
        var pos=POSITION.get();
        pos.set(entity.getBlockPos().getX()+.5,entity.getBlockPos().getY()+.5,entity.getBlockPos().getZ()+.5);
        double speed=0;boolean turbulent=false;
        var body=Sable.HELPER.getContaining(level,entity.getBlockPos());
        if(body instanceof ServerSubLevel serverBody && !body.isRemoved()) {
            // Read Sable's cached world velocity; never request a native physics handle on the server tick.
            speed=serverBody.latestLinearVelocity.length();
            body.logicalPose().transformPosition(pos);
            turbulent=WindWorlds.isTurbulent(level,serverBody.getRuntimeId());
        }
        double probability=settings.risk().probability(rpm,speed,pos.y,turbulent,1);
        if(probability>0 && state.tryFail(probability,level.random.nextDouble()))access.aste_aeroworks$syncFailure();
    }
}
