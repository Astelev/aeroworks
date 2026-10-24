package net.aste.aeroworks;

import net.aste.aeroworks.wind.*;

public final class FlightWindTest {
    private static final WindEffectsParameters P=WindEffectsParameters.DEFAULT;
    private static void check(boolean ok,String why) { if(!ok) throw new AssertionError(why); }
    private static void near(double a,double b) { check(Math.abs(a-b)<1e-8,"Mismatch "+a+" / "+b); }
    public static void main(String[] args) {
        mountains(); gusts(); lifecycle(); frameIndependence(); storms(); alternatingRocking(); cameraMotion();
        System.out.println("Flight wind: mountain boundaries/drift, bounded gusts, per-aircraft torque, distance lifecycle, timestep independence and storm cadence passed");
    }
    private static void mountains() {
        boolean up=false,down=false;
        for(int seed=1;seed<200;seed++) {
            var flow=new MountainFlow(0,150,0,seed,0);
            double v=flow.velocity(P,40,0,250,0);
            up|=v>0; down|=v<0;
            check(Math.abs(v)<=P.verticalSpeed(),"Bounded vertical speed");
            near(flow.velocity(P,40,66,250,0),0);
            near(flow.velocity(P,40,0,149,0),0);
            near(flow.velocity(P,40,0,751,0),0);
            near(flow.velocity(P,0,0,250,0),0);
            check(Math.abs(v-flow.velocity(P,40.001,0,250,0))<.01,"Slow continuous motion");
        }
        check(up&&down,"Both updrafts and downdrafts");
        var out=new ZonalWindModel.Result();
        var same=new MountainFlow(0,150,0,1,0);
        FlightWindEffects.apply(P,40,0,250,0,new MountainFlow[]{same,same,same},null,20,out);
        near(out.y,same.velocity(P,40,0,250,0));
    }
    private static void gusts() {
        var e=new TurbulenceEpisode(21,0,30,200);
        for(int i=0;i<1000;i++) {
            double t=i*.013;
            var a=new ZonalWindModel.Result(); var b=new ZonalWindModel.Result();
            e.apply(P,t,10,a); e.apply(P,t,20,b);
            check(Math.sqrt(a.x*a.x+a.y*a.y+a.z*a.z)<=P.gustSpeed()+1e-8,"Bounded 3D gust");
            near(a.x,b.x); near(a.y,b.y); near(a.z,b.z);
            near(a.torqueX*2,b.torqueX); near(a.torqueY*2,b.torqueY); near(a.torqueZ*2,b.torqueZ);
        }
        var zero=new ZonalWindModel.Result(); e.apply(P,10,0,zero); near(zero.x,0);near(zero.torqueX,0);
        near(new TurbulenceEpisode(21,0,0,200).envelope(P),0);
        near(new TurbulenceEpisode(21,0,200,200).envelope(P),0);
        var a=new ZonalWindModel.Result();var b=new ZonalWindModel.Result();
        e.apply(P,10,10,a);new TurbulenceEpisode(22,0,30,200).apply(P,10,10,b);
        check(a.x!=b.x && a.torqueX!=b.torqueX,"Independent aircraft episodes");
    }
    private static void lifecycle() {
        var tracker=new TurbulenceTracker(123);
        for(int i=0;i<1000;i++)tracker.advance(10,0,1,i*.05,P);
        check(tracker.snapshot()==null,"Cannot start outside cloud");
        for(int i=0;i<1000;i++)tracker.advance(0,1,1,i*.05,P);
        check(tracker.snapshot()==null,"Hovering does not accumulate distance");
        int walked=0;
        while(tracker.snapshot()==null && walked++<10000)tracker.advance(1,1,0,50,P);
        check(tracker.snapshot()!=null,"Cloud traversal can start episode");
        var start=tracker.snapshot();
        tracker.advance(20,0,0,51,P);
        check(tracker.snapshot()!=null,"Episode follows aircraft outside cloud");
        near(tracker.snapshot().progress(),start.progress()+20);
        tracker.advance(1000,0,0,52,P);
        check(tracker.snapshot()==null,"Episode ends by flown distance");
        tracker.advance(Double.NaN,1,1,53,P);check(tracker.snapshot()==null,"Invalid motion resets safely");
        tracker.reset();check(tracker.snapshot()==null,"Teleport reset");
    }
    private static void frameIndependence() {
        for(int seed=1;seed<100;seed++) {
            var a=new TurbulenceTracker(seed);var b=new TurbulenceTracker(seed);
            for(int i=0;i<1000;i++)a.advance(.1,.7,.8,1,P);
            b.advance(100,.7,.8,1,P);
            var ea=a.snapshot();var eb=b.snapshot();
            check((ea==null)==(eb==null),"Distance hazard independent of tick subdivision");
            if(ea!=null) {near(ea.progress(),eb.progress());near(ea.length(),eb.length());check(ea.seed()==eb.seed(),"Same episode");}
        }
    }
    private static void alternatingRocking() {
        for(long seed=1;seed<100;seed++) {
            for(int half=0;half<100;half++) {
                double center=(half+.5)*P.rockingPeriod();
                double a=AlternatingWindPulse.sample(seed,center,P.rockingPeriod());
                double b=AlternatingWindPulse.sample(seed,center+P.rockingPeriod(),P.rockingPeriod());
                check(a*b<0,"Every pulse must reverse, irrespective of random seed");
                check(Math.abs(a)>=.55 && Math.abs(a)<=1,"Random bounded pulse strength");
                double edge=(half+1)*P.rockingPeriod();
                double before=AlternatingWindPulse.sample(seed,edge-1e-7,P.rockingPeriod());
                double after=AlternatingWindPulse.sample(seed,edge+1e-7,P.rockingPeriod());
                check(Math.abs(before-after)<1e-8,"Force remains continuous at reversals");
            }
            var e=new TurbulenceEpisode(seed,0,50,200);
            double previous=0;
            int changes=0;
            for(int tick=0;tick<40;tick++) {
                var out=new ZonalWindModel.Result();e.apply(P,tick/20.0,10,out);
                if(out.torqueX*previous<0)changes++;
                if(Math.abs(out.torqueX)>1e-6)previous=out.torqueX;
            }
            check(changes>=10,"Aircraft rocks repeatedly at actual 20 Hz sampling");
        }
    }
    private static void cameraMotion() {
        var out=new WindCameraMotion.Result();
        for(int frame=0;frame<1000;frame++) {
            WindCameraMotion.sample(123,frame/144.0,P.rockingPeriod(),1,out);
            check(Math.abs(out.roll)<=1 && Math.abs(out.pitch)<=.45 && Math.abs(out.yaw)<=.25,"Small bounded camera angles");
        }
        WindCameraMotion.sample(123,7,P.rockingPeriod(),1,out);
        double roll=out.roll,pitch=out.pitch,yaw=out.yaw;
        // Repeated rendering of the same time cannot accumulate a permanent aim rotation.
        for(int frame=0;frame<1000;frame++)WindCameraMotion.sample(123,7,P.rockingPeriod(),1,out);
        near(roll,out.roll);near(pitch,out.pitch);near(yaw,out.yaw);
        WindCameraMotion.sample(123,7,P.rockingPeriod(),0,out);
        near(out.roll,0);near(out.pitch,0);near(out.yaw,0);
        WindCameraMotion.sample(123,Double.NaN,P.rockingPeriod(),1,out);
        near(out.roll,0);near(out.pitch,0);near(out.yaw,0);
    }
    private static void storms() {
        near(FlightWindEffects.directionRate(1,0,P),1);
        near(FlightWindEffects.directionRate(1,1,P),3);
        near(FlightWindEffects.directionRate(1,.5,P),2);
        double phase=.8;
        double next=phase+.05*FlightWindEffects.directionRate(.01,1,P);
        check(next>phase && next-phase<.002,"Speed up the phase without resetting heading");
    }
}
