package net.aste.aeroworks;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(Aeroworks.MODID)
public class Aeroworks {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "aste_aeroworks";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public Aeroworks(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        net.aste.aeroworks.wind.WindParticles.REGISTER.register(modEventBus);
        modEventBus.addListener(net.aste.aeroworks.network.WindVisualNetwork::register);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (ExampleMod) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modEventBus.addListener(net.aste.aeroworks.failure.FailureSettings::loading);
        modEventBus.addListener(net.aste.aeroworks.failure.FailureSettings::reloading);
        modEventBus.addListener(net.aste.aeroworks.atmosphere.PropellerSettings::loading);
        modEventBus.addListener(net.aste.aeroworks.atmosphere.PropellerSettings::reloading);
        modEventBus.addListener(net.aste.aeroworks.atmosphere.AtmosphereSettings::loading);
        modEventBus.addListener(net.aste.aeroworks.atmosphere.AtmosphereSettings::reloading);
        modEventBus.addListener(net.aste.aeroworks.wind.WindSettings::loading);
        modEventBus.addListener(net.aste.aeroworks.wind.WindSettings::reloading);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");

        if (Config.LOG_DIRT_BLOCK.getAsBoolean()) {
            LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));
        }

        LOGGER.info("{}{}", Config.MAGIC_NUMBER_INTRODUCTION.get(), Config.MAGIC_NUMBER.getAsInt());

        Config.ITEM_STRINGS.get().forEach((item) -> LOGGER.info("ITEM >> {}", item));
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }
}
