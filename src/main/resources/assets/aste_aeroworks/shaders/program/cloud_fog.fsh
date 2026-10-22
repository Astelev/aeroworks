#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D MainDepthSampler;
uniform sampler2D DhDepthSampler;
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

void main() {
    vec4 original = texture(DiffuseSampler, texCoord);
    float distance = distanceFromDepth(texture(MainDepthSampler, texCoord).r, InverseProjection);
    if (HasDhDepth != 0) {
        // Raw depths from these two projections cannot be compared directly.
        float dhDistance = distanceFromDepth(texture(DhDepthSampler, texCoord).r, InverseDhProjection);
        distance = min(distance, dhDistance);
    }
    float alpha = smoothstep(Visibility * 0.1, Visibility, distance) * Strength;
    fragColor = vec4(mix(original.rgb, FogColor, alpha), original.a);
}
