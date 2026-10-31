package net.aste.aeroworks.wind;

import net.aste.aeroworks.Aeroworks;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class WindParticles {
    public static final DeferredRegister<ParticleType<?>> REGISTER=DeferredRegister.create(Registries.PARTICLE_TYPE,Aeroworks.MODID);
    public static final DeferredHolder<ParticleType<?>,SimpleParticleType> UPDRAFT=REGISTER.register("updraft_cloud",()->new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>,SimpleParticleType> STALL=REGISTER.register("stall_wisp",()->new SimpleParticleType(false));
    private WindParticles() {}
}
