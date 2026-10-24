package net.aste.aeroworks.failure;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.aste.aeroworks.Aeroworks;
import net.aste.aeroworks.Config;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;

public final class FailureSettings {
    public record Snapshot(boolean enabled,Set<ResourceLocation> blocks,FailureRisk risk,int repairGraceTicks,
            Map<Block,FailureBlockProfile> byBlock,Map<String,FailureBlockProfile> byName) {
        public boolean includes(BlockState state) {return byBlock.containsKey(state.getBlock());}
        public FailureBlockProfile profile(BlockState state) {return byBlock.get(state.getBlock());}
    }
    private static volatile Snapshot current=new Snapshot(false,Set.of(),FailureRisk.DEFAULT,300,Map.of(),Map.of());
    private FailureSettings() {}
    public static Snapshot current() {return current;}
    public static void loading(ModConfigEvent.Loading event) {reload(event);}
    public static void reloading(ModConfigEvent.Reloading event) {reload(event);}
    private static void reload(ModConfigEvent event) {
        if(!event.getConfig().getModId().equals(Aeroworks.MODID)||event.getConfig().getType()!=ModConfig.Type.COMMON)return;
        FailureRisk risk;
        try {
            risk=new FailureRisk(Config.FAILURE_CHANCE.get(),Config.FAILURE_REFERENCE_RPM.get(),Config.FAILURE_RPM_WEIGHT.get(),
                    Config.FAILURE_REFERENCE_SPEED.get(),Config.FAILURE_SPEED_WEIGHT.get(),Config.FAILURE_ALTITUDE_START.get(),
                    Config.FAILURE_ALTITUDE_SCALE.get(),Config.FAILURE_ALTITUDE_WEIGHT.get(),Config.FAILURE_TURBULENCE.get(),Config.FAILURE_MAX_MULTIPLIER.get());
        } catch(IllegalArgumentException error) {
            risk=current.risk();Aeroworks.LOGGER.warn("Invalid failure defaults; retaining previous risk profile: {}",error.getMessage());
        }
        int grace=Config.FAILURE_REPAIR_GRACE.get()*20;
        var blocks=Config.FAILURE_BLOCKS.get().stream().map(ResourceLocation::parse).collect(Collectors.toUnmodifiableSet());
        var configured=Config.FAILURE_BLOCK_PROFILES.get();
        var entries=FailureBlockProfile.completeEntries(java.util.List.copyOf(configured),java.util.List.copyOf(Config.FAILURE_BLOCKS.get()),risk);
        boolean added=entries.size()!=configured.size();
        var profiles=new HashMap<String,FailureBlockProfile>();
        for(var text:entries) {
            String id=FailureBlockProfile.idOf(text);
            if(id==null) {Aeroworks.LOGGER.warn("Invalid block failure profile: {}",text);continue;}
            if(profiles.containsKey(id)) {Aeroworks.LOGGER.warn("Duplicate failure profile for {}; keeping first entry",id);continue;}
            try {
                profiles.put(id,FailureBlockProfile.parse(text,risk,grace).profile());
            } catch(RuntimeException error) {
                profiles.put(id,current.byName().getOrDefault(id,new FailureBlockProfile(risk,0,grace,true)));
                Aeroworks.LOGGER.warn("Invalid failure profile for {}; retaining previous/default parameters: {}",id,error.getMessage());
            }
        }
        var byBlock=new HashMap<Block,FailureBlockProfile>();
        for(var id:blocks) {
            var profile=profiles.getOrDefault(id.toString(),new FailureBlockProfile(risk,0,grace,true));
            BuiltInRegistries.BLOCK.getOptional(id).ifPresent(block->byBlock.put(block,profile));
        }
        current=new Snapshot(Config.FAILURE_ENABLED.get(),blocks,risk,grace,Map.copyOf(byBlock),Map.copyOf(profiles));
        if(added) {
            // Idempotent: the reloading event caused by save sees every generated entry already present.
            Config.FAILURE_BLOCK_PROFILES.set(entries);
            var loaded=event.getConfig().getLoadedConfig();
            if(loaded!=null)loaded.save();
        }
    }
}
