package net.aste.aeroworks.mixin.rain;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.aste.aeroworks.client.rain.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets="dev.nonamecrackers2.simpleclouds.client.renderer.WorldEffects",remap=false)
public abstract class CloudRainWorldMixin {
    @Unique private static final BlockPos aste_aeroworks$rejected=new BlockPos(0,Integer.MIN_VALUE,0);
    @Unique private final RandomSource aste_aeroworks$spawnRandom=RandomSource.create(0);
    @Unique private final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap aste_aeroworks$light=new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
    @Unique private Object aste_aeroworks$lightWorld;
    @Unique private long aste_aeroworks$lightTick=Long.MIN_VALUE;

    @WrapOperation(method="tick",at=@At(value="NEW",target="(III)Lnet/minecraft/core/BlockPos;"),require=1,allow=1)
    private BlockPos aste_aeroworks$spawnPosition(int x,int y,int z,Operation<BlockPos> original){
        if(RainConfig.ENABLED.get()&&!RainSpawn.accepted(BlockPos.asLong(x,y,z)))return aste_aeroworks$rejected;
        return original.call(x,y,z);
    }
    @WrapOperation(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"),require=1,allow=1)
    private Biome.Precipitation aste_aeroworks$skipRejected(Biome biome,BlockPos pos,Operation<Biome.Precipitation> original){
        return pos==aste_aeroworks$rejected?Biome.Precipitation.NONE:original.call(biome,pos);
    }
    @WrapOperation(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/util/RandomSource;create(J)Lnet/minecraft/util/RandomSource;"),require=1,allow=1)
    private RandomSource aste_aeroworks$reuseRandom(long seed,Operation<RandomSource> original){
        if(!RainConfig.ENABLED.get())return original.call(seed);
        aste_aeroworks$spawnRandom.setSeed(seed);return aste_aeroworks$spawnRandom;
    }
    @WrapOperation(method="renderRain",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/LevelRenderer;getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)I"),require=1,allow=1)
    private int aste_aeroworks$light(BlockAndTintGetter view,BlockPos pos,Operation<Integer> original){
        if(!RainConfig.ENABLED.get())return original.call(view,pos);
        var level=Minecraft.getInstance().level;long tick=level==null?0:level.getGameTime();
        if(view!=aste_aeroworks$lightWorld||tick!=aste_aeroworks$lightTick){
            aste_aeroworks$light.clear();aste_aeroworks$lightWorld=view;aste_aeroworks$lightTick=tick;
        }
        long key=pos.asLong();
        if(aste_aeroworks$light.containsKey(key))return aste_aeroworks$light.get(key);
        int light=original.call(view,pos);
        if(aste_aeroworks$light.size()<16384)aste_aeroworks$light.put(key,light);
        return light;
    }
}
