package net.aste.aeroworks.waves;

/** Difference from Sable 2.0.6 Rapier do_float: 10.5 * submerged voxel volume * block volume.
 * This is a correction, not a second full buoyancy force. Deep Seas air cells are separate. */
public final class BlockWaveMath {
    private BlockWaveMath() {}
    public static double delta(double y,double half,double surface,double displacement,double strength){
        if(displacement==0 || strength<=0)return 0;
        double width=2*half,bottom=y-half;
        return 10.5*strength*width*width*(Math.clamp(surface+displacement-bottom,0,width)
                -Math.clamp(surface-bottom,0,width));
    }
}
