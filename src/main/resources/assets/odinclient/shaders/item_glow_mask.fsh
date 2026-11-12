#version 330
in vec4 itemColor;
in vec2 texCoord;
uniform sampler2D Sampler0;
out vec4 fragColor;
void main() {
    float alpha = texture(Sampler0,texCoord).a;
    if (alpha < 0.1) discard;
    // Coverage is independent of glow opacity: even a very faint glow must
    // still exclude every opaque item pixel from the final colour overlay.
    fragColor = vec4(itemColor.rgb * itemColor.a * alpha,alpha);
}
