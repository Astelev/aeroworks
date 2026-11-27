package net.aste.aeroworks;

import net.aste.aeroworks.compat.deepseas.SailHeadingMath;
import org.joml.Vector3d;

public final class SailHeadingTest {
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static void near(double a,double b,String why){check(Math.abs(a-b)<1e-10,why+": "+a+" != "+b);}
    public static void main(String[] args){
        var sum=new Vector3d();
        SailHeadingMath.add(sum,1,0,0,4,0);SailHeadingMath.add(sum,0,0,1,2,0);
        check(SailHeadingMath.normalize(sum),"Area-weighted mean exists");near(sum.x/sum.z,2,"Larger sail area weighs proportionally more");
        sum.zero();SailHeadingMath.add(sum,1,0,0,4,1);SailHeadingMath.normalize(sum);near(sum.x,-1,"Support side matches native vane sign");
        SailHeadingMath.add(sum,1,0,0,1,-1);check(!SailHeadingMath.normalize(sum),"Opposing sails cancel instead of inventing a hull-axis heading");
        sum.zero();SailHeadingMath.add(sum,0,1,0,10,0);check(!SailHeadingMath.normalize(sum),"Vertical-only normal does not choose a horizontal bow");
        var forward=new Vector3d(1,0,0);var out=new Vector3d();
        SailHeadingMath.drift(forward,-10,0,.15,out);near(out.x,1,"Headwind cannot flip the designated bow");near(out.z,0,"No headwind lateral component");
        SailHeadingMath.drift(forward,0,10,.15,out);near(out.z/out.x,.15,"Crosswind leeway is limited to atan(0.15)");
        check(out.x>0&&out.z>0,"Crosswind nudges thrust toward wind without steering it backwards");
        for(double angle:new double[]{0,.4,Math.PI/2,Math.PI,4.7}){
            var f=new Vector3d(Math.cos(angle),0,Math.sin(angle));
            double wx=-Math.sin(angle),wz=Math.cos(angle);
            SailHeadingMath.drift(f,wx,wz,.15,out);
            near(out.x,(f.x+.15*wx)/Math.sqrt(1+.15*.15),"World-space rotation covariance X");
            near(out.z,(f.z+.15*wz)/Math.sqrt(1+.15*.15),"World-space rotation covariance Z");
        }
        near(SailHeadingMath.alignment(1,0,5,0),1,"Aligned vane");
        near(SailHeadingMath.alignment(1,0,-5,0),-1,"Upwind vane");
        near(SailHeadingMath.alignment(1,0,0,5),0,"Crosswind vane");
        check(Double.isNaN(SailHeadingMath.alignment(0,0,5,0)),"No mean sail direction has undefined alignment");
        SailHeadingMath.drift(forward,0,0,.15,out);near(out.x,1,"Calm weather retains sail bow");
        System.out.println("Ship heading: area/support weights, cancellation, rotated ships, headwind bow, bounded leeway and vane alignment passed");
    }
}
