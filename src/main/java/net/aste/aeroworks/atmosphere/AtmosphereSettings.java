package net.aste.aeroworks.atmosphere;

import java.util.Set;
import java.util.stream.Collectors;
import net.aste.aeroworks.Aeroworks;
import net.aste.aeroworks.Config;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;

/** Immutable snapshot for the physics path: no config reads, parsing or allocations per surface. */
public final class AtmosphereSettings {
    private record Settings(boolean enabled, Set<ResourceLocation> dimensions, AtmosphereProfile profile) {}
    private static volatile Settings current = new Settings(false, Set.of(), AtmosphereProfile.DEFAULT);

    private AtmosphereSettings() {}

    public static void loading(ModConfigEvent.Loading event) { reload(event); }
    public static void reloading(ModConfigEvent.Reloading event) { reload(event); }

    private static void reload(ModConfigEvent event) {
        if (!event.getConfig().getModId().equals(Aeroworks.MODID)
                || event.getConfig().getType() != ModConfig.Type.COMMON) return;
        AtmosphereProfile profile;
        try {
            profile = new AtmosphereProfile(Config.ATMOSPHERE_REFERENCE_HEIGHT.get(),
                    Config.ATMOSPHERE_SCALE_HEIGHT.get(), Config.ATMOSPHERE_FADE_START.get(),
                    Config.ATMOSPHERE_CEILING.get());
        } catch (IllegalArgumentException error) {
            profile = current.profile();
            Aeroworks.LOGGER.warn("Invalid atmosphere heights; retaining previous density profile: {}", error.getMessage());
        }
        var dimensions = Config.ATMOSPHERE_DIMENSIONS.get().stream()
                .map(ResourceLocation::parse).collect(Collectors.toUnmodifiableSet());
        current = new Settings(Config.ATMOSPHERE_ENABLED.get(), dimensions, profile);
    }

    public static boolean applies(Level level) {
        var settings=current;
        return settings.enabled() && settings.dimensions().contains(level.dimension().location());
    }

    /** NaN means delegate to Sable's original curve, including custom dimension data. */
    public static double densityOrOriginal(Level level, double height) {
        var settings = current;
        return settings.enabled() && settings.dimensions().contains(level.dimension().location())
                ? settings.profile().density(height) : Double.NaN;
    }
}
