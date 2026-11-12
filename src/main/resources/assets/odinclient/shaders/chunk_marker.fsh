#version 150
in vec4 markerColor;
out vec4 fragColor;
void main() {
    // Raw configured RGB/alpha: no underwater fog, lightmap or environment tint.
    fragColor = markerColor;
}
