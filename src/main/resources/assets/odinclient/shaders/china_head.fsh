#version 150

in vec2 strawCoord;
in vec3 strawColor;
out vec4 fragColor;

void main() {
    // Fine, understated woven strands. Filter their contrast when far away,
    // avoiding moire and hard pixel lines without loading any raster texture.
    float footprint = max(length(fwidth(strawCoord)),0.0001);
    float filtering = 1.0 / (1.0 + footprint * 230.0);
    float strands = sin(strawCoord.x*210.0 + sin(strawCoord.y*31.0)*0.25)
                  * cos(strawCoord.y*178.0);
    float rows = sin(length(strawCoord)*220.0);
    float weave = 1.0 + filtering * (strands*0.035 + rows*0.022);
    fragColor = vec4(clamp(strawColor*weave,0.0,1.0),1.0);
}
