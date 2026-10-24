package net.aste.aeroworks.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** Sparse white condensation puffs. Standard depth-tested particle pass, no extra framebuffer. */
public final class UpdraftCloudParticle extends TextureSheetParticle {
    private UpdraftCloudParticle(ClientLevel level,double x,double y,double z,double size,double rise,double opacity,SpriteSet sprites) {
        super(level,x,y,z);
        pickSprite(sprites);
        quadSize=(float)size;
        lifetime=80+random.nextInt(41);
        hasPhysics=false; gravity=0; friction=1;
        xd=0; yd=rise; zd=0;
        rCol=gCol=bCol=1;
        alpha=0;
        this.opacity=(float)opacity;
    }
    private final float opacity;
    @Override public void tick() {
        super.tick();
        alpha=opacity*(float)Math.sin(Math.PI*Math.min(1,(double)age/lifetime));
    }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) { this.sprites=sprites; }
        @Override public Particle createParticle(SimpleParticleType type,ClientLevel level,double x,double y,double z,double size,double rise,double opacity) {
            return new UpdraftCloudParticle(level,x,y,z,size,rise,opacity,sprites);
        }
    }
}
