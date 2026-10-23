package net.aste.aeroworks.wind;

import net.minecraft.server.level.ServerLevel;

/** World coordinates in blocks; time in seconds, velocity in blocks/second. */
public record WindSample(ServerLevel level, double x, double y, double z, double timeSeconds) {}
