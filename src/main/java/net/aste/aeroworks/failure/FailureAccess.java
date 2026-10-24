package net.aste.aeroworks.failure;

/** Implemented by Create kinetic block entities; persistence/sync use their normal NBT path. */
public interface FailureAccess {
    FailureState aste_aeroworks$failureState();
    void aste_aeroworks$syncFailure();
    float aste_aeroworks$failureRPM();
}
