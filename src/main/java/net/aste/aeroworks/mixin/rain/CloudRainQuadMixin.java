package net.aste.aeroworks.mixin.rain;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.aste.aeroworks.client.rain.*;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.Function;

@Pseudo
@Mixin(targets="dev.nonamecrackers2.simpleclouds.client.renderer.rain.PrecipitationQuad",remap=false)
public abstract class CloudRainQuadMixin {
    @Shadow @Final private Vector3f position;
    @Shadow @Final private net.minecraft.core.BlockPos blockPos;
    @Shadow @Final private int lifeSpan;
    @Shadow @Final private float initialWidth;
    @Shadow @Final private Function<ClipContext,BlockHitResult> raycaster;
    @Shadow private float length,xRot,yRot,widthO,width;
    @Shadow private int tickCount;
    @Unique private int aste_aeroworks$lastRay;
    @Unique private float aste_aeroworks$lastX,aste_aeroworks$lastY;
    @Unique private BlockHitResult aste_aeroworks$hit;
    @Unique private Vec3 aste_aeroworks$start,aste_aeroworks$end;
    @Unique private final Quaternionf aste_aeroworks$rotation=new Quaternionf();
    @Unique private final Vector3f aste_aeroworks$camera=new Vector3f();

    @Inject(method="tick",at=@At("HEAD"),cancellable=true,require=1)
    private void aste_aeroworks$cachedTick(CallbackInfo ci){
        if(!RainConfig.ENABLED.get())return;
        tickCount++;
        var camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        boolean changed=xRot!=aste_aeroworks$lastX||yRot!=aste_aeroworks$lastY;
        boolean near=aste_aeroworks$start!=null&&RainRaySchedule.near(aste_aeroworks$start.x,aste_aeroworks$start.z,
                aste_aeroworks$end.x,aste_aeroworks$end.z,camera.x,camera.z);
        if(RainRaySchedule.refresh(tickCount,aste_aeroworks$lastRay,RainConfig.RAY_INTERVAL.get(),aste_aeroworks$hit!=null,changed,near)){
            boolean first=aste_aeroworks$hit==null;
            aste_aeroworks$start=new Vec3(position);
            float yaw=-yRot,pitch=xRot-1.5707964F,cos=Mth.cos(pitch);
            aste_aeroworks$end=new Vec3(Mth.sin(yaw)*cos,Mth.sin(pitch),Mth.cos(yaw)*cos).scale(32).add(aste_aeroworks$start);
            aste_aeroworks$hit=raycaster.apply(new ClipContext(aste_aeroworks$start,aste_aeroworks$end,
                    ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,CollisionContext.empty()));
            length=(float)aste_aeroworks$start.distanceTo(aste_aeroworks$hit.getLocation());
            aste_aeroworks$lastRay=tickCount-(first?Math.floorMod(blockPos.asLong(),RainConfig.RAY_INTERVAL.get()):0);aste_aeroworks$lastX=xRot;aste_aeroworks$lastY=yRot;
        }
        widthO=width;
        width=initialWidth*Math.min(1F,tickCount<lifeSpan-20?tickCount/20F:(lifeSpan-tickCount)/20F);
        ci.cancel();
    }
    @WrapOperation(method="render",at=@At(value="NEW",target="()Lorg/joml/Quaternionf;"),require=1,allow=1)
    private Quaternionf aste_aeroworks$rotation(Operation<Quaternionf> original){
        return RainConfig.ENABLED.get()?aste_aeroworks$rotation.identity():original.call();
    }
    @WrapOperation(method="render",at=@At(value="NEW",target="(FFF)Lorg/joml/Vector3f;"),require=1,allow=1)
    private Vector3f aste_aeroworks$camera(float x,float y,float z,Operation<Vector3f> original){
        return RainConfig.ENABLED.get()?aste_aeroworks$camera.set(x,y,z):original.call(x,y,z);
    }
}
