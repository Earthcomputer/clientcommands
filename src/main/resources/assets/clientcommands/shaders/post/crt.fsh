#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

out vec4 fragColor;

void main() {
    vec2 oneTexel = 1.0 / InSize;

    float aberr = 0.002;
    vec3 col;
    col.r = texture(InSampler, texCoord + vec2(aberr, 0.0)).r;
    col.g = texture(InSampler, texCoord).g;
    col.b = texture(InSampler, texCoord - vec2(aberr, 0.0)).b;

    // Add scanlines
    float scan = sin(texCoord.y * 800.0) * 0.1; // adjust frequency
    col *= 1.0 - scan * 0.3;

    // Vignette
    float vignette = smoothstep(1.2, 0.6, length(texCoord - 0.5));
    col *= vignette;

    fragColor = vec4(col, 1.0);
}
