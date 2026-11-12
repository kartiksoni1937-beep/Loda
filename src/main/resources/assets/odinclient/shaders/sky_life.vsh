#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <odinclient:sky_orbits.glsl>

in vec3 Position;
in vec2 UV0;
out vec2 objectUv;
flat out int objectKind;
flat out int planetKind;
flat out float objectSeed;
flat out float objectFade;
flat out float skyStyle;
flat out float animationTime;
flat out float objectElevation;

float seed(float n) { return fract(sin(n * 127.1 + 311.7) * 43758.5453); }

void main() {
    float id = Position.z;
    float time = TextureMat[3].x;
    float style = TextureMat[3].y;
    float h = seed(id + 1.0), h2 = seed(id + 43.0);
    float az = id * 2.39996323 + time * (0.008 + h * 0.012);
    float el = 0.20 + h2 * 0.70 + 0.028 * sin(time * 0.19 + id);
    vec2 size = vec2(0.005 + h * 0.007);
    float angle = 0.0, fade = 1.0;
    int kind = 3;
    int planet = 0;
    bool space = abs(style - 2.0) < 0.25 || abs(style - 4.0) < 0.25;
    bool birds = abs(style - 5.0) < 0.25 || abs(style - 6.0) < 0.25;

    if (space) {
        if (id < 6.0) {
            kind = 0;
            // Six orbiting bodies: ocean world, ringed giant, purple/cyan
            // fractured worlds, warm gas giant and another violet lava world.
            planet = int(id) == 5 ? 2 : int(id);
            // Independent smooth routes reverse, climb and descend toward
            // fresh goals; they are not tied to the backdrop's rotation.
            vec2 orbit = odinSkyOrbitAngles(id,time);
            az = orbit.x;
            el = orbit.y;
            size = vec2(0.070+h*0.025)*(id<0.5?1.4:1.0)*odinSkyOrbitScale(el);
        } else if (id < 14.0) {
            kind = 1;
            float phase = fract(time / (7.0 + h * 3.0) + (id - 6.0) / 8.0);
            float travel = phase / 0.38;
            az = id * 2.39996323 + travel * 0.45;
            el = 0.78 - travel * 0.30 + h2 * 0.28;
            fade = smoothstep(0.0, 0.12, travel) * (1.0 - smoothstep(0.78, 1.0, travel));
            size = vec2(0.080 + h * 0.065, 0.009);
            angle = -0.55;
        } else {
            // Fine orbiting luminous dust stays in motion between meteor passes.
            kind = 3;
            el = 0.13 + h2 * 1.10 + 0.06 * sin(time * 0.12 + id);
        }
    } else if (birds || abs(style - 10.0) < 0.25) {
        int birdCount = birds ? 24 : 10;
        if (int(id) < birdCount) {
            kind = 2;
            float flock = floor(id / 4.0);
            az = flock * 2.39996323 + time * (0.032 + seed(flock + 9.0) * 0.015) + mod(id,4.0) * 0.028;
            el = 0.20 + seed(flock + 18.0) * 0.48 + mod(id,4.0) * 0.013
               + 0.03 * sin(time * 0.26 + flock);
            if (id >= 20.0) el += 0.60;
            size = vec2(0.018 + h * 0.013, 0.018);
            angle = 0.12 * sin(time * 0.4 + flock);
        } else {
            kind = birds ? 4 : 6;
            size = birds ? vec2(0.17,0.07) : vec2(0.009 + h * 0.010);
            el = birds ? 0.50 + h2 * 0.65 : 0.08 + h2 * 0.42;
            if (birds && id >= 30.0) el = 1.32 + 0.05*sin(time*0.17+id);
            az = id * 2.39996323 + time * (birds ? 0.006 : 0.026);
        }
    } else if (abs(style - 8.0) < 0.25) {
        // Warm paper lanterns climbing above the Tokyo skyline.
        kind = id < 24.0 ? 5 : 3;
        float phase = fract(h2 + time * (0.006 + h * 0.003));
        el = 0.32 + phase * 0.98;
        az = id * 2.39996323 + time*0.012 + 0.035*sin(time*0.18+id);
        fade = smoothstep(0.0,0.09,phase) * (1.0-smoothstep(0.88,1.0,phase));
        // Most lanterns are small and distant; a few foreground ones retain
        // enough angular size to reveal the paper shell and open underside.
        float distanceScale = mix(1.0,0.46,h2)*mix(1.0,0.72,phase);
        size = kind == 5 ? vec2((0.027+0.021*h*h*h)*distanceScale) : vec2(0.006);
        angle = 0.07 * sin(time * 0.37 + id);
    } else if (abs(style - 9.0) < 0.25) {
        // Fireflies drift around the enchanted canopy, rather than blinking at fixed coordinates.
        kind = 3;
        az = id * 2.39996323 + time * 0.019 + 0.09 * sin(time * 0.34 + id);
        el = 0.06 + h2 * 0.58 + 0.08 * sin(time * 0.51 + id * 1.7);
        size = vec2(0.012 + h * 0.012);
        fade = 0.50 + 0.50 * pow(0.5 + 0.5 * sin(time * 1.1 + id),2.0);
    } else if (abs(style - 1.0) < 0.25) {
        kind = id < 12.0 ? 2 : 4;
        el = 0.26 + h2 * 0.65;
        size = kind == 2 ? vec2(0.025,0.016) : vec2(0.14,0.06);
        az = id * 2.39996323 + time * (kind == 2 ? 0.032 : 0.010);
    } else {
        // Night and aurora: softly twinkling stars plus staggered shooting stars.
        if (id < 8.0) {
            kind = 1;
            float phase = fract(time / (10.0 + h * 4.0) + id / 8.0);
            float travel = phase / 0.24;
            az = id * 2.39996323 + travel * 0.36;
            el = 0.60 + h2 * 0.48 - travel * 0.21;
            size = vec2(0.080,0.007);
            fade = smoothstep(0.0,0.1,travel) * (1.0-smoothstep(0.78,1.0,travel));
            angle = -0.5;
        }
    }

    // Every theme has something overhead as well as near the sides. Space
    // worlds travel through the ceiling; other skies reuse their highest objects.
    if (!space && !birds && id >= 29.0) {
        el = 1.20 + (id-29.0)*0.11 + 0.025*sin(time*0.20+id);
    }

    vec3 direction = vec3(sin(az) * cos(el),sin(el),cos(az) * cos(el));
    vec3 right = vec3(cos(az),0.0,-sin(az));
    vec3 up = vec3(-sin(az) * sin(el),cos(el),-cos(az) * sin(el));
    vec2 p = Position.xy * size;
    p = mat2(cos(angle),sin(angle),-sin(angle),cos(angle)) * p;
    gl_Position = ProjMat * ModelViewMat * vec4((direction + right * p.x + up * p.y) * 92.0,1.0);
    objectUv = UV0 * 2.0 - 1.0;
    objectKind = kind;
    planetKind = planet;
    objectSeed = h;
    objectFade = fade * TextureMat[3].z;
    skyStyle = style;
    animationTime = time;
    objectElevation = el;
}
