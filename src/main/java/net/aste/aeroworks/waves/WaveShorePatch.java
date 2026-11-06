package net.aste.aeroworks.waves;

/** Immutable, world-aligned 4-block grid. Published by the server thread, read by physics. */
public final class WaveShorePatch {
    private final int x,z,size;
    private final float[] field;
    public WaveShorePatch(int x,int z,int size,float[] heights,double fade){
        this.x=x;this.z=z;this.size=size;
        field=ShoreField.build(heights,size,4,(float)fade);
    }
    public double weight(double wx,double wz,double waterBlockY){
        double u=(wx-x)/4,v=(wz-z)/4;
        if(!Double.isFinite(u)||!Double.isFinite(v)||u<0||v<0||u>=size-1||v>=size-1)return 0;
        int nearest=((int)Math.floor(v+.5)*size+(int)Math.floor(u+.5))*2;
        if(Math.abs(field[nearest]-waterBlockY)>.01)return 0;
        int ix=(int)u,iz=(int)v,i=(iz*size+ix)*2;
        double fx=u-ix,fz=v-iz;
        return (field[i+1]*(1-fx)+field[i+3]*fx)*(1-fz)
                +(field[i+size*2+1]*(1-fx)+field[i+size*2+3]*fx)*fz;
    }
}
