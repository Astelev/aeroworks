package net.aste.aeroworks.wind;

/** Server-thread distance hazard process. State is never read directly by the physics thread. */
public final class TurbulenceTracker {
    private final long seed;
    private long serial;
    private double hazard, cooldown, progress, length, started;
    private boolean active;
    public TurbulenceTracker(long seed) { this.seed=seed; hazard=nextHazard(); }
    private double nextHazard() { return -Math.log(Math.max(1e-12,ZonalWindModel.unitHash(seed^(serial*0x6a09e667f3bcc909L)))); }
    public void reset() { active=false; progress=0; cooldown=0; serial++; hazard=nextHazard(); }
    public void advance(double meters,double exposure,double storm,double now,WindEffectsParameters p) {
        if(!p.turbulenceEnabled()) { reset(); return; }
        if(!Double.isFinite(meters) || meters<0) { reset(); return; }
        double rate=-Math.log1p(-p.chancePer100Blocks())/100*ZonalWindModel.clamp(exposure)
                *(1+ZonalWindModel.clamp(storm)*(p.stormChanceMultiplier()-1));
        while(meters>1e-10) {
            if(active) {
                double step=Math.min(meters,length-progress);
                progress+=step; meters-=step;
                if(progress>=length-1e-9) { active=false; cooldown=p.cooldownDistance(); }
            } else if(cooldown>0) {
                double step=Math.min(meters,cooldown); cooldown-=step; meters-=step;
            } else if(rate<=0) break;
            else {
                double until=hazard/rate;
                if(meters<until) { hazard-=meters*rate; break; }
                meters-=until;
                serial++;
                length=p.minimumDistance()+(p.maximumDistance()-p.minimumDistance())
                        *ZonalWindModel.unitHash(seed^serial^0x5ac34L);
                progress=0; active=true; started=now;
                hazard=nextHazard();
            }
        }
    }
    public TurbulenceEpisode snapshot() {
        return active ? new TurbulenceEpisode(seed^(serial*0xd1b54a32d192ed03L),started,progress,length):null;
    }
}
