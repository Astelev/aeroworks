package net.aste.aeroworks.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import org.joml.Vector3d;

/** Short, fading condensation wisps curling around a drifting wake axis. */
public final class StallWispParticle extends TextureSheetParticle {
    private final Vector3d axis,across,up;
    private double cx,cy,cz;
    private final double phase,turn;
    private StallWispParticle(ClientLevel level,double x,double y,double z,double vx,double vy,double vz,SpriteSet sprites) {
        super(level,x,y,z);pickSprite(sprites);
        cx=x;cy=y;cz=z;xd=vx;yd=vy;zd=vz;
        axis=new Vector3d(vx,vy,vz);
        if(axis.lengthSquared()<1e-9)axis.set(1,0,0);else axis.normalize();
        across=axis.cross(Math.abs(axis.y)<.9?new Vector3d(0,1,0):new Vector3d(1,0,0),new Vector3d()).normalize();
        up=axis.cross(across,new Vector3d());
        phase=random.nextDouble()*Math.PI*2;turn=random.nextBoolean()?.6:-.6;
        lifetime=16+random.nextInt(9);quadSize=.12f;alpha=0;
        rCol=gCol=bCol=1;hasPhysics=false;gravity=0;friction=1;
    }
    @Override public void tick() {
        xo=x;yo=y;zo=z;
        if(age++>=lifetime) {remove();return;}
        cx+=xd;cy+=yd;cz+=zd;
        double radius=.025*age,a=phase+age*turn;
        double c=Math.cos(a)*radius,s=Math.sin(a)*radius;
        setPos(cx+across.x*c+up.x*s,cy+across.y*c+up.y*s,cz+across.z*c+up.z*s);
        alpha=.6f*(float)Math.sin(Math.PI*age/lifetime);
        quadSize=.12f+.008f*age;
    }
    @Override public ParticleRenderType getRenderType() {return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;}
    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) {this.sprites=sprites;}
        @Override public Particle createParticle(SimpleParticleType type,ClientLevel level,double x,double y,double z,double vx,double vy,double vz) {
            return new StallWispParticle(level,x,y,z,vx,vy,vz,sprites);
        }
    }
}
