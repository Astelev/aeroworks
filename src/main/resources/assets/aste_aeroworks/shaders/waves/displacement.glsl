// This is inserted into Sodium's vertex shader. The fragment shader, including
// Deep Seas' hull-depth discard, is deliberately left untouched.
uniform sampler2D aste_WaveShore;
uniform vec3 aste_WaveGrid; // camera X/Z relative to map origin, camera Y
uniform vec4 aste_WaveProfile; // kX, kZ, camera/time phase, amplitude
uniform vec4 aste_WaveStill;
uniform vec4 aste_WaveFlow;
uniform vec4 aste_WaveOverlay;
uniform float aste_WaveEnabled;

bool aste_waveSprite(vec2 uv, vec4 bounds) {
    return all(greaterThan(uv, bounds.xy)) && all(lessThan(uv, bounds.zw));
}
vec3 aste_wavePosition(vec3 p, vec2 uv) {
    if (aste_WaveEnabled < 0.5) return p;
    if (!(aste_waveSprite(uv, aste_WaveStill) || aste_waveSprite(uv, aste_WaveFlow)
          || aste_waveSprite(uv, aste_WaveOverlay))) return p;
    vec2 cell = (p.xz + aste_WaveGrid.xy) / 4.0;
    if (any(lessThan(cell, vec2(0))) || any(greaterThan(cell, vec2(128)))) return p;
    // Interpolate weight, but never interpolate water heights across separate lakes.
    vec2 field = texture(aste_WaveShore, (cell + 0.5) / 129.0).rg;
    float waterY = texelFetch(aste_WaveShore, ivec2(floor(cell + 0.5)), 0).r;
    if (abs(p.y + aste_WaveGrid.z - waterY) > 0.04) return p;
    float edge = min(min(cell.x, cell.y), min(128.0 - cell.x, 128.0 - cell.y));
    float weight = field.g * smoothstep(0.0, 8.0, edge);
    p.y += sin(dot(p.xz, aste_WaveProfile.xy) + aste_WaveProfile.z)
           * aste_WaveProfile.w * weight;
    return p;
}
