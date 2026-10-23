#version 150

uniform sampler2D DiffuseSampler;
in vec2 texCoord;
out vec4 fragColor;

void main() {
    // Replace the cleared main target; never alpha-blend a completed scene.
    fragColor = texture(DiffuseSampler, texCoord);
}
