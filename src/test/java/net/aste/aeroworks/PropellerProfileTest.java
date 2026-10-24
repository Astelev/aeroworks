package net.aste.aeroworks;

import net.aste.aeroworks.atmosphere.AtmosphereProfile;
import net.aste.aeroworks.atmosphere.PropellerProfile;

public final class PropellerProfileTest {
    private static final PropellerProfile P=PropellerProfile.DEFAULT;
    private static void check(boolean ok,String why) { if(!ok)throw new AssertionError(why); }
    private static void near(double a,double b,double tolerance) { check(Math.abs(a-b)<=tolerance,"Expected "+a+" / "+b); }
    public static void main(String[] args) {
        for(double y:new double[]{-10000,-64,0,64,100,128})near(P.efficiency(y),1,0);
        double previous=1;
        for(int y=128;y<=4096;y++) {
            double value=P.efficiency(y);
            check(Double.isFinite(value)&&value>=0&&value<=previous,"Finite monotonic propeller penalty");
            previous=value;
        }
        near(P.efficiency(384),Math.exp(-1),1e-12);
        near(P.efficiency(128-1e-4),P.efficiency(128+1e-4),1e-12);
        check((1-P.efficiency(128+1e-3))/1e-3<1e-7,"Zero initial slope avoids an altitude step");
        var air=AtmosphereProfile.DEFAULT;
        for(int y=129;y<=1000;y++) {
            double wing=air.density(y),propeller=wing*P.efficiency(y);
            check(propeller<wing,"Propeller thrust weakens faster than wing density");
        }
        check(air.density(600)>.59 && air.density(600)*P.efficiency(600)<.02,"High flight leaves wing lift but little propeller thrust");
        check(P.efficiency(1000)>0,"No artificial hard altitude cutoff");
        check(new PropellerProfile(128,512).efficiency(600)>P.efficiency(600),"Larger scale preserves thrust longer");
        check(new PropellerProfile(256,256).efficiency(600)>P.efficiency(600),"Later onset preserves thrust longer");
        near(P.efficiency(Double.NaN),0,0);near(P.efficiency(Double.POSITIVE_INFINITY),0,0);near(P.efficiency(Double.NEGATIVE_INFINITY),1,0);
        for(double[] values:new double[][]{{128,0},{128,-1},{128,Double.NaN},{Double.POSITIVE_INFINITY,256}}) {
            boolean failed=false;
            try {new PropellerProfile(values[0],values[1]);}catch(IllegalArgumentException expected){failed=true;}
            check(failed,"Reject invalid profile");
        }
        System.out.println("Propellers: low-altitude preservation, smooth onset, monotonicity, wing/thrust separation, high-altitude balance, configuration and finite limits passed");
        for(double y:new double[]{128,250,400,600}) {
            System.out.printf(java.util.Locale.ROOT,"Y %.0f: wing density %.2f%%, total propeller multiplier %.2f%%%n",y,100*air.density(y),100*air.density(y)*P.efficiency(y));
        }
    }
}
