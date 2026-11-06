package net.aste.aeroworks.waves;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Reused per physics thread; one total impulse/torque, no world queries or block iteration. */
public final class BlockWaveStep {
    public final Vector3d impulse=new Vector3d(),torque=new Vector3d();
    private final Vector3d local=new Vector3d(),world=new Vector3d(),force=new Vector3d(),lever=new Vector3d();
    public int samples,changed;
    public void calculate(BlockWaveSamples blocks,WaveShorePatch shore,WaveParameters p,Pose3dc pose,
            Vector3dc center,double seconds,double gain,double dt){
        impulse.zero();torque.zero();samples=changed=0;
        if(blocks==null||shore==null||!p.enabled()||!p.buoyancy()||p.amplitude()==0||gain<=0)return;
        var cells=blocks.cells();int parts=blocks.complex()?8:1;double half=parts==8?.25:.5;
        double k=WaveMath.TAU/p.wavelength(),kx=k*Math.cos(p.radians()),kz=k*Math.sin(p.radians());
        double phase=-k*p.speed()*seconds;
        for(int i=0;i<cells.length;i+=4)for(int part=0;part<parts;part++){
            local.set(cells[i],cells[i+1],cells[i+2]);
            if(parts==8)local.add((part&1)==0?-.25:.25,(part&2)==0?-.25:.25,(part&4)==0?-.25:.25);
            pose.transformPosition(local,world);
            double surface=shore.surface(world.x,world.z);
            if(surface==ShoreField.DRY||world.y-half>=surface+p.amplitude()||world.y+half<=surface-p.amplitude())continue;
            double weight=shore.weight(world.x,world.z,surface-1);
            if(weight<=0)continue;
            double dy=WaveMath.fastSin(kx*world.x+kz*world.z+phase)*p.amplitude()*weight*gain;
            double delta=BlockWaveMath.delta(world.y,half,surface,dy,cells[i+3]*blocks.weight());
            samples++;
            if(delta==0)continue;changed++;
            force.set(0,delta*dt,0);pose.transformNormalInverse(force);
            impulse.add(force);
            lever.set(local).sub(center).cross(force);torque.add(lever);
        }
    }
}
