package net.aste.aeroworks.cloud;

/** Scalar CPU adaptation of psrdnoise (Stefan Gustavson and Ian McEwan, MIT).
 * Matches the periodic 3D shader bundled with Simple Clouds 0.7.3.
 * Copyright and permission notice: META-INF/licenses/psrdnoise-MIT.txt.
 */
public final class PsrdNoise {
    private PsrdNoise() {}

    public static float sample(float x, float y, float z, float alpha) {
        float ux = y + z, uy = x + z, uz = x + y;
        float ix = floor(ux), iy = floor(uy), iz = floor(uz);
        float fx = ux - ix, fy = uy - iy, fz = uz - iz;
        float a = fy >= fx ? 1 : 0, b = fz >= fy ? 1 : 0, c = fz >= fx ? 1 : 0;
        // GLSL step(f0.xyx, f0.yzz) = (fy>=fx, fz>=fy, fz>=fx).
        float o1x = Math.min(1 - c, 1 - a), o1y = Math.min(a, 1 - b), o1z = Math.min(b, c);
        float o2x = Math.max(1 - c, 1 - a), o2y = Math.max(a, 1 - b), o2z = Math.max(b, c);
        float sum = 0;
        float ca = (float) Math.cos(alpha), sa = (float) Math.sin(alpha);
        for (int corner = 0; corner < 4; corner++) {
            float cx = ix + (corner == 1 ? o1x : corner == 2 ? o2x : corner == 3 ? 1 : 0);
            float cy = iy + (corner == 1 ? o1y : corner == 2 ? o2y : corner == 3 ? 1 : 0);
            float cz = iz + (corner == 1 ? o1z : corner == 2 ? o2z : corner == 3 ? 1 : 0);
            float vx = (-cx + cy + cz) * .5f, vy = (cx - cy + cz) * .5f, vz = (cx + cy - cz) * .5f;
            float dx = x - vx, dy = y - vy, dz = z - vz;
            float w = Math.max(.5f - (dx * dx + dy * dy + dz * dz), 0);
            if (w == 0) continue;
            // TILE_PERIOD in cube_mesh.comp is (32, 64, 32).
            vx = mod(vx, 32); vy = mod(vy, 64); vz = mod(vz, 32);
            cx = floor(vy + vz + .5f); cy = floor(vx + vz + .5f); cz = floor(vx + vy + .5f);
            float hash = permute(permute(permute(cz) + cy) + cx);
            float theta = hash * 3.883222077f, sz = hash * -.006920415f + .996539792f;
            float ct = (float) Math.cos(theta), st = (float) Math.sin(theta);
            float radius = (float) Math.sqrt(Math.max(0, 1 - sz * sz));
            float px = ct * radius, py = st * radius;
            float gx = px, gy = py, gz = sz;
            if (alpha != 0) {
                float psi = hash * .108705628f, sp = (float) Math.sin(psi), cp = (float) Math.cos(psi);
                float ctp = st * sp - ct * cp;
                float qx = ctp * st * (1 - sz) + sp * sz;
                float qy = -ctp * ct * (1 - sz) + cp * sz;
                float qz = -(py * cp + px * sp);
                gx = ca * px + sa * qx; gy = ca * py + sa * qy; gz = ca * sz + sa * qz;
            }
            sum += w * w * w * (gx * dx + gy * dy + gz * dz);
        }
        return 39.5f * sum;
    }

    private static float floor(float v) { return (float) Math.floor(v); }
    private static float mod(float v, float divisor) { return v - floor(v / divisor) * divisor; }
    private static float permute(float v) { v = mod(v, 289); return mod((v * 34 + 10) * v, 289); }
}
