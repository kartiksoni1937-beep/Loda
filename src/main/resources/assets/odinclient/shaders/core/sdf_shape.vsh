#version 330

// Copies of dynamictransforms.glsl and projection.glsl, like vanilla's gui.vsh.
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
in vec2 UV0;      // position relative to the shape centre, in GUI units
in ivec2 UV1;     // half size * 16
in ivec2 UV2;     // x: radius * 16, y: softness * 16 | mode << 12
in float LineWidth; // outline thickness in GUI units

out vec4 vertexColor;
out vec2 localPos;
flat out vec4 shape;
flat out float border;
flat out int mode;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    localPos = UV0;
    shape = vec4(vec2(UV1) / 16.0, float(UV2.x) / 16.0, float(UV2.y & 4095) / 16.0);
    mode = (UV2.y >> 12) & 7;
    border = LineWidth;
}
