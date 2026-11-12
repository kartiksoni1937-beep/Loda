#version 330
#moj_import <minecraft:dynamictransforms.glsl>
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    // Only the isolated item's premultiplied colour is added. World/UI colours
    // are never sampled, blurred or recoloured, and target alpha is preserved.
    vec4 center = texture(Sampler1,texCoord);
    // Exclude the original silhouette, preserving its exact texture and glint.
    float outside = 1.0 - smoothstep(0.01,0.04,center.a);
    vec2 pixel = TextureMat[3].xy;
    vec4 edge = vec4(0.0);
    edge = max(edge,texture(Sampler1,texCoord + vec2(pixel.x,0.0)));
    edge = max(edge,texture(Sampler1,texCoord - vec2(pixel.x,0.0)));
    edge = max(edge,texture(Sampler1,texCoord + vec2(0.0,pixel.y)));
    edge = max(edge,texture(Sampler1,texCoord - vec2(0.0,pixel.y)));
    edge = max(edge,texture(Sampler1,texCoord + pixel * 0.70710678));
    edge = max(edge,texture(Sampler1,texCoord - pixel * 0.70710678));
    edge = max(edge,texture(Sampler1,texCoord + vec2(pixel.x,-pixel.y) * 0.70710678));
    edge = max(edge,texture(Sampler1,texCoord - vec2(pixel.x,-pixel.y) * 0.70710678));
    vec3 halo = texture(Sampler0,texCoord).rgb * TextureMat[3].z;
    fragColor = vec4((halo + edge.rgb * 0.85) * outside,1.0);
}
