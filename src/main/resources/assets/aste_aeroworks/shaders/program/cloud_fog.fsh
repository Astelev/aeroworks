#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D MainDepthSampler;
uniform sampler2D DhDepthSampler;
uniform sampler2D CloudsSampler;
uniform sampler2D AccumSampler;
uniform sampler2D RevealSampler;
uniform int HasTransparency;
uniform mat4 InverseProjection;
uniform mat4 InverseDhProjection;
uniform int HasDhDepth;
uniform float Visibility;
uniform float Strength;
uniform vec3 FogColor;

in vec2 texCoord;
out vec4 fragColor;

float distanceFromDepth(float depth, mat4 inverseProjection) {
    if (depth >= 1.0) return 1.0e10;
    vec4 view = inverseProjection * vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return length(view.xyz / view.w);
}

float fogAt(float distance) {
    return smoothstep(Visibility * 0.1, Visibility, distance) * Strength;
}

void main() {
    vec4 original = texture(DiffuseSampler, texCoord);
    float mainDepth = texture(MainDepthSampler, texCoord).r;
    bool hasMainSurface = mainDepth >= 0.0 && mainDepth < 1.0;
    float mainDistance = distanceFromDepth(mainDepth, InverseProjection);
    float backgroundDistance = mainDistance;
    float dhDistance = 1.0e10;
    vec4 cloud = vec4(0.0);
    if (HasDhDepth != 0) {
        dhDistance = distanceFromDepth(texture(DhDepthSampler, texCoord).r, InverseDhProjection);
        cloud = texture(CloudsSampler, texCoord);
        // Native terrain owns its fog distance. The DH target mixes LOD and
        // opaque cloud depth; neither may shade hidden caves over native land.
        if (!hasMainSurface) {
            // If depth belongs to a cloud, it says nothing about the sky/LOD
            // behind it. Never give the background that nearby cloud distance.
            backgroundDistance = cloud.a > 0.0 ? 1.0e10 : dhDistance;
        }
    }
    float backgroundFog = fogAt(backgroundDistance);
    // At full immersion every cloud layer is also fully fogged. Skip the
    // transparency buffers and compositing math for already opaque fog pixels.
    if (backgroundFog >= 1.0 && Strength >= 1.0) {
        fragColor = vec4(FogColor, 1.0);
        return;
    }
    vec3 color = mix(original.rgb, FogColor, backgroundFog);
    if (HasDhDepth != 0 && (dhDistance < mainDistance || !hasMainSurface)) {
        // Shade each layer BEFORE compositing it. At full immersion dissolve
        // even nearby cloud mesh faces into the volumetric fog color, without
        // changing visibility for nearby terrain, entities or particles.
        float cloudFog = max(fogAt(dhDistance), Strength * Strength);
        vec3 cloudColor = mix(cloud.rgb, FogColor, cloudFog);
        color = mix(color, cloudColor, cloud.a);
        if (HasTransparency != 0) {
            float reveal = texture(RevealSampler, texCoord).r;
            vec4 accum = texture(AccumSampler, texCoord);
            if (any(isinf(accum.rgb))) accum.rgb = vec3(accum.a);
            vec3 transparentColor = mix(accum.rgb / max(accum.a, .00001), FogColor, cloudFog);
            color = mix(transparentColor, color, reveal);
        }
    }
    // The completed world is opaque even if sky/DH input had alpha=0.
    fragColor = vec4(color, 1.0);
}
