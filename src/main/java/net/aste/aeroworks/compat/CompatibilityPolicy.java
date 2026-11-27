package net.aste.aeroworks.compat;

/** Independent decisions: an absent optional mod never contributes a mixin or wave owner. */
public final class CompatibilityPolicy {
    private CompatibilityPolicy() {}
    public static boolean apply(String name, boolean atmospheric, boolean deepSeas) {
        if (name.contains(".atmospheric.")) return atmospheric;
        if (name.contains(".deepseas.") && !deepSeas) return false;
        if (atmospheric && (name.contains(".waves.") || name.endsWith(".WaveShaderMixin")
                || name.endsWith(".WaveRendererMixin") || name.endsWith(".DeepSeasWaveBuoyancyMixin")))
            return false;
        return true;
    }
}
