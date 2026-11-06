package net.aste.aeroworks.waves;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Reused per physics thread; one total impulse/torque, no world queries or block iteration. */
public final class BlockWaveStep {
    public final Vector3d impulse=new Vector3d(),torque=new Vector3d();
    private final Vector3d local=new Vector3d(),world=new Vector3d(),force=new Vector3d(),lever=new Vector3d();
    private final WaveSpectrum spectrum=new WaveSpectrum();
    public int samples,changed;
    public void calculate(BlockWaveSamples blocks,WaveShorePatch shore,WaveParameters p,Pose3dc pose,
            Vector3dc center,double seconds,double gain,double dt){
        calculate(blocks,shore,p,pose,center,seconds,gain,dt,WaveWindGrid.EMPTY);
    }
    public void calculate(BlockWaveSamples blocks,WaveShorePatch shore,WaveParameters p,Pose3dc pose,
            Vector3dc center,double seconds,double gain,double dt,WaveWindGrid wind){
        impulse.zero();torque.zero();samples=changed=0;
        if(blocks==null||shore==null||!p.enabled()||!p.buoyancy()||p.amplitude()==0||gain<=0)return;
        var cells=blocks.cells();int parts=blocks.complex()?8:1;double half=parts==8?.25:.5;
        spectrum.begin(p,wind,seconds);double maximum=p.maximumDisplacement();
        for(int i=0;i<cells.length;i+=4)for(int part=0;part<parts;part++){
            local.set(cells[i],cells[i+1],cells[i+2]);
            if(parts==8)local.add((part&1)==0?-.25:.25,(part&2)==0?-.25:.25,(part&4)==0?-.25:.25);
            pose.transformPosition(local,world);
            double surface=shore.surface(world.x,world.z);
            if(surface==ShoreField.DRY||world.y-half>=surface+maximum||world.y+half<=surface-maximum)continue;
            double weight=shore.weight(world.x,world.z,surface-1);
            if(weight<=0)continue;
            double dy=spectrum.height(world.x,world.z,shore.depth(world.x,world.z))*weight*gain;
            double delta=BlockWaveMath.delta(world.y,half,surface,dy,cells[i+3]*blocks.weight());
            samples++;
            if(delta==0)continue;changed++;
            force.set(0,delta*dt,0);pose.transformNormalInverse(force);
            impulse.add(force);
            lever.set(local).sub(center).cross(force);torque.add(lever);
        }
    }
}
