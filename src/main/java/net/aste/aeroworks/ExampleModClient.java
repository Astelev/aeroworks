package net.aste.aeroworks;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = Aeroworks.MODID, dist = Dist.CLIENT)
public class ExampleModClient {
    public ExampleModClient(ModContainer container, net.neoforged.bus.api.IEventBus modEventBus) {
        modEventBus.addListener(ExampleModClient::onClientSetup);
        modEventBus.addListener(net.aste.aeroworks.client.WindVisualEvents::registerParticles);
        net.aste.aeroworks.network.WindVisualNetwork.setReceiver(net.aste.aeroworks.client.WindVisualEvents::receive);
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT,
                net.aste.aeroworks.client.WindVisualConfig.SPEC,"aste_aeroworks-wind-client.toml");
        modEventBus.addListener(net.aste.aeroworks.client.CloudFogPostProcess::registerReloadListener);
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT,
                net.aste.aeroworks.client.CloudFogConfig.SPEC);
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    static void onClientSetup(FMLClientSetupEvent event) {
        // Some client setup code
        Aeroworks.LOGGER.info("HELLO FROM CLIENT SETUP");
        Aeroworks.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }
}
