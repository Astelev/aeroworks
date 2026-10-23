package net.aste.aeroworks.wind;

/** A world-fixed mountain anchor with a slowly drifting, vertically fading column. */
public record MountainFlow(double x, double groundY, double z, long seed, double discovered) {
    public double velocity(WindEffectsParameters p, double time, double px, double py, double pz) {
        if (!p.mountainEnabled()) return 0;
        double phase=ZonalWindModel.unitHash(seed)*Math.PI*2;
        double clock=time*Math.PI*2/p.driftPeriod();
        double cx=x+p.driftRadius()*Math.cos(clock+phase);
        double cz=z+p.driftRadius()*Math.sin(clock*.83+phase);
        double r=Math.hypot(px-cx,pz-cz)/(p.diameter()*.5);
        if(r>=1 || py<=groundY) return 0;
        double top=groundY+p.columnHeight()*(1+.2*Math.sin(clock*.61+phase));
        if(py>=top) return 0;
        double horizontal=ZonalWindModel.smooth(1-r);
        double vertical=ZonalWindModel.smooth((py-groundY)/30)*ZonalWindModel.smooth((top-py)/60);
        double strength=.65+.35*Math.sin(clock*.71+phase+1.3);
        double sign=ZonalWindModel.unitHash(seed^0x6e37abL)<.5 ? -1:1;
        return sign*p.verticalSpeed()*horizontal*vertical*strength
                *ZonalWindModel.smooth((time-discovered)/10);
    }
}
