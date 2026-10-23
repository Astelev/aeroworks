package net.aste.aeroworks;

import net.aste.aeroworks.wind.WindParameters;
import net.aste.aeroworks.wind.WindWeather;
import net.aste.aeroworks.wind.WindWeatherBlend;
import net.aste.aeroworks.wind.ZonalWindModel;

public final class ZonalWindTest {
    private static final WindParameters P = WindParameters.DEFAULT;
    public static void main(String[] args) {
        surfaceRange(); weatherSpeed(); altitude(); cloudAlignment(); zones(); spatialContinuity();
        temporalContinuity(); weatherRetarget(); typeChange(); randomTurns(); deterministic(); invalidConfig();
        antipodalContinuity(); weatherFrameIndependence();
        System.out.println("Zonal wind: 14 field/weather/continuity checks passed");
    }
    private static ZonalWindModel.Result at(double x, double y, double z, double phase, WindWeatherBlend weather, double time) {
        var result = new ZonalWindModel.Result();
        ZonalWindModel.sample(P, 1234, phase, x,y,z,weather,time,result);
        return result;
    }
    private static WindWeatherBlend steady(double rain, double storm, double heading) {
        var weather = new WindWeather(1,rain,storm,heading,0,0);
        return new WindWeatherBlend(weather,weather,0,1);
    }
    private static void surfaceRange() {
        for (int i = -100; i <= 100; i++) {
            double speed = speed(at(i*53,64,i*31,i*.037,steady(0,0,0),10));
            require(speed >= 2 && speed <= 3, "Clear surface stays near 2–3 m/s");
        }
    }
    private static void weatherSpeed() {
        for (double y : new double[]{64,150,250,1000}) {
            double clear=speed(at(73,y,-90,.7,steady(0,0,0),10));
            double rain=speed(at(73,y,-90,.7,steady(1,0,0),10));
            double storm=speed(at(73,y,-90,.7,steady(1,1,0),10));
            require(storm > rain && rain > clear, "Storm > rain > clear at every altitude");
        }
    }
    private static void altitude() {
        var weather=steady(0,0,.6);
        double previous=0;
        for (int y=64;y<=250;y++) {
            double speed=speed(at(20,y,100,.8,weather,10));
            require(speed >= previous-1e-10,"Speed grows monotonically with height"); previous=speed;
        }
        near(at(20,250,100,.8,weather,10),at(20,4096,100,.8,weather,10),1e-12,"Height effect plateaus at Y=250");
        near(at(20,64,100,.8,weather,10),at(20,-64,100,.8,weather,10),1e-12,"Surface layer below base height");
    }
    private static void cloudAlignment() {
        double heading=-1.2;
        for(int i=0;i<200;i++) {
            var target=WindWeather.of(i+1,0,0,heading,1234,P.cloudDeviationDegrees());
            var v=at(i*17,250,-i*29,i*.03,new WindWeatherBlend(target,target,0,1),10);
            double error=Math.abs(Math.IEEEremainder(Math.atan2(v.z,v.x)-heading,2*Math.PI));
            require(error <= Math.toRadians(P.cloudDeviationDegrees())+1e-9,"Upper wind agrees with cloud motion");
        }
    }
    private static void zones() {
        var a=at(0,64,0,.3,steady(0,0,0),10);
        var b=at(1024,64,1024,.3,steady(0,0,0),10);
        require(distance(a,b)>.1,"Separate cells have distinct patterns");
    }
    private static void spatialContinuity() {
        for(double boundary:new double[]{-1024,-512,0,512,1024}) {
            near(at(boundary-1e-4,130,77,.9,steady(1,0,.2),10),
                    at(boundary+1e-4,130,77,.9,steady(1,0,.2),10),1e-5,"No jump at positive/negative grid seam");
            near(at(77,130,boundary-1e-4,.9,steady(1,0,.2),10),
                    at(77,130,boundary+1e-4,.9,steady(1,0,.2),10),1e-5,"No jump at Z seam");
        }
    }
    private static void temporalContinuity() {
        for(int i=0;i<100;i++) {
            double phase=i*.1;
            near(at(-512,170,1024,phase-1e-7,steady(0,0,.3),10),
                    at(-512,170,1024,phase+1e-7,steady(0,0,.3),10),1e-4,"Random epochs remain continuous");
        }
    }
    private static void weatherRetarget() {
        var first=WindWeatherBlend.CLEAR.retarget(WindWeather.of(22,1,1,Math.PI,1234,15),10,15);
        near(at(20,140,50,.4,WindWeatherBlend.CLEAR,10),at(20,140,50,.4,first,10),1e-10,"New front starts at previous wind");
        var second=first.retarget(WindWeather.of(55,0,0,-1.3,1234,15),14,15);
        near(at(20,140,50,.4,first,14),at(20,140,50,.4,second,14),1e-10,"Interrupted transition preserves current wind");
        require(second.current(100).rain()<1e-7,"Transition eventually reaches clear weather");
    }
    private static void typeChange() {
        var a=WindWeather.of(ZonalWindModel.typeKey("sc:cumulus"),0,0,.4,1234,15);
        var b=WindWeather.of(ZonalWindModel.typeKey("sc:cirrus"),0,0,.4,1234,15);
        var old=new WindWeatherBlend(a,a,0,1);
        var next=old.retarget(b,10,15);
        for(double height:new double[]{64,250})
            require(distance(at(73,height,90,.3,old,30),at(73,height,90,.3,next,30))>.001,
                    "Cloud type alone changes both layers, even without a rain/speed change");
    }
    private static void randomTurns() {
        for(double height:new double[]{64,250})
            require(distance(at(22,height,90,.1,steady(0,0,0),0),at(22,height,90,2.3,steady(0,0,0),0))>.01,
                    "Wind changes with time without weather changes");
    }
    private static void deterministic() {
        var a=at(-4000,200,3000,100.35,steady(0,0,-.2),10);
        for(int i=0;i<100;i++)near(a,at(-4000,200,3000,100.35,steady(0,0,-.2),10),0,"Repeatable seed/position/time");
    }
    private static void invalidConfig() {
        boolean rejected=false;
        try{new WindParameters(512,250,64,2.5,4,6,8,12,18,360,1,15,15);}
        catch(IllegalArgumentException expected){rejected=true;}
        require(rejected,"Invalid height ordering rejected");
    }
    private static void antipodalContinuity() {
        // Crossing the opposite heading must not flip a middle-altitude wind by 180 degrees.
        double phase=.2;
        var a=at(42,150,99,phase,steady(0,0,Math.PI-1e-8),10);
        var b=at(42,150,99,phase,steady(0,0,Math.PI+1e-8),10);
        near(a,b,1e-6,"Continuous unwrapped layer interpolation");
    }
    private static void weatherFrameIndependence() {
        var target=WindWeather.of(77,1,1,.2,1234,15);
        var once=WindWeatherBlend.CLEAR.retarget(target,0,15);
        var many=once;
        for(int second=1;second<=15;second++) {
            // Moving clouds keep changing heading while their rain/storm remain constant.
            var drifting=new WindWeather(77,1,1,.2+second*.001,target.surfaceTurn(),target.aloftTurn());
            many=many.retarget(drifting,second,15);
        }
        require(Math.abs(once.current(15).rain()-many.current(15).rain())<1e-10,
                "Repeated heading updates must not slow rain/storm transitions");
        require(many.current(15).rain()>.95,"About 95% response within configured transition time");
    }
    private static double speed(ZonalWindModel.Result v){return Math.hypot(v.x,v.z);}
    private static double distance(ZonalWindModel.Result a,ZonalWindModel.Result b){return Math.hypot(a.x-b.x,a.z-b.z);}
    private static void near(ZonalWindModel.Result a,ZonalWindModel.Result b,double tolerance,String label){require(distance(a,b)<=tolerance,label);}
    private static void require(boolean condition,String label){if(!condition)throw new AssertionError(label);}
}
