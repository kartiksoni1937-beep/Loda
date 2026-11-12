#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;
in vec2 localPos;
flat in vec4 params;
flat in int markLayout;

out vec4 fragColor;

// The OdinClient "OC" mark, rebuilt as distance fields so it can morph and scale freely.
const float SLANT = 0.40;        // italic shear of the letters
const float LETTER_GAP = 1.51;   // distance between the O and C centres

// Rounded box with per-corner radii (bottom-right, top-right, bottom-left, top-left), y down.
float roundedBox(vec2 p, vec2 halfSize, vec4 r) {
    r.xy = (p.x > 0.0) ? r.xy : r.zw;
    r.x = (p.y > 0.0) ? r.x : r.y;
    vec2 q = abs(p) - halfSize + r.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r.x;
}

float letterO(vec2 p) {
    float outer = roundedBox(p, vec2(0.65, 0.5), vec4(0.36, 0.14, 0.14, 0.36));
    float hole = roundedBox(p, vec2(0.34, 0.25), vec4(0.05));
    return max(outer, -hole);
}

float letterC(vec2 p) {
    float outer = roundedBox(p, vec2(0.65, 0.5), vec4(0.02, 0.02, 0.14, 0.36));
    float mouth = roundedBox(p - vec2(0.5, 0.0), vec2(0.84, 0.25), vec4(0.0, 0.0, 0.05, 0.05));
    return max(outer, -mouth);
}

float letter(vec2 p, float morph) {
    p.x += SLANT * p.y;
    return mix(letterO(p), letterC(p), morph);
}

void main() {
    float morph = params.x;
    float glow = params.y;
    float reveal = params.z;

    float dist;
    if (markLayout == 1) {
        dist = min(letter(localPos + vec2(LETTER_GAP * 0.5, 0.0), 0.0), letter(localPos - vec2(LETTER_GAP * 0.5, 0.0), 1.0));
    } else {
        dist = letter(localPos, morph);
    }

    float aa = max(fwidth(dist) * 0.5, 1e-4);
    float coverage;
    if (glow > 0.0) {
        float outside = max(dist, 0.0);
        coverage = exp(-outside * outside / (glow * glow)) * (dist < 0.0 ? 0.55 : 1.0);
    } else {
        coverage = 1.0 - smoothstep(-aa, aa, dist);
    }

    if (reveal < 1.0) {
        float extent = markLayout == 1 ? LETTER_GAP * 0.5 + 0.9 : 0.9;
        float edge = mix(-extent, extent, reveal);
        coverage *= 1.0 - smoothstep(edge - 0.08, edge + 0.08, localPos.x);
    }

    vec4 color = vertexColor;
    color.a *= coverage;
    if (color.a <= 0.002) {
        discard;
    }
    fragColor = color * ColorModulator;
}
