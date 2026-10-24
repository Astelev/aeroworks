package net.aste.aeroworks;

import java.util.List;
import java.util.UUID;
import net.aste.aeroworks.failure.*;

public final class FailureProfilesTest {
    private static final FailureRisk R=FailureRisk.DEFAULT;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void near(double a,double b){check(Math.abs(a-b)<1e-10,"Mismatch "+a+" / "+b);}
    public static void main(String[] args) {
        profiles(); shortWork(); starts(); context();
        System.out.println("Failure profiles: per-block inheritance/validation, idempotent auto-entries, short-stroke accounting, starts/reversals, protection context and occupied exposure passed");
    }
    private static void profiles() {
        var p=FailureBlockProfile.parse("aeronautics:propeller_bearing; base_chance_per_minute=0.01; chance_per_start=0",R,300).profile();
        var q=FailureBlockProfile.parse("create:mechanical_bearing; base_chance_per_minute=0.8; chance_per_start=0.02; rpm_risk_weight=0; repair_grace_seconds=5; count_reversals_as_start=false",R,300).profile();
        near(p.risk().baseChancePerMinute(),.01);near(q.risk().baseChancePerMinute(),.8);
        near(q.risk().turbulenceMultiplier(),R.turbulenceMultiplier());check(q.repairGraceTicks()==100&&!q.countReversals(),"Per-block repair/reversal overrides");
        check(q.probability(16,16,0,64,false,.05,true)>p.probability(16,16,0,64,false,.05,true),"Independent block chances");
        near(q.risk().eventProbability(.02,16,0,64,false),.02);
        var rows=FailureBlockProfile.completeEntries(List.of(),List.of("aeronautics:propeller_bearing","create:mechanical_bearing"),R);
        check(rows.size()==2,"One generated profile for each selected block");
        check(rows.equals(FailureBlockProfile.completeEntries(rows,List.of("aeronautics:propeller_bearing","create:mechanical_bearing"),R)),"Saving generated profiles must not loop or duplicate");
        var preset=FailureBlockProfile.parse(rows.get(1),R,300).profile();
        near(preset.chancePerStart(),.02);near(preset.risk().rpmWeight(),0);
        var custom=List.of("create:mechanical_bearing; chance_per_start=0.73");
        check(FailureBlockProfile.completeEntries(custom,List.of("create:mechanical_bearing"),R).equals(custom),"Never overwrite user values");
        for(String bad:List.of("bad id; chance_per_start=.1","create:test; typo=.1","create:test; chance_per_start=NaN", "create:test; chance_per_start=1.1", "create:test; reference_rpm=0", "create:test; count_reversals_as_start=maybe", "create:test; repair_grace_seconds=2147483647", "create:test; chance_per_start=.1; chance_per_start=.2")) {
            boolean rejected=false;try{FailureBlockProfile.parse(bad,R,300);}catch(RuntimeException expected){rejected=true;}
            check(rejected,"Reject malformed override: "+bad);
        }
    }
    private static void shortWork() {
        var w=new FailureWork();var id=new UUID(1,2);
        w.observe(0,true);
        for(int pulse=0;pulse<10;pulse++) {
            check(w.observe(16,true),"Short stroke begins");w.enter(id);
            for(int tick=0;tick<2;tick++) {w.observe(16,true);w.accumulate(16);}
            w.observe(0,true);
        }
        check(w.due(),"Ten two-tick strokes cannot disappear between one-second checks");near(w.seconds(),1);near(w.averageRPM(),16);
        var p=new FailureBlockProfile(R,0,300,true);
        near(p.probability(w.averageRPM(),16,40,300,false,w.seconds(),false),R.probability(16,40,300,false,1));
        w.clearWindow();check(!w.due(),"Only already evaluated exposure is consumed");
    }
    private static void starts() {
        var w=new FailureWork();
        check(!w.observe(16,true),"Loading an already running block must not invent a start");
        check(!w.observe(32,true),"Same-direction RPM change is not a new stroke");
        check(w.observe(-16,true),"Direction reversal can count as a stroke");
        check(!w.observe(16,false),"Reversal counting is independently configurable");
        w.observe(0,true);check(w.observe(16,true),"Actual restart is detected within one tick");
        var p=FailureBlockProfile.parse("create:mechanical_bearing; base_chance_per_minute=0; chance_per_start=.02; rpm_risk_weight=0",R,300).profile();
        near(p.probability(16,16,0,64,false,.05,true),.02);
        near(p.probability(16,16,0,64,false,.05,false),0);
        check(p.probability(16,16,40,400,true,.05,true)>.02,"Flight environment amplifies stroke risk");
        near(p.probability(16,16,0,64,false,0,true),.02);
        var mixed=new FailureBlockProfile(R,.1,300,true);
        double expected=1-(1-R.probability(32,0,64,false,1))*(1-R.eventProbability(.1,128,0,64,false));
        near(mixed.probability(32,128,0,64,false,1,true),expected);
    }
    private static void context() {
        var w=new FailureWork();w.observe(0,true);w.observe(64,true);w.enter(new UUID(1,2));w.accumulate(64);
        w.discardExposure();near(w.seconds(),0);
        check(!w.observe(64,true),"Boarding during continuous rotation must not invent a restart");
        w.enter(new UUID(1,2));w.accumulate(64);w.enter(new UUID(3,4));near(w.seconds(),0);
        check(w.running(),"Changing ship clears exposure without inventing mechanical stops");
        w.reset();check(!w.observe(64,true),"Reloaded or disabled state does not create a fake start");
    }
}
