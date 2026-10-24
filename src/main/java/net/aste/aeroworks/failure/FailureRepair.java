package net.aste.aeroworks.failure;

import net.aste.aeroworks.Aeroworks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid=Aeroworks.MODID)
public final class FailureRepair {
    private static final ResourceLocation CREATE_WRENCH=ResourceLocation.fromNamespaceAndPath("create","wrench");
    private static final TagKey<Item> WRENCHES=TagKey.create(Registries.ITEM,ResourceLocation.fromNamespaceAndPath("c","tools/wrench"));
    private static final TagKey<Item> CREATE_WRENCHES=TagKey.create(Registries.ITEM,ResourceLocation.fromNamespaceAndPath("create","wrench"));
    private FailureRepair() {}
    private static boolean wrench(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(WRENCHES)||stack.is(CREATE_WRENCHES)
                ||CREATE_WRENCH.equals(BuiltInRegistries.ITEM.getKey(stack.getItem())));
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void repair(PlayerInteractEvent.RightClickBlock event) {
        if(!wrench(event.getItemStack()))return;
        var level=event.getLevel();var player=event.getEntity();
        if(player.isSpectator()||!player.mayBuild()||!level.mayInteract(player,event.getPos()))return;
        var entity=level.getBlockEntity(event.getPos());
        if(!(entity instanceof FailureAccess access)||!access.aste_aeroworks$failureState().failed())return;
        // Consume the click on both sides before a normal wrench action rotates or dismantles the bearing.
        event.setCanceled(true);event.setCancellationResult(InteractionResult.SUCCESS);
        if(level.isClientSide)return;
        if(access.aste_aeroworks$failureState().repair(FailureSettings.current().repairGraceTicks())) {
            access.aste_aeroworks$syncFailure();
            player.displayClientMessage(Component.translatable("aste_aeroworks.failure.repaired"),true);
            level.playSound(null,event.getPos(),SoundEvents.ANVIL_USE,SoundSource.BLOCKS,.35f,1.5f);
        }
    }
}
