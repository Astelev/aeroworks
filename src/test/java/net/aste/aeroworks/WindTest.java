package net.aste.aeroworks;

import dev.ryanhcode.sable.companion.math.Pose3d;
import net.aste.aeroworks.wind.WindField;
import net.aste.aeroworks.wind.WindKinematics;
import net.aste.aeroworks.wind.WindSample;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class WindTest {
    public static void main(String[] args) {
        calm(); stationary(); coflow(); headwind(); crosswind(); immutability();
        angularMotion(); worldPosition(); nestedPosition(); positionProvider();
        System.out.println("Wind: 10 kinematics/provider checks passed");
    }
    private static void calm() {
        var velocity = new Vector3d(1,2,3);
        require(WindKinematics.relativeVelocity(velocity, new Vector3d()) == velocity, "Calm preserves original velocity object");
    }
    private static void stationary() {
        near(new Vector3d(-8,0,0), WindKinematics.relativeVelocity(new Vector3d(), new Vector3d(8,0,0)), "Stationary sails feel headwind");
    }
    private static void coflow() {
        near(new Vector3d(), WindKinematics.relativeVelocity(new Vector3d(8,0,0), new Vector3d(8,0,0)), "Moving with air gives zero relative flow");
    }
    private static void headwind() {
        near(new Vector3d(-16,0,0), WindKinematics.relativeVelocity(new Vector3d(-8,0,0), new Vector3d(8,0,0)), "Headwind adds relative speed");
    }
    private static void crosswind() {
        near(new Vector3d(2,-3,-4), WindKinematics.relativeVelocity(new Vector3d(2,0,1), new Vector3d(0,3,5)), "Sidewind/updraft retain all world axes");
    }
    private static void immutability() {
        var body = new Vector3d(10,2,3);
        var wind = new Vector3d(8,0,0);
        near(new Vector3d(2,2,3), WindKinematics.relativeVelocity(body,wind), "First sail");
        near(new Vector3d(2,2,3), WindKinematics.relativeVelocity(body,wind), "Second sail must not subtract wind twice");
        near(new Vector3d(10,2,3), body, "Body velocity not mutated");
        near(new Vector3d(8,0,0), wind, "Wind provider output not mutated");
        var shift = new Vector3d(100,-5,70);
        near(WindKinematics.relativeVelocity(body,wind), WindKinematics.relativeVelocity(new Vector3d(body).add(shift), new Vector3d(wind).add(shift)), "Galilean invariance");
    }
    private static void angularMotion() {
        var body = new Vector3d(5,0,0);
        var angular = new Vector3d(0,2,0);
        var offset = new Vector3d(3,0,0);
        var wind = new Vector3d(8,0,0);
        var point = new Vector3d(WindKinematics.relativeVelocity(body,wind)).add(new Vector3d(angular).cross(offset));
        near(new Vector3d(-3,0,-6), point, "Original angular point velocity retained after wind subtraction");
        near(new Vector3d(0,2,0), angular, "Angular velocity not mutated");
    }
    private static void worldPosition() {
        var pose = new Pose3d();
        pose.position().set(100,200,300);
        near(new Vector3d(101.5,202.5,303.5), WindKinematics.worldPosition(new Vector3d(1.5,2.5,3.5), null, pose, new Vector3d()), "Sail center sampled in world, not storage plot");
    }
    private static void nestedPosition() {
        var local = new Pose3d();
        local.orientation().rotationZ(Math.PI/2);
        local.position().set(2,0,0);
        var world = new Pose3d();
        world.orientation().rotationY(Math.PI/2);
        world.position().set(100,200,300);
        near(new Vector3d(100,201,298), WindKinematics.worldPosition(new Vector3d(1,0,0), local, world, new Vector3d()), "Nested bearing rotation then sublevel pose");
    }
    private static void positionProvider() {
        WindField gradient = (sample,out) -> out.set(sample.x()/100, sample.y()/100, sample.timeSeconds());
        var a = new WindSample(null,100,200,300,5);
        var b = new WindSample(null,300,800,900,7);
        var result = new Vector3d();
        gradient.sample(a,result);
        near(new Vector3d(1,2,5),result,"Position/height/time available to future fields");
        gradient.sample(b,result);
        near(new Vector3d(3,8,7),result,"Provider can vary across world points");
    }
    private static void near(Vector3dc expected, Vector3dc actual, String label) {
        require(new Vector3d(expected).sub(actual).length() < 1e-8,label + ": " + actual);
    }
    private static void require(boolean condition,String label) {
        if (!condition) throw new AssertionError(label);
    }
}
