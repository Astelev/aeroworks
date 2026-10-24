package net.aste.aeroworks.wind;

/** A world-fixed mountain anchor with a slowly drifting, vertically fading column. */
public record MountainFlow(double x, double groundY, double z, long seed, double discovered) {
    public boolean rising() { return ZonalWindModel.unitHash(seed^0x6e37abL)>=.5; }
    public double centerX(WindEffectsParameters p,double time) {
        return x+p.driftRadius()*Math.cos(time*Math.PI*2/p.driftPeriod()+ZonalWindModel.unitHash(seed)*Math.PI*2);
    }
    public double centerZ(WindEffectsParameters p,double time) {
        return z+p.driftRadius()*Math.sin(time*Math.PI*2/p.driftPeriod()*.83+ZonalWindModel.unitHash(seed)*Math.PI*2);
    }
    public double top(WindEffectsParameters p,double time) {
        return groundY+p.columnHeight()*(1+.2*Math.sin(time*Math.PI*2/p.driftPeriod()*.61+ZonalWindModel.unitHash(seed)*Math.PI*2));
    }
    public double velocity(WindEffectsParameters p, double time, double px, double py, double pz) {
        if (!p.mountainEnabled()) return 0;
        double phase=ZonalWindModel.unitHash(seed)*Math.PI*2;
        double clock=time*Math.PI*2/p.driftPeriod();
        double cx=centerX(p,time);
        double cz=centerZ(p,time);
        double r=Math.hypot(px-cx,pz-cz)/(p.diameter()*.5);
        if(r>=1 || py<=groundY) return 0;
        double top=top(p,time);
        if(py>=top) return 0;
        double horizontal=ZonalWindModel.smooth(1-r);
        double vertical=ZonalWindModel.smooth((py-groundY)/30)*ZonalWindModel.smooth((top-py)/60);
        double strength=.65+.35*Math.sin(clock*.71+phase+1.3);
        double sign=rising() ? 1:-1;
        return sign*p.verticalSpeed()*horizontal*vertical*strength
                *ZonalWindModel.smooth((time-discovered)/10);
    }
}
