package net.aste.aeroworks.failure;

import java.util.UUID;

/** Cheap tick accounting. Short strokes retain their active duration without doing a chance roll each tick. */
public final class FailureWork {
    private boolean initialized,running;
    private int direction,activeTicks;
    private double rpmSum;
    private UUID body;
    private FailureBlockProfile profile;
    public void use(FailureBlockProfile next) {
        if(profile!=next) {profile=next;discardExposure();}
    }
    public boolean observe(float rpm,boolean countReversals) {
        boolean active=Float.isFinite(rpm)&&Math.abs(rpm)>=.001;
        int nextDirection=active?(rpm>0?1:-1):0;
        boolean started=initialized&&active&&(!running||(countReversals&&direction!=nextDirection));
        initialized=true;running=active;direction=nextDirection;
        return started;
    }
    public boolean running() { return running; }
    public void enter(UUID id) { if(!id.equals(body)) {discardExposure();body=id;} }
    public void accumulate(float rpm) { if(running){activeTicks++;rpmSum+=Math.abs(rpm);} }
    public boolean due() { return activeTicks>=20; }
    public double seconds() { return activeTicks/20.0; }
    public double averageRPM() { return activeTicks==0?0:rpmSum/activeTicks; }
    public void clearWindow() {activeTicks=0;rpmSum=0;}
    public void discardExposure() {clearWindow();body=null;}
    public void reset() {discardExposure();initialized=running=false;direction=0;profile=null;}
}
