package net.aste.aeroworks.aerodynamics;

import com.aerofix.ldfix.AerofixConfig;

/** Loaded only when ldfix is installed. Values are sampled once per body/substep, never per sail. */
final class LiftPatchTuning {
    static double lift() {return AerofixConfig.LIFT_TUNING.get();}
    static double normalDrag() {return AerofixConfig.PARALLEL_DRAG_TUNING.get();}
    static double baseDrag() {return AerofixConfig.DIRECTIONLESS_DRAG_TUNING.get();}
    static double liftCap() {return AerofixConfig.LIFT_SOFTCAP.get();}
    static double normalCap() {return AerofixConfig.NORMAL_DRAG_SOFTCAP.get();}
    private LiftPatchTuning() {}
}
