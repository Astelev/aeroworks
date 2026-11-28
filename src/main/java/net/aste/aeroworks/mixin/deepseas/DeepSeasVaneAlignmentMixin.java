package net.aste.aeroworks.mixin.deepseas;

import dev.ryanhcode.sable.companion.SableCompanion;
import net.aste.aeroworks.compat.deepseas.DeepSeasWind;
import net.aste.aeroworks.compat.deepseas.SailHeadingMath;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="com.maxenonyme.highseas.client.WindVaneGoggle",remap=false)
public abstract class DeepSeasVaneAlignmentMixin {
    @Inject(method="alignment",at=@At("HEAD"),cancellable=true,require=1)
    private static void aeroworks$shipAlignment(Level level,BlockPos pos,CallbackInfoReturnable<Double> cir){
        if(level==null)return;
        var body=SableCompanion.INSTANCE.getContaining(level,pos);if(body==null)return;
        var heading=new Vector3d();if(!DeepSeasWind.clientHeading(level,body.getUniqueId(),heading))return;
        var p=body.logicalPose().transformPosition(new Vector3d(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5));
        var wind=DeepSeasWind.at(level,p.x,p.y,p.z);
        cir.setReturnValue(SailHeadingMath.alignment(heading.x,heading.z,wind.x,wind.z));
    }
}
