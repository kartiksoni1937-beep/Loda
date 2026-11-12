#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;
in vec2 localPos;
flat in vec4 shape;
flat in float border;
flat in int mode;

out vec4 fragColor;

const int MODE_FILL = 0;
const int MODE_OUTLINE = 1;
const int MODE_SOFT = 2;

// Signed distance to a rounded box centred on the origin.
float roundedBox(vec2 p, vec2 halfSize, float radius) {
    vec2 q = abs(p) - halfSize + radius;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius;
}

void main() {
    float radius = min(shape.z, min(shape.x, shape.y));
    float dist = roundedBox(localPos, shape.xy, radius);
    // Half a physical pixel, whatever the GUI scale or monitor resolution.
    float aa = max(fwidth(dist) * 0.5, 1e-4);

    float coverage;
    if (mode == MODE_OUTLINE) {
        float ring = abs(dist + border * 0.5) - border * 0.5;
        coverage = 1.0 - smoothstep(-aa, aa, ring);
    } else if (mode == MODE_SOFT) {
        float soft = max(shape.w, aa);
        coverage = 1.0 - smoothstep(-soft, soft, dist);
        coverage *= coverage;
    } else {
        coverage = 1.0 - smoothstep(-aa, aa, dist);
    }

    vec4 color = vertexColor;
    color.a *= coverage;
    if (color.a <= 0.002) {
        discard;
    }
    fragColor = color * ColorModulator;
}
