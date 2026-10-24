package net.aste.aeroworks.failure;

import java.util.function.Consumer;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Client installs the particle handler; no client classes are referenced by common mixins. */
public final class FailureVisuals {
    private static Consumer<BlockEntity> clientTick=entity -> {};
    private FailureVisuals() {}
    public static void setClientTick(Consumer<BlockEntity> handler) { clientTick=handler; }
    public static void tick(BlockEntity entity) { clientTick.accept(entity); }
}
