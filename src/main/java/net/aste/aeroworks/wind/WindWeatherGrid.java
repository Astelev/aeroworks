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
    /** Blend neighbouring cell centres so crossing a cell edge cannot abruptly turn the wind. */
    public static WindWeatherBlend lookup(Map<Long,Cell> cells,double x,double z,double time) {
        var fallback=lookup(cells,x,z);
        double gx=x/SIZE-.5,gz=z/SIZE-.5;int ix=(int)Math.floor(gx),iz=(int)Math.floor(gz);
        var a=weather(cells,ix,iz,fallback);var b=weather(cells,ix+1,iz,fallback);
        var c=weather(cells,ix,iz+1,fallback);var d=weather(cells,ix+1,iz+1,fallback);
        if(a.equals(b)&&a.equals(c)&&a.equals(d))return a;
        double fx=ZonalWindModel.smooth(gx-ix),fz=ZonalWindModel.smooth(gz-iz);
        var state=mix(mix(a.current(time),b.current(time),fx),mix(c.current(time),d.current(time),fx),fz);
        return new WindWeatherBlend(state,state,0,1);
    }
    private static WindWeatherBlend weather(Map<Long,Cell> cells,int x,int z,WindWeatherBlend fallback) {
        var cell=cells.get(key(x,z));return cell==null?fallback:cell.weather();
    }
    private static WindWeather mix(WindWeather a,WindWeather b,double t) {
        return new WindWeather(t<.5?a.cloudType():b.cloudType(),
            a.rain()+(b.rain()-a.rain())*t,a.storm()+(b.storm()-a.storm())*t,
            ZonalWindModel.angleMix(a.cloudHeading(),b.cloudHeading(),t),
            ZonalWindModel.angleMix(a.surfaceTurn(),b.surfaceTurn(),t),
            ZonalWindModel.angleMix(a.aloftTurn(),b.aloftTurn(),t));
    }
    private WindWeatherGrid(){}
}
