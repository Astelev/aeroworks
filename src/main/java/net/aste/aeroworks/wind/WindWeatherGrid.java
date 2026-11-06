package net.aste.aeroworks.wind;

import java.util.Map;
/** Shared environmental weather cells, independent of sails and physical-body identity. */
public final class WindWeatherGrid {
    public static final int SIZE=128, MAX_CELLS=256;
    public record Cell(int x,int z,WindWeatherBlend weather) {
        public Cell {
            if(weather==null||!Double.isFinite(weather.started())||!Double.isFinite(weather.duration())||weather.duration()<=0)
                throw new IllegalArgumentException("Invalid weather transition");
            for(var state:new WindWeather[]{weather.from(),weather.to()}) {
                if(state==null)throw new IllegalArgumentException("Missing weather state");
                for(double value:new double[]{state.rain(),state.storm(),state.cloudHeading(),state.surfaceTurn(),state.aloftTurn()})
                    if(!Double.isFinite(value))throw new IllegalArgumentException("Invalid weather state");
                if(state.rain()<0||state.rain()>1||state.storm()<0||state.storm()>1)throw new IllegalArgumentException("Invalid weather intensity");
            }
        }
        public long key(){return WindWeatherGrid.key(x,z);}
    }
    public static int coordinate(double position){return (int)Math.floor(position/SIZE);}
    public static long key(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
    public static long at(double x,double z){return key(coordinate(x),coordinate(z));}
    public static int x(long key){return (int)(key>>32);}
    public static int z(long key){return (int)key;}
    public static double center(int coordinate){return ((double)coordinate+.5)*SIZE;}
    public static WindWeatherBlend lookup(Map<Long,Cell> cells,double x,double z){
        var cell=cells.get(at(x,z));return cell==null?WindWeatherBlend.CLEAR:cell.weather();
    }
    private WindWeatherGrid(){}
}
