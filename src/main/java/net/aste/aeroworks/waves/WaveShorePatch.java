package net.aste.aeroworks.waves;

/** Immutable, world-aligned 4-block grid. Published by the server thread, read by physics. */
public final class WaveShorePatch {
    private final int x,z,size;
    private final float[] field,depths;
    public WaveShorePatch(int x,int z,int size,float[] heights,double fade){
        this(x,z,size,heights,null,fade);
    }
    public WaveShorePatch(int x,int z,int size,float[] heights,float[] depths,double fade){
        this.x=x;this.z=z;this.size=size;
        if(depths!=null&&depths.length!=size*size)throw new IllegalArgumentException("Depth grid size");
        this.depths=depths;
        field=ShoreField.build(heights,size,4,(float)fade);
    }
    /** Top of physical water, not the air pocket masked by Deep Seas. */
    public double surface(double wx,double wz){
        double u=(wx-x)/4,v=(wz-z)/4;
        if(!Double.isFinite(u)||!Double.isFinite(v)||u<0||v<0||u>=size-1||v>=size-1)return ShoreField.DRY;
        float y=field[((int)Math.floor(v+.5)*size+(int)Math.floor(u+.5))*2];
        return y==ShoreField.DRY?ShoreField.DRY:y+1;
    }
    public double depth(double wx,double wz){
        if(depths==null)return 128;
        double u=(wx-x)/4,v=(wz-z)/4;
        if(u<0||v<0||u>=size-1||v>=size-1)return 0;
        int ix=(int)u,iz=(int)v,i=iz*size+ix;double fx=u-ix,fz=v-iz;
        return (depths[i]*(1-fx)+depths[i+1]*fx)*(1-fz)+(depths[i+size]*(1-fx)+depths[i+size+1]*fx)*fz;
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
