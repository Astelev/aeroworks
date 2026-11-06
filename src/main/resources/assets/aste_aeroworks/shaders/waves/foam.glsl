in float v_asteWaveFoam;
in vec2 v_asteWaveFoamUV;
float aste_foamHash(vec2 p) {
    p=mod(p,vec2(384.0));
    vec3 q=fract(vec3(p.xyx)*0.1031);q+=dot(q,q.yzx+33.33);
    return fract((q.x+q.y)*q.z);
}
float aste_foamNoise(vec2 p) {
    vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);
    return mix(mix(aste_foamHash(i),aste_foamHash(i+vec2(1,0)),f.x),
               mix(aste_foamHash(i+vec2(0,1)),aste_foamHash(i+vec2(1,1)),f.x),f.y);
}
void aste_applyFoam(inout vec4 color) {
    if(v_asteWaveFoam<=0.001)return;
    float patch=smoothstep(0.2,0.8,aste_foamNoise(v_asteWaveFoamUV));
    float coverage=clamp(v_asteWaveFoam*(0.25+0.75*patch),0.0,1.0);
    float light=max(v_Color.r,max(v_Color.g,v_Color.b));
    color.rgb=mix(color.rgb,vec3(0.9,0.95,1.0)*light,coverage);
    color.a=mix(color.a,max(color.a,0.85),coverage);
}
