package net.aste.aeroworks.wind;

import net.minecraft.server.level.ServerLevel;

/** World position/time; body id selects a server-prepared weather snapshot. */
public record WindSample(ServerLevel level, double x, double y, double z, double timeSeconds, int bodyId) {
    public WindSample(ServerLevel level, double x, double y, double z, double timeSeconds) {
        this(level, x, y, z, timeSeconds, -1);
    }
}
