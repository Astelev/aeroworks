package net.aste.aeroworks.aerodynamics;

import net.aste.aeroworks.Config;
import net.neoforged.fml.event.config.ModConfigEvent;

/** Upgrade only the old default pair; retain individually tuned angles. */
public final class AerodynamicSettings {
    private AerodynamicSettings() {}
    public static void loading(ModConfigEvent.Loading event) {
        if(event.getConfig().getSpec()!=Config.SPEC)return;
        if(Config.ATTACHED_FLOW_ANGLE.get()==12.0 && Config.SEPARATED_FLOW_ANGLE.get()==25.0) {
            Config.ATTACHED_FLOW_ANGLE.set(30.0);
            Config.SEPARATED_FLOW_ANGLE.set(45.0);
            event.getConfig().save();
        }
    }
}
