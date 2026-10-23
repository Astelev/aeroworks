package net.aste.aeroworks.wind;

import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Per-body scratch storage. No allocations or field queries inside the surface loop. */
public final class StepWind {
    private final Vector3d wind = new Vector3d();
    private final Vector3d torque = new Vector3d();
    public Vector3d torqueBuffer() { return torque; }
    private final Vector3d relative = new Vector3d();
    private boolean ready;
    private double pressureCoefficient;
    public void begin() { ready = false; }
    public boolean ready() { return ready; }
    public Vector3d sampleBuffer() { return wind; }
    public void prepare(Vector3dc velocity) { prepare(velocity, 1); }
    public void prepare(Vector3dc velocity, double coefficient) {
        pressureCoefficient = coefficient;
        relative.set(velocity).sub(wind);
        ready = true;
    }
    /** Sable and Lift Patch multiply aerodynamic impulses by timeStep linearly. */
    public double aerodynamicTimeStep(double timeStep) { return timeStep * pressureCoefficient; }
    public Vector3dc velocity(Vector3dc original) {
        return wind.lengthSquared() == 0 ? original : relative;
    }
}
