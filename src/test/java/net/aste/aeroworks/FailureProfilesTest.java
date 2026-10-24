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
        System.out.println("Failure profiles: common/per-block multiplication, migration and validation, idempotent auto-entries, short-stroke accounting, starts/reversals, protection context and occupied exposure passed");
    }
    private static void profiles() {
        String row="create:mechanical_bearing; base_chance_per_minute_factor=2; chance_per_start_factor=3; rpm_risk_weight_factor=0; repair_grace_seconds_factor=2; count_reversals_as_start=false";
        var p=FailureBlockProfile.parse(row,R,300,.02).profile();
        near(p.risk().baseChancePerMinute(),.6);near(p.chancePerStart(),.06);
        near(p.risk().rpmWeight(),0);check(p.repairGraceTicks()==600&&!p.countReversals(),"Protection factor and reversal switch");
        var lower=new FailureRisk(.1,64,1,40,1,128,256,1,8,256);
        var q=FailureBlockProfile.parse(row,lower,100,.01).profile();
        near(q.risk().baseChancePerMinute(),.2);near(q.chancePerStart(),.03);check(q.repairGraceTicks()==200,"Common protection still applies");
        check(q.probability(16,16,0,64,false,1,true)<p.probability(16,16,0,64,false,1,true),"Changing globals affects existing factors");
        var zero=new FailureRisk(0,64,1,40,1,128,256,1,8,256);
        near(FailureBlockProfile.parse(row,zero,300,0).profile().probability(16,16,100,600,true,1,true),0);
        near(FailureBlockProfile.parse("create:test",R,300).profile().risk().baseChancePerMinute(),R.baseChancePerMinute());
        near(FailureBlockProfile.parse("create:test; base_chance_per_minute_factor=10; chance_per_start_factor=100",R,300).profile().chancePerStart(),1);
        near(FailureBlockProfile.parse("create:test; base_chance_per_minute_factor=10",R,300).profile().risk().baseChancePerMinute(),1);
        var rows=FailureBlockProfile.completeEntries(List.of(),List.of("aeronautics:propeller_bearing","create:mechanical_bearing"),R);
        check(rows.size()==2,"One generated factor profile per selected block");
        check(rows.equals(FailureBlockProfile.completeEntries(rows,List.of("aeronautics:propeller_bearing","create:mechanical_bearing"),lower)),"Generated rows never freeze common chances or duplicate");
        near(FailureBlockProfile.parse(rows.get(1),R,300,.02).profile().chancePerStart(),.02);
        near(FailureBlockProfile.parse(rows.get(1),lower,300,.001).profile().chancePerStart(),.001);
        near(FailureBlockProfile.parse(rows.get(0),R,300,.02).profile().chancePerStart(),0);
        check(FailureBlockProfile.migrate(row,R,300,.02).equals(row),"New profiles remain untouched");
        String legacy="create:mechanical_bearing; base_chance_per_minute=0.6; chance_per_start=0.04; rpm_risk_weight=0; repair_grace_seconds=5";
        String migrated=FailureBlockProfile.migrate(legacy,R,300,.02);
        var old=FailureBlockProfile.parse(migrated,R,300,.02).profile();
        near(old.risk().baseChancePerMinute(),.6);near(old.chancePerStart(),.04);near(old.risk().rpmWeight(),0);check(old.repairGraceTicks()==100,"Legacy protection converted");
        check(FailureBlockProfile.migrate(migrated,R,300,.02).equals(migrated),"Migration must be idempotent");
        var oldWithoutStart=FailureBlockProfile.parse(FailureBlockProfile.migrate("create:test; base_chance_per_minute=.3",R,300,.02),R,300,.02).profile();
        near(oldWithoutStart.chancePerStart(),0);
        var globals=new FailureRisk(.05,128,2,80,3,256,512,4,16,512);
        var neutral=FailureBlockProfile.parse("create:test",globals,600,.005).profile();
        check(neutral.risk().equals(globals),"All omitted numerical settings follow common values");near(neutral.chancePerStart(),.005);
        var factors=FailureBlockProfile.parse("create:test; reference_rpm_factor=2; rpm_risk_weight_factor=.5; reference_flight_speed_factor=2; flight_speed_risk_weight_factor=2; altitude_start_factor=.5; altitude_scale_factor=2; altitude_risk_weight_factor=2; turbulence_multiplier_factor=.5; maximum_risk_multiplier_factor=.5",globals,600,.005).profile().risk();
        near(factors.referenceRPM(),256);near(factors.rpmWeight(),1);near(factors.referenceSpeed(),160);near(factors.speedWeight(),6);
        near(factors.altitudeStart(),128);near(factors.altitudeScale(),1024);near(factors.altitudeWeight(),8);near(factors.turbulenceMultiplier(),8);near(factors.maximumMultiplier(),256);

        near(FailureBlockProfile.parse(migrated,lower,300,.01).profile().risk().baseChancePerMinute(),.2);
        near(FailureBlockProfile.parse(FailureBlockProfile.migrate(legacy,zero,300,0),zero,300,0).profile().probability(16,16,0,64,false,1,true),0);
        for(String bad:List.of("bad id; chance_per_start_factor=1","create:test; typo=1","create:test; chance_per_start_factor=NaN", "create:test; chance_per_start_factor=-1", "create:test; reference_rpm_factor=0", "create:test; count_reversals_as_start=maybe", "create:test; repair_grace_seconds_factor=Infinity", "create:test; chance_per_start_factor=1; chance_per_start_factor=2", "create:test; base_chance_per_minute=.3")) {
            boolean rejected=false;try{FailureBlockProfile.parse(bad,R,300);}catch(RuntimeException expected){rejected=true;}
            check(rejected,"Reject malformed factor: "+bad);
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
        var p=FailureBlockProfile.parse("create:mechanical_bearing; base_chance_per_minute_factor=0; chance_per_start_factor=1; rpm_risk_weight_factor=0",R,300).profile();
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
