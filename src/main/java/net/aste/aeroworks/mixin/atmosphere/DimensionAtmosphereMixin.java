package net.aste.aeroworks.mixin.atmosphere;

import dev.ryanhcode.sable.physics.config.dimension_physics.BezierResourceFunction;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import net.aste.aeroworks.atmosphere.AtmosphereSettings;
import net.minecraft.world.level.Level;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Replace the height curve, preserving the dimension's base pressure (including vacuum). */
@Mixin(value = DimensionPhysicsData.class, remap = false)
public abstract class DimensionAtmosphereMixin {
    @Redirect(method = "getAirPressure", at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/physics/config/dimension_physics/BezierResourceFunction;evaluateFunction(D)D"),
            require = 1, expect = 1, allow = 1)
    private static double aste_aeroworks$heightDensity(BezierResourceFunction original, double height,
            Level level, Vector3dc position) {
        double density = AtmosphereSettings.densityOrOriginal(level, height);
        return Double.isNaN(density) ? original.evaluateFunction(height) : density;
    }
}
