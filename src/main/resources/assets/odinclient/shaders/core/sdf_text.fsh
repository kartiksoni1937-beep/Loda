#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

// Glyph atlases store a signed distance field in alpha: 0.5 is the outline of the glyph.
void main() {
    float distance = texture(Sampler0, texCoord0).a;
    // Half a screen pixel of anti-aliasing, whatever the text size or resolution.
    float width = max(fwidth(distance) * 0.6, 1e-4);
    // A touch of extra weight keeps small text as solid as hinted text.
    float edge = 0.5 - min(0.04, width * 0.5);
    float coverage = smoothstep(edge - width, edge + width, distance);
    vec4 color = vertexColor;
    color.a *= coverage;
    if (color.a <= 0.002) {
        discard;
    }
    fragColor = color * ColorModulator;
}
