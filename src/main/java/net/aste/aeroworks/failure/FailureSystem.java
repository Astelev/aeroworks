package net.aste.aeroworks.failure;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.aste.aeroworks.wind.WindWorlds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Vector3d;

/** Cheap work/occupancy accounting per tick; world sampling, risk math and RNG only per active second or start. */
public final class FailureSystem {
    private static final ThreadLocal<Vector3d> POSITION=ThreadLocal.withInitial(Vector3d::new);
    private FailureSystem() {}
    public static boolean stopped(Object blockEntity) {
        return blockEntity instanceof FailureAccess access && access.aste_aeroworks$failureState().failed()
                && FailureSettings.current().enabled();
    }
    public static void tick(BlockEntity entity,FailureAccess access,FailureWork work) {
        if(!(entity.getLevel() instanceof ServerLevel level)||entity.isRemoved())return;
        var state=access.aste_aeroworks$failureState();
        boolean protectedNow=state.graceTicks()>0;
        if(protectedNow) {
            state.elapse(1);
            if(state.graceTicks()==0||level.getGameTime()%20==0)entity.setChanged();
        }
        var settings=FailureSettings.current();
        var profile=settings.profile(entity.getBlockState());
        if(!settings.enabled()||profile==null) {work.reset();return;}
        float rpm=access.aste_aeroworks$failureRPM();
        work.use(profile);
        boolean started=work.observe(rpm,profile.countReversals());
        if(protectedNow) {work.discardExposure();return;}
        if(!state.canFail()) {work.discardExposure();return;}
        // An idle tick updates the transition detector but keeps the fraction of eligible work already done.
        if(!work.running())return;
        var body=Sable.HELPER.getContaining(level,entity.getBlockPos());
        if(!(body instanceof ServerSubLevel serverBody) || body.isRemoved()
                || !FailureOccupancy.hasPlayer(level,serverBody)) {work.discardExposure();return;}
        work.enter(body.getUniqueId());
        work.accumulate(rpm);
        boolean startRoll=started && profile.chancePerStart()>0;
        if(!startRoll && !work.due())return;
        var pos=POSITION.get();
        pos.set(entity.getBlockPos().getX()+.5,entity.getBlockPos().getY()+.5,entity.getBlockPos().getZ()+.5);
        double speed=serverBody.latestLinearVelocity.length();
        body.logicalPose().transformPosition(pos);
        boolean turbulent=WindWorlds.isTurbulent(level,serverBody.getRuntimeId());
        double probability=profile.probability(work.averageRPM(),rpm,speed,pos.y,turbulent,work.seconds(),startRoll);
        work.clearWindow();
        if(probability>0 && state.tryFail(probability,level.random.nextDouble()))access.aste_aeroworks$syncFailure();
    }
}
