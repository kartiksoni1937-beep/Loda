#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;
out vec2 strawCoord;
out vec3 strawColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position,1.0);
    vec3 normal = Normal / max(length(Normal),0.0001);
    // Soft shape shading with a floor, independent of world-lightmap darkness.
    float shade = 0.80 + 0.20 * abs(normal.y);
    strawCoord = UV0;
    strawColor = Color.rgb * shade;
}
