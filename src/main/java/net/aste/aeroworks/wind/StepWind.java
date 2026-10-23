package net.aste.aeroworks.wind;

import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Per-body scratch storage. No allocations or field queries inside the surface loop. */
public final class StepWind {
    private final Vector3d wind = new Vector3d();
    private final Vector3d relative = new Vector3d();
    private boolean ready;
    public void begin() { ready = false; }
    public boolean ready() { return ready; }
    public Vector3d sampleBuffer() { return wind; }
    public void prepare(Vector3dc velocity) {
        relative.set(velocity).sub(wind);
        ready = true;
    }
    public Vector3dc velocity(Vector3dc original) {
        return wind.lengthSquared() == 0 ? original : relative;
    }
}
