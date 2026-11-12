#version 150
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
in vec3 Position;
in vec4 Color;
out vec4 markerColor;
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    markerColor = Color;
}
