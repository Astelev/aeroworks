package net.aste.aeroworks.compat;

/** A specialised provider reports whether its last call selected its native water model.
 * Implementations must reset this per invocation and keep the result physics-thread local. */
public interface NativeWaterSurface {
    boolean aeroworks$usedWater();
}
