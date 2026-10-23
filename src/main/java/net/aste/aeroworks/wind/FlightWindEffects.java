package net.aste.aeroworks.wind;

public final class FlightWindEffects {
    private FlightWindEffects() {}
    public static double directionRate(double base,double storm,WindEffectsParameters p) {
        return base*(1+ZonalWindModel.clamp(storm)*(p.stormDirectionMultiplier()-1));
    }
    public static void apply(WindEffectsParameters p, double time, double x, double y, double z,
            MountainFlow[] flows,TurbulenceEpisode episode,int sails,ZonalWindModel.Result out) {
        out.y=0; out.torqueX=0; out.torqueY=0; out.torqueZ=0;
        if(p.mountainEnabled()) {
            double up=0,down=0;
            for(var flow:flows) {
                double v=flow.velocity(p,time,x,y,z);
                up=Math.max(up,v); down=Math.min(down,v);
            }
            out.y=up+down; // Overlapping streams cannot grow without bound with the number of anchors.
        }
        if(episode!=null) episode.apply(p,time,sails,out);
    }
}
