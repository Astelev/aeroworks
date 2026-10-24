package net.aste.aeroworks.wind;

import org.joml.Vector3d;

/** Called on the physics path: providers must be thread-safe and must not mutate the world. */
@FunctionalInterface
public interface WindField {
    void sample(WindSample sample, Vector3d result);
}
