package net.aste.aeroworks.failure;

import java.util.Set;
import java.util.stream.Collectors;
import net.aste.aeroworks.Aeroworks;
import net.aste.aeroworks.Config;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;

public final class FailureSettings {
    public record Snapshot(boolean enabled,Set<ResourceLocation> blocks,FailureRisk risk,int repairGraceTicks) {
        public boolean includes(BlockState state) { return blocks.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock())); }
    }
    private static volatile Snapshot current=new Snapshot(false,Set.of(),FailureRisk.DEFAULT,300);
    private FailureSettings() {}
    public static Snapshot current() { return current; }
    public static void loading(ModConfigEvent.Loading event) { reload(event); }
    public static void reloading(ModConfigEvent.Reloading event) { reload(event); }
    private static void reload(ModConfigEvent event) {
        if(!event.getConfig().getModId().equals(Aeroworks.MODID)||event.getConfig().getType()!=ModConfig.Type.COMMON)return;
        FailureRisk risk;
        try {
            risk=new FailureRisk(Config.FAILURE_CHANCE.get(),Config.FAILURE_REFERENCE_RPM.get(),Config.FAILURE_RPM_WEIGHT.get(),
                    Config.FAILURE_REFERENCE_SPEED.get(),Config.FAILURE_SPEED_WEIGHT.get(),Config.FAILURE_ALTITUDE_START.get(),
                    Config.FAILURE_ALTITUDE_SCALE.get(),Config.FAILURE_ALTITUDE_WEIGHT.get(),Config.FAILURE_TURBULENCE.get(),Config.FAILURE_MAX_MULTIPLIER.get());
        } catch(IllegalArgumentException error) {
            risk=current.risk();Aeroworks.LOGGER.warn("Invalid failure settings; retaining previous risk profile: {}",error.getMessage());
        }
        var blocks=Config.FAILURE_BLOCKS.get().stream().map(ResourceLocation::parse).collect(Collectors.toUnmodifiableSet());
        current=new Snapshot(Config.FAILURE_ENABLED.get(),blocks,risk,Config.FAILURE_REPAIR_GRACE.get()*20);
    }
}
