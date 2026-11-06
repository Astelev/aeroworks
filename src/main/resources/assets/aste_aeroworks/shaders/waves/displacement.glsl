// This is inserted into Sodium's vertex shader. The fragment shader, including
// Deep Seas' hull-depth discard, is deliberately left untouched.
uniform sampler2D aste_WaveShore;
uniform vec3 aste_WaveGrid; // camera X/Z relative to map origin, camera Y
uniform vec4 aste_WaveProfile; // kX, kZ, camera/time phase, amplitude
uniform vec4 aste_WaveStill;
uniform vec4 aste_WaveFlow;
uniform vec4 aste_WaveOverlay;
uniform float aste_WaveEnabled;
uniform float aste_FoamStrength;
uniform vec2 aste_FoamOrigin;
out float v_asteWaveFoam;
out vec2 v_asteWaveFoamUV;
float aste_waveEnergy;
float aste_waveStorm;

// CPU counterpart: WaveSpectrum / WaveShape. Integer hash is identical on both sides.
uniform float aste_WaveDetailed;
uniform float aste_WaveWarp; // horizontal distortion in blocks
uniform vec4 aste_WaveDetail; // full depth, noise strength, chop threshold, chop fraction
uniform vec4 aste_WaveChopA; // kx, kz, camera/time phase, unused
uniform vec4 aste_WaveChopB;
uniform sampler2D aste_WaveWind;
uniform vec3 aste_WaveWindGrid; // camera minus wind-grid origin X/Z, time minus epoch
uniform vec4 aste_WaveNoiseA; // camera fractional noise coordinate X/Z, time fraction, 1/scale
uniform vec4 aste_WaveNoiseB;
uniform ivec3 aste_WaveNoiseOriginA;
uniform ivec3 aste_WaveNoiseOriginB;
uniform uint aste_WaveSeed;

float aste_hash(ivec3 c, uint seed) {
    uint h=uint(c.x)*0x632be5abu ^ uint(c.y)*0x85157af5u ^ uint(c.z)*0x9e3779b9u ^ seed;
    h=(h^(h>>16u))*0x7feb352du; h=(h^(h>>15u))*0x846ca68bu; h^=h>>16u;
    return float(h>>8u)*(1.0/16777215.0)*2.0-1.0;
}
float aste_noise(vec3 p, ivec3 origin, uint seed) {
    ivec3 c=ivec3(floor(p))+origin; vec3 f=fract(p); f=f*f*(3.0-2.0*f);
    float a=mix(mix(aste_hash(c,seed),aste_hash(c+ivec3(1,0,0),seed),f.x),
                mix(aste_hash(c+ivec3(0,1,0),seed),aste_hash(c+ivec3(1,1,0),seed),f.x),f.y);
    float b=mix(mix(aste_hash(c+ivec3(0,0,1),seed),aste_hash(c+ivec3(1,0,1),seed),f.x),
                mix(aste_hash(c+ivec3(0,1,1),seed),aste_hash(c+ivec3(1,1,1),seed),f.x),f.y);
    return mix(a,b,f.z);
}
float aste_windNode(ivec2 c) {
    if(any(lessThan(c,ivec2(0)))||any(greaterThanEqual(c,ivec2(11))))return 0.0;
    vec4 state=texelFetch(aste_WaveWind,c,0);
    return mix(state.r,state.g,smoothstep(0.0,2.0,aste_WaveWindGrid.z-state.b))*state.a;
}
float aste_windRatio(vec2 p) {
    vec2 c=(p+aste_WaveWindGrid.xy)/64.0;ivec2 i=ivec2(floor(c));vec2 f=fract(c);
    return mix(mix(aste_windNode(i),aste_windNode(i+ivec2(1,0)),f.x),
               mix(aste_windNode(i+ivec2(0,1)),aste_windNode(i+ivec2(1,1)),f.x),f.y);
}
float aste_waveHeight(vec2 p,float depth) {
    aste_waveEnergy=1.0;aste_waveStorm=0.0;
    float phase=dot(p,aste_WaveProfile.xy)+aste_WaveProfile.z;
    if(aste_WaveDetailed<0.5)return sin(phase)*aste_WaveProfile.w;
    float ratio=aste_windRatio(p);
    float energy=sqrt(max(0.0,ratio))*smoothstep(0.0,aste_WaveDetail.x,depth);
    aste_waveEnergy=energy;
    if(energy==0.0)return 0.0;
    float n=aste_WaveDetail.y==0.0&&aste_WaveWarp==0.0?0.0:aste_noise(vec3(p*aste_WaveNoiseA.w+aste_WaveNoiseA.xy,aste_WaveNoiseA.z),aste_WaveNoiseOriginA,aste_WaveSeed);
    vec2 warp=vec2(0.0);
    if(aste_WaveWarp>0.0) {
        float nz=aste_noise(vec3(p*aste_WaveNoiseA.w+aste_WaveNoiseA.xy,aste_WaveNoiseA.z),aste_WaveNoiseOriginA,aste_WaveSeed^0x68bc21ebu);
        warp=vec2(n,nz)*aste_WaveWarp;
        phase+=dot(warp,aste_WaveProfile.xy);
    }
    float gate=smoothstep(aste_WaveDetail.z,1.0,ratio),chop=0.0;
    aste_waveStorm=gate;
    float s=sin(phase+n*aste_WaveDetail.y*0.6),a=0.24*gate;
    float mainWave=(s+a*(2.0*s*s-1.0))/(1.0+a)*(1.0+n*aste_WaveDetail.y);
    if(gate>0.0&&aste_WaveDetail.w>0.0) {
        float jitter=aste_noise(vec3(p*aste_WaveNoiseB.w+aste_WaveNoiseB.xy,aste_WaveNoiseB.z),aste_WaveNoiseOriginB,aste_WaveSeed^0x5bd1e995u);
        chop=(0.7*sin(dot(p+warp,aste_WaveChopA.xy)+aste_WaveChopA.z+1.8*jitter)
             +0.3*sin(dot(p+warp,aste_WaveChopB.xy)+aste_WaveChopB.z-1.2*jitter))
             *(0.55+0.45*jitter)*aste_WaveDetail.w*gate;
    }
    return aste_WaveProfile.w*energy*(mainWave+chop);
}

bool aste_waveSprite(vec2 uv, vec4 bounds) {
    return all(greaterThanEqual(uv, bounds.xy)) && all(lessThanEqual(uv, bounds.zw));
}
vec3 aste_wavePosition(vec3 p, vec2 uv) {
    v_asteWaveFoam=0.0;v_asteWaveFoamUV=vec2(0.0);
    if (aste_WaveEnabled < 0.5) return p;
    if (!(aste_waveSprite(uv, aste_WaveStill) || aste_waveSprite(uv, aste_WaveFlow)
          || aste_waveSprite(uv, aste_WaveOverlay))) return p;
    vec2 cell = (p.xz + aste_WaveGrid.xy) / 4.0;
    if (any(lessThan(cell, vec2(0))) || any(greaterThan(cell, vec2(128)))) return p;
    // Interpolate weight, but never interpolate water heights across separate lakes.
    vec4 field = texture(aste_WaveShore, (cell + 0.5) / 129.0);
    float waterBlockY = texelFetch(aste_WaveShore, ivec2(floor(cell + 0.5)), 0).r;
    float top = p.y + aste_WaveGrid.z - waterBlockY;
    // Source water tops are usually 8/9-.001, but other meshers can use 1.0.
    // Keep the bottom/lower blocks fixed; animate only the upper edge of surface water.
    if (top < 0.75 || top > 1.04) return p;
    float edge = min(min(cell.x, cell.y), min(128.0 - cell.x, 128.0 - cell.y));
    float weight = field.g * smoothstep(0.0, 8.0, edge);
    float height=aste_waveHeight(p.xz,field.b);
    float crest=smoothstep(0.45,0.92,height/max(0.001,aste_WaveProfile.w*aste_waveEnergy));
    v_asteWaveFoam=crest*weight*aste_FoamStrength*smoothstep(0.01,0.1,aste_WaveProfile.w*aste_waveEnergy)*(0.65+0.35*aste_waveStorm);
    v_asteWaveFoamUV=(p.xz+aste_FoamOrigin)*1.5;
    p.y += height*weight;
    return p;
}
