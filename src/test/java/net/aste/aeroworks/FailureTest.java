package net.aste.aeroworks;

import net.aste.aeroworks.failure.FailureRisk;
import net.aste.aeroworks.failure.FailureState;

public final class FailureTest {
    private static final FailureRisk R=FailureRisk.DEFAULT;
    private static void check(boolean ok,String why) { if(!ok)throw new AssertionError(why); }
    private static void near(double a,double b) {check(Math.abs(a-b)<1e-10,"Mismatch "+a+" / "+b);}
    public static void main(String[] args) {
        double base=R.probability(64,0,64,false,60);
        near(base,.3);
        near(R.probability(0,200,1000,true,60),0);
        near(R.probability(-64,0,64,false,60),base);
        check(R.probability(128,0,64,false,60)>base,"Higher RPM increases risk");
        check(R.probability(16,0,64,false,60)<base,"Slow machinery has lower risk");
        check(R.probability(64,40,64,false,60)>base,"Aircraft speed increases risk");
        check(R.probability(64,0,400,false,60)>base,"World altitude increases risk");
        near(R.probability(64,0,-500,false,60),base);
        double perSecond=R.probability(128,40,300,false,1);
        double turbulent=R.probability(128,40,300,true,1);
        near(1-turbulent,Math.pow(1-perSecond,R.turbulenceMultiplier()));
        double minute=R.probability(128,40,300,false,60);
        near(1-minute,Math.pow(1-perSecond,60));
        near(1-perSecond,Math.pow(1-R.probability(128,40,300,false,.05),20));
        check(R.probability(1e100,1e100,1e100,true,1)>=0 && R.probability(1e100,1e100,1e100,true,1)<=1,"Huge finite inputs remain bounded");
        near(R.probability(Double.NaN,40,300,true,1),0);
        near(R.probability(64,Double.NaN,300,true,1),0);
        near(R.probability(64,40,Double.NaN,true,1),0);
        near(R.probability(64,40,300,true,0),0);
        var off=new FailureRisk(0,64,1,40,1,128,256,1,8,256);
        near(off.probability(256,200,1000,true,60),0);
        var certain=new FailureRisk(1,64,1,40,1,128,256,1,8,256);
        near(certain.probability(64,0,64,false,1),1);
        var a=new FailureState();var b=new FailureState();
        check(!a.tryFail(.3,.5),"Failed roll leaves machinery working");
        check(a.tryFail(.3,.1)&&a.failed(),"Successful roll persists as a failed state");
        check(!b.failed(),"Blocks fail independently");
        check(!a.tryFail(1,0),"Failed blocks do not fail twice");
        check(a.repair(300)&&!a.failed(),"Repair restores operation");
        check(!a.tryFail(1,0),"Repair protection prevents immediate repeated failure");
        a.elapse(280);check(!a.canFail(),"Protection is not exhausted early");
        a.elapse(20);check(a.canFail()&&a.tryFail(1,0),"Risk resumes after protection expires");
        check(!a.tryFail(Double.NaN,0),"Invalid probability never fails a block");
        check(a.repair(0)&&a.canFail(),"Zero protection is supported");
        check(!a.tryFail(.3,Double.NaN),"Invalid random value does not fail a block");
        check(!a.repair(300),"Healthy blocks do not get free protection from repeated wrenching");
        boolean rejected=false;
        try {new FailureRisk(.3,0,1,40,1,128,256,1,8,256);}catch(IllegalArgumentException e){rejected=true;}
        check(rejected,"Reject invalid reference speed");
        System.out.printf(java.util.Locale.ROOT,"Failures: time-independent probability, work gating, RPM/speed/altitude/turbulence risk, independent blocks and repair protection passed; test flight risk %.2f%%/s, turbulent %.2f%%/s%n",perSecond*100,turbulent*100);
    }
}
