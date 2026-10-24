package net.aste.aeroworks.client;

import dev.ryanhcode.sable.Sable;
import net.aste.aeroworks.failure.FailureSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Vector3d;

/** A small world-space smoke trail from each failed part, using existing synchronized failure NBT. */
public final class FailureSmoke {
    private static long budgetTick=Long.MIN_VALUE;
    private static int emitted;
    private static final Vector3d POSITION=new Vector3d();
    private FailureSmoke() {}
    public static void tick(BlockEntity entity) {
        var mc=Minecraft.getInstance();
        if(mc.level==null || entity.getLevel()!=mc.level || entity.isRemoved() || mc.isPaused()
                || !FailureSystem.stopped(entity))return;
        var mode=mc.options.particles().get();
        if(mode==ParticleStatus.MINIMAL)return;
        long now=mc.level.getGameTime();
        int interval=mode==ParticleStatus.DECREASED ? 16:8;
        // Vary the phase each emission window so the same parts do not always consume the budget first.
        long seed=entity.getBlockPos().asLong()^((now/interval)*0x9e3779b97f4a7c15L);
        int slot=(int)(net.aste.aeroworks.wind.ZonalWindModel.unitHash(seed)*interval);
        if(Math.floorMod(now,interval)!=slot)return;
        if(budgetTick!=now) { budgetTick=now;emitted=0; }
        if(emitted>=2)return; // Shared cap: at most 40 new puffs/second on this client.
        var block=entity.getBlockPos();
        POSITION.set(block.getX()+.5,block.getY()+.7,block.getZ()+.5);
        var body=Sable.HELPER.getContainingClient(entity);
        if(body!=null && !body.isRemoved())body.renderPose(1).transformPosition(POSITION,POSITION);
        var camera=mc.gameRenderer.getMainCamera().getPosition();
        double dx=POSITION.x-camera.x,dy=POSITION.y-camera.y,dz=POSITION.z-camera.z;
        if(dx*dx+dy*dy+dz*dz>96*96)return;
        var random=mc.level.random;
        var particle=mc.particleEngine.createParticle(ParticleTypes.LARGE_SMOKE,
                POSITION.x+(random.nextDouble()-.5)*.25,POSITION.y,POSITION.z+(random.nextDouble()-.5)*.25,
                (random.nextDouble()-.5)*.01,.025,(random.nextDouble()-.5)*.01);
        if(particle!=null) {particle.scale(1.4f);emitted++;}
    }
}
