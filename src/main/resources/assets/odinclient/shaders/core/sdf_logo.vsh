#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec4 Color;
in vec2 UV0;      // position in letter units (letter height = 1), relative to the letter centre
in ivec2 UV1;     // x: morph O->C * 1000, y: glow radius * 1000
in ivec2 UV2;     // x: reveal * 1000 (left-to-right wipe), y: layout (0 = one letter, 1 = full OC mark)
in float LineWidth;

out vec4 vertexColor;
out vec2 localPos;
flat out vec4 params;
flat out int markLayout;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    localPos = UV0;
    params = vec4(float(UV1.x) / 1000.0, float(UV1.y) / 1000.0, float(UV2.x) / 1000.0, LineWidth);
    markLayout = UV2.y;
}
