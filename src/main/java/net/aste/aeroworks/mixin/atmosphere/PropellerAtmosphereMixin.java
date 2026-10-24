package net.aste.aeroworks.mixin.atmosphere;

import dev.ryanhcode.sable.api.block.propeller.BlockEntityPropeller;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import net.aste.aeroworks.atmosphere.PropellerSettings;
import net.minecraft.world.level.Level;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Reuse Sable's already projected world position and pressure sample. No added world/block queries. */
@Mixin(value=BlockEntityPropeller.class,remap=false)
public interface PropellerAtmosphereMixin {
    @Redirect(method="getCurrentAirPressure",at=@At(value="INVOKE",
            target="Ldev/ryanhcode/sable/physics/config/dimension_physics/DimensionPhysicsData;getAirPressure(Lnet/minecraft/world/level/Level;Lorg/joml/Vector3dc;)D"),
            require=1,expect=1,allow=1)
    private double aste_aeroworks$propellerPressure(Level level,Vector3dc worldPosition) {
        double original=DimensionPhysicsData.getAirPressure(level,worldPosition);
        return PropellerSettings.pressure((BlockEntityPropeller)this,level,worldPosition.y(),original);
    }
}
