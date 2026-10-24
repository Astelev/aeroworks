package net.aste.aeroworks.wind;

/** Aircraft-local gusts; no world-space location. Progress measures flown distance, not time. */
public record TurbulenceEpisode(long seed, double started, double progress, double length) {
    public double envelope(WindEffectsParameters p) {
        return ZonalWindModel.smooth(progress/p.fadeDistance())
                *ZonalWindModel.smooth((length-progress)/p.fadeDistance());
    }
    public void apply(WindEffectsParameters p, double time, int sails, ZonalWindModel.Result out) {
        if(!p.turbulenceEnabled() || sails<=0) return;
        double strength=envelope(p);
        double clock=Math.max(0,time-started)/p.gustPeriod();
        double x=wave(seed,clock), y=wave(seed^0x7b5acL,clock), z=wave(seed^0x5b178L,clock);
        double norm=Math.max(1,Math.sqrt(x*x+y*y+z*z));
        double gust=strength*p.gustSpeed()/norm;
        out.x+=x*gust; out.y+=y*gust; out.z+=z*gust;
        double torque=strength*p.rockingTorquePerSail()*sails;
        out.torqueX=AlternatingWindPulse.sample(seed^0x5a0fL,Math.max(0,time-started),p.rockingPeriod())*torque;
        out.torqueY=AlternatingWindPulse.sample(seed^0x91afL,Math.max(0,time-started)+p.rockingPeriod()*.31,p.rockingPeriod())*torque*.4;
        out.torqueZ=AlternatingWindPulse.sample(seed^0x631cL,Math.max(0,time-started)+p.rockingPeriod()*.64,p.rockingPeriod())*torque;
    }
    private static double wave(long seed,double time) {
        long epoch=(long)Math.floor(time);
        double t=ZonalWindModel.smooth(time-epoch);
        double a=ZonalWindModel.unitHash(seed^(epoch*0x9e3779b97f4a7c15L))*2-1;
        double b=ZonalWindModel.unitHash(seed^((epoch+1)*0x9e3779b97f4a7c15L))*2-1;
        return a+(b-a)*t;
    }
}
