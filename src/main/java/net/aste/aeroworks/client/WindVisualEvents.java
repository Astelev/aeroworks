package net.aste.aeroworks.client;

import java.util.List;
import dev.ryanhcode.sable.Sable;
import net.aste.aeroworks.Aeroworks;
import net.aste.aeroworks.network.WindVisualPayload;
import net.aste.aeroworks.wind.WindCameraMotion;
import net.aste.aeroworks.wind.WindParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid=Aeroworks.MODID,value=Dist.CLIENT)
public final class WindVisualEvents {
    private static ClientLevel level;
    private static WindVisualPayload.Shake received, motion;
    private static List<WindVisualPayload.Flow> flows=List.of();
    private static long receivedAt, motionAt;
    private static double previousStrength,strength,particleCredit;
    private static int cursor;
    private static final WindCameraMotion.Result OFFSET=new WindCameraMotion.Result();
    private WindVisualEvents() {}
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(WindParticles.UPDRAFT.get(),UpdraftCloudParticle.Provider::new);
        event.registerSpriteSet(WindParticles.STALL.get(),StallWispParticle.Provider::new);
    }
    public static void receive(WindVisualPayload payload) {
        var world=Minecraft.getInstance().level;
        if(world==null || !world.dimension().location().equals(payload.dimension())) return;
        if(level!=world) reset(world);
        received=payload.shake(); flows=payload.flows(); receivedAt=world.getGameTime();
        if(received!=null) {
            if(motion==null || !motion.body().equals(received.body()) || motion.seed()!=received.seed()) {
                strength=previousStrength=0;
            }
            motion=received; motionAt=receivedAt;
        }
    }
    private static void reset(ClientLevel next) {
        level=next; received=motion=null; flows=List.of();
        previousStrength=strength=particleCredit=0;cursor=0;
    }
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        if(mc.level!=level) reset(mc.level);
        if(level==null||mc.player==null||mc.isPaused()) return;
        var body=Sable.HELPER.getTrackingOrVehicleSubLevel(mc.player);
        boolean onBody=body!=null && !body.isRemoved() && motion!=null && body.getUniqueId().equals(motion.body());
        if(!onBody || mc.player.isSpectator() || !WindVisualConfig.CAMERA_SHAKE.get()) {
            strength=previousStrength=0;
        } else {
            previousStrength=strength;
            double target=received!=null && level.getGameTime()-receivedAt<=20 ? received.strength():0;
            strength+=(target-strength)*.4;
        }
        if(level.getGameTime()-receivedAt>40) flows=List.of();
        if(!WindVisualConfig.UPDRAFT_CLOUDS.get() || flows.isEmpty() || mc.options.particles().get()==ParticleStatus.MINIMAL) {
            particleCredit=0; return;
        }
        double budget=WindVisualConfig.PARTICLES_PER_SECOND.get();
        if(mc.options.particles().get()==ParticleStatus.DECREASED) budget*=.5;
        particleCredit=Math.min(8,particleCredit+budget/20);
        while(particleCredit>=1) {
            particleCredit--;
            var flow=flows.get(Math.floorMod(cursor++,flows.size()));
            var camera=mc.gameRenderer.getMainCamera().getPosition();
            double dx=flow.x()-camera.x,dy=flow.y()-camera.y,dz=flow.z()-camera.z;
            if(dx*dx+dy*dy+dz*dz>512*512 || flow.strength()<.02) continue;
            var random=level.random;
            double angle=random.nextDouble()*Math.PI*2;
            double radius=Math.sqrt(random.nextDouble())*flow.radius()*.7;
            double x=flow.x()+Math.cos(angle)*radius, z=flow.z()+Math.sin(angle)*radius;
            double y=flow.y()+(random.nextDouble()-.5)*flow.radius()*.2;
            // Direct creation avoids vanilla's short-range ambient-particle spawn cutoff.
            mc.particleEngine.createParticle(WindParticles.UPDRAFT.get(),x,y,z,
                    flow.radius()*(.16+.10*random.nextDouble()),.015+.02*flow.strength(),.35+.3*flow.strength());
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void camera(ViewportEvent.ComputeCameraAngles event) {
        var mc=Minecraft.getInstance();
        if(mc.level!=level || level==null || motion==null || mc.player==null
                || event.getCamera().getEntity()!=mc.player || !WindVisualConfig.CAMERA_SHAKE.get()) return;
        double partial=event.getPartialTick();
        double amount=previousStrength+(strength-previousStrength)*partial;
        if(amount<.0001) return;
        double elapsed=motion.elapsed()+(level.getGameTime()-motionAt+partial)/20.0;
        WindCameraMotion.sample(motion.seed(),elapsed,motion.period(),amount*WindVisualConfig.SHAKE_STRENGTH.get(),OFFSET);
        event.setPitch(event.getPitch()+(float)OFFSET.pitch);
        event.setYaw(event.getYaw()+(float)OFFSET.yaw);
        event.setRoll(event.getRoll()+(float)OFFSET.roll);
    }
}
