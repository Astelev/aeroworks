package net.aste.aeroworks.waves;

import java.util.Map;
import java.util.HashMap;

/** Coarse ambient wind energy, sampled once/second. No aircraft turbulence in ocean waves. */
public final class WaveWindGrid {
    private final int seed;
    private final Map<Long,Cell> cells;
    private final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<Cell> index;
    public int seed(){return seed;}
    public Map<Long,Cell> cells(){return cells;}
    @Override public boolean equals(Object other){return other instanceof WaveWindGrid g&&g.seed==seed&&g.cells.equals(cells);}
    @Override public int hashCode(){return 31*seed+cells.hashCode();}
    public static final int SPACING=64,MAX_CELLS=1024,CLIENT_RADIUS=5,CLIENT_SIZE=11;
    public static final WaveWindGrid EMPTY=new WaveWindGrid(0,Map.of());
    public record Cell(int x,int z,float from,float to,double started){
        public Cell {
            if(Math.abs((long)x)>500000||Math.abs((long)z)>500000||!Float.isFinite(from)||!Float.isFinite(to)
                    ||from<0||to<0||from>1.5||to>1.5||!Double.isFinite(started))throw new IllegalArgumentException("Invalid sea wind cell");
        }
        public double at(double seconds){return from+(to-from)*WaveMath.smooth((seconds-started)/2);}
        public long key(){return WaveWindGrid.key(x,z);}
    }
    public WaveWindGrid(int seed,Map<Long,Cell> cells) {
        if(cells.size()>MAX_CELLS)throw new IllegalArgumentException("Too many sea wind cells");
        for(var e:cells.entrySet())if(e.getKey()!=e.getValue().key())throw new IllegalArgumentException("Sea wind key mismatch");
        this.seed=seed;this.cells=Map.copyOf(cells);
        index=new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>(cells.size());
        for(var c:cells.values())index.put(c.key(),c);
    }
    public static long key(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
    private double node(int x,int z,double seconds){var c=index.get(key(x,z));return c==null?0:c.at(seconds);}
    public double ratio(double x,double z,double seconds){
        double u=x/SPACING,v=z/SPACING;int ix=(int)Math.floor(u),iz=(int)Math.floor(v);
        double fx=u-ix,fz=v-iz;
        return (node(ix,iz,seconds)*(1-fx)+node(ix+1,iz,seconds)*fx)*(1-fz)
                +(node(ix,iz+1,seconds)*(1-fx)+node(ix+1,iz+1,seconds)*fx)*fz;
    }
    public WaveWindGrid around(double x,double z){
        int cx=(int)Math.floor(x/SPACING),cz=(int)Math.floor(z/SPACING);var map=new HashMap<Long,Cell>();
        for(int dz=-CLIENT_RADIUS;dz<=CLIENT_RADIUS;dz++)for(int dx=-CLIENT_RADIUS;dx<=CLIENT_RADIUS;dx++){
            var c=cells.get(key(cx+dx,cz+dz));if(c!=null)map.put(c.key(),c);
        }
        return new WaveWindGrid(seed,map);
    }
}
