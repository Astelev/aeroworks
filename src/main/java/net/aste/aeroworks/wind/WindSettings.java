package net.aste.aeroworks.wind;

import net.aste.aeroworks.Aeroworks;
import net.aste.aeroworks.Config;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;

public final class WindSettings {
    public record Snapshot(boolean enabled, boolean dynamic, double constantX, double constantZ, WindParameters field) {}
    private static volatile Snapshot current = new Snapshot(false, true, 8, 0, WindParameters.DEFAULT);
    private WindSettings() {}
    public static Snapshot current() { return current; }
    public static void loading(ModConfigEvent.Loading event) { reload(event); }
    public static void reloading(ModConfigEvent.Reloading event) { reload(event); }
    private static void reload(ModConfigEvent event) {
        if (!event.getConfig().getModId().equals(Aeroworks.MODID)
                || event.getConfig().getType() != ModConfig.Type.COMMON) return;
        WindParameters p;
        try {
            p = new WindParameters(Config.WIND_ZONE_SIZE.get(), Config.WIND_SURFACE_HEIGHT.get(),
                    Config.WIND_MAXIMUM_HEIGHT.get(), Config.WIND_SURFACE_CLEAR.get(), Config.WIND_SURFACE_RAIN.get(),
                    Config.WIND_SURFACE_STORM.get(), Config.WIND_ALOFT_CLEAR.get(), Config.WIND_ALOFT_RAIN.get(),
                    Config.WIND_ALOFT_STORM.get(), Config.WIND_FALLBACK_PERIOD.get(), Config.WIND_WEATHER_PERIOD_MULTIPLIER.get(),
                    Config.WIND_TRANSITION_SECONDS.get(), Config.WIND_CLOUD_DEVIATION.get());
        } catch (IllegalArgumentException error) {
            p = current.field();
            Aeroworks.LOGGER.warn("Invalid wind field settings; retaining previous parameters: {}", error.getMessage());
        }
        current = new Snapshot(Config.WIND_ENABLED.get(), Config.WIND_DYNAMIC.get(),
                Config.WIND_X.get(), Config.WIND_Z.get(), p);
    }
}
