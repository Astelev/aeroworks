package net.aste.aeroworks.compat;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import net.neoforged.fml.loading.FMLLoader;

public final class CompatibilityPlugin implements IMixinConfigPlugin {
    private boolean atmospheric, deepSeas;
    @Override public void onLoad(String mixinPackage) {
        var mods = FMLLoader.getLoadingModList();
        atmospheric = mods.getModFileById("aeronauticswinds") != null;
        deepSeas = mods.getModFileById("create_submarine") != null;
    }
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        return CompatibilityPolicy.apply(mixin,atmospheric,deepSeas);
    }
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
