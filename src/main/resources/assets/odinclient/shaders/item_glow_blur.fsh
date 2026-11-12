#version 330
#moj_import <minecraft:dynamictransforms.glsl>
uniform sampler2D Sampler0;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec2 stepUv = TextureMat[3].xy;
    // Normalised nine-tap Gaussian; horizontal downsampling and vertical
    // filtering run on reusable half-resolution targets with linear sampling.
    vec4 halo = texture(Sampler0,texCoord) * 0.2270270270;
    halo += (texture(Sampler0,texCoord + stepUv) + texture(Sampler0,texCoord - stepUv)) * 0.1945945946;
    halo += (texture(Sampler0,texCoord + 2.0*stepUv) + texture(Sampler0,texCoord - 2.0*stepUv)) * 0.1216216216;
    halo += (texture(Sampler0,texCoord + 3.0*stepUv) + texture(Sampler0,texCoord - 3.0*stepUv)) * 0.0540540541;
    halo += (texture(Sampler0,texCoord + 4.0*stepUv) + texture(Sampler0,texCoord - 4.0*stepUv)) * 0.0162162162;
    fragColor = halo;
}
