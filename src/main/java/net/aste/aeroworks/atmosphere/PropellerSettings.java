package net.aste.aeroworks.atmosphere;

import dev.ryanhcode.sable.api.block.propeller.BlockEntityPropeller;
import net.aste.aeroworks.Aeroworks;
import net.aste.aeroworks.Config;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;

/** Cached settings and an explicit block tag: sharing Sable's propulsion API does not classify a jet as a propeller. */
public final class PropellerSettings {
    public static final TagKey<Block> AFFECTED=TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(Aeroworks.MODID,"altitude_sensitive_propellers"));
    private record Settings(boolean enabled,PropellerProfile profile) {}
    private static volatile Settings current=new Settings(false,PropellerProfile.DEFAULT);
    private PropellerSettings() {}
    public static void loading(ModConfigEvent.Loading event) { reload(event); }
    public static void reloading(ModConfigEvent.Reloading event) { reload(event); }
    private static void reload(ModConfigEvent event) {
        if(!event.getConfig().getModId().equals(Aeroworks.MODID)||event.getConfig().getType()!=ModConfig.Type.COMMON) return;
        PropellerProfile profile;
        try {
            profile=new PropellerProfile(Config.PROPELLER_FADE_START.get(),Config.PROPELLER_SCALE_HEIGHT.get());
        } catch(IllegalArgumentException error) {
            profile=current.profile();
            Aeroworks.LOGGER.warn("Invalid propeller altitude settings; retaining previous profile: {}",error.getMessage());
        }
        current=new Settings(Config.PROPELLER_ALTITUDE_ENABLED.get(),profile);
    }
    public static double pressure(BlockEntityPropeller propeller,Level level,double worldY,double original) {
        var settings=current;
        if(!settings.enabled() || original==0 || !AtmosphereSettings.applies(level)
                || !(propeller instanceof BlockEntity entity) || !entity.getBlockState().is(AFFECTED)) return original;
        return original*settings.profile().efficiency(worldY);
    }
}
