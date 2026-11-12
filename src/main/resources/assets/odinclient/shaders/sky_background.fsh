#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <odinclient:sky_orbits.glsl>

uniform sampler2D Sampler0;
in vec3 skyDirection;
out vec4 fragColor;

// Positive cubic B-spline weights: smooth gradients without sharpening halos.
// Four hardware-linear reads instead of sixteen separate texel reads.
vec4 cubicWeights(float f) {
    float f2 = f * f;
    float f3 = f2 * f;
    return vec4(1.0 - 3.0*f + 3.0*f2 - f3, 4.0 - 6.0*f2 + 3.0*f3,
                1.0 + 3.0*f + 3.0*f2 - 3.0*f3, f3) / 6.0;
}

vec3 smoothFace(vec2 uv, vec2 tile) {
    vec2 size = vec2(textureSize(Sampler0, 0));
    vec2 faceSize = size / vec2(3.0, 2.0);
    vec2 p = uv * faceSize - 0.5;
    vec2 base = floor(p);
    vec2 f = fract(p);
    vec4 wx = cubicWeights(f.x);
    vec4 wy = cubicWeights(f.y);
    vec2 gx = vec2(wx.x + wx.y, wx.z + wx.w);
    vec2 gy = vec2(wy.x + wy.y, wy.z + wy.w);
    vec2 px = base.x + vec2(-1.0 + wx.y / gx.x, 1.0 + wx.w / gx.y) + 0.5;
    vec2 py = base.y + vec2(-1.0 + wy.y / gy.x, 1.0 + wy.w / gy.y) + 0.5;
    // Clamp each read to this face, not the complete atlas. No neighbour bleed.
    px = clamp(px, vec2(0.5), faceSize.xx - 0.5);
    py = clamp(py, vec2(0.5), faceSize.yy - 0.5);
    vec2 origin = tile * faceSize;
    vec3 a = texture(Sampler0, (origin + vec2(px.x, py.x)) / size).rgb;
    vec3 b = texture(Sampler0, (origin + vec2(px.y, py.x)) / size).rgb;
    vec3 c = texture(Sampler0, (origin + vec2(px.x, py.y)) / size).rgb;
    vec3 d = texture(Sampler0, (origin + vec2(px.y, py.y)) / size).rgb;
    return mix(mix(a, b, gx.y), mix(c, d, gx.y), gy.y);
}

vec3 smoothCube(vec3 d) {
    vec3 a = abs(d);
    vec2 uv;
    vec2 tile;
    if (a.y >= a.x && a.y >= a.z) {
        if (d.y < 0.0) { tile = vec2(0,0); uv = vec2(d.x, d.z) / a.y; }
        else { tile = vec2(1,0); uv = vec2(d.x, -d.z) / a.y; }
    } else if (a.z >= a.x) {
        if (d.z > 0.0) { tile = vec2(2,0); uv = vec2(-d.x, -d.y) / a.z; }
        else { tile = vec2(1,1); uv = vec2(d.x, -d.y) / a.z; }
    } else {
        if (d.x < 0.0) { tile = vec2(0,1); uv = vec2(-d.z, -d.y) / a.x; }
        else { tile = vec2(2,1); uv = vec2(d.z, -d.y) / a.x; }
    }
    return smoothFace(uv * 0.5 + 0.5, tile);
}

// Native panorama reconstruction. Catmull-Rom keeps fine detail instead of
// blurring it with a B-spline, and combines the central weights into nine
// hardware-linear reads. No intermediate face atlas or repeated resampling.
vec4 detailWeights(float f) {
    float f2 = f*f, f3 = f2*f;
    return vec4(-0.5*f + f2 - 0.5*f3, 1.0 - 2.5*f2 + 1.5*f3,
                 0.5*f + 2.0*f2 - 1.5*f3, -0.5*f2 + 0.5*f3);
}

vec3 detailPanoramaUv(vec2 uv) {
    vec2 size = vec2(textureSize(Sampler0,0));
    vec2 p = uv*size - 0.5;
    vec2 base = floor(p);
    vec4 wx = detailWeights(fract(p.x)), wy = detailWeights(fract(p.y));
    vec3 x = base.x + vec3(-1.0, wx.z/(wx.y+wx.z), 2.0) + 0.5;
    vec3 y = base.y + vec3(-1.0, wy.z/(wy.y+wy.z), 2.0) + 0.5;
    x = fract(x/size.x);
    y = clamp(y/size.y,vec3(0.5/size.y),vec3(1.0-0.5/size.y));
    vec3 ax = vec3(wx.x,wx.y+wx.z,wx.w), ay = vec3(wy.x,wy.y+wy.z,wy.w);
    vec3 a = texture(Sampler0,vec2(x.x,y.x)).rgb*ax.x
           + texture(Sampler0,vec2(x.y,y.x)).rgb*ax.y
           + texture(Sampler0,vec2(x.z,y.x)).rgb*ax.z;
    vec3 b = texture(Sampler0,vec2(x.x,y.y)).rgb*ax.x
           + texture(Sampler0,vec2(x.y,y.y)).rgb*ax.y
           + texture(Sampler0,vec2(x.z,y.y)).rgb*ax.z;
    vec3 c = texture(Sampler0,vec2(x.x,y.z)).rgb*ax.x
           + texture(Sampler0,vec2(x.y,y.z)).rgb*ax.y
           + texture(Sampler0,vec2(x.z,y.z)).rgb*ax.z;
    return clamp(a*ay.x + b*ay.y + c*ay.z,0.0,1.0);
}

vec3 nativePanorama(vec3 direction) {
    vec2 uv = vec2(0.5 + atan(direction.x,direction.z)/6.28318530718,
                   0.5 - asin(clamp(direction.y,-1.0,1.0))/3.14159265359);
    vec3 color = detailPanoramaUv(uv);
    // Symmetric transition for imperfect AI image edges; both sides meet at
    // exactly the same colour, not at a hard line or a different cube face.
    float edge = min(uv.x,1.0-uv.x);
    if (edge < 0.018) {
        vec3 continuation = detailPanoramaUv(vec2(1.0-uv.x,uv.y));
        color = mix((color+continuation)*0.5,color,smoothstep(0.0,0.018,edge));
    }
    // All longitudes converge to one colour at the poles, eliminating pinches.
    float pole = min(uv.y,1.0-uv.y);
    if (pole < 0.012) {
        float row = uv.y < 0.5 ? 0.0 : 1.0;
        vec3 average = vec3(0);
        for (int i=0; i<8; i++) average += texture(Sampler0,vec2((float(i)+0.5)/8.0,row)).rgb;
        color = mix(average/8.0,color,smoothstep(0.0,0.012,pole));
    }
    return color;
}

vec3 backgroundAt(vec3 d) {
    return TextureMat[0].w > 0.5 ? nativePanorama(d) : smoothCube(d);
}

vec3 turnY(vec3 p, float angle) {
    float c = cos(angle), s = sin(angle);
    return vec3(c*p.x - s*p.z, p.y, s*p.x + c*p.z);
}

vec3 turnX(vec3 p, float angle) {
    float c = cos(angle), s = sin(angle);
    return vec3(p.x,c*p.y-s*p.z,s*p.y+c*p.z);
}

float greenCurtain(vec3 color) {
    return smoothstep(0.025,0.13,color.g-max(color.r*0.95,color.b*0.75));
}

vec3 movingBackdrop(vec3 direction, float time, float style, float animated) {
    if (animated < 0.5) return backgroundAt(direction);
    float upper = smoothstep(0.12,0.42,direction.y);
    vec3 d = direction;
    if (abs(style-3.0)<0.25) {
        vec3 original = backgroundAt(direction);
        if (upper < 0.001) return original;
        float phase = direction.x*4.7 + direction.z*3.8 + direction.y*6.0;
        float sweep = sin(time*0.37+phase)-sin(phase);
        float fold = sin(time*0.61+phase*1.7)-sin(phase*1.7);
        // The actual photographed green curtains bend and rise/fall. Stars
        // outside the aurora and everything below the horizon stay anchored.
        // Hue masks must not deform the lookup coordinates: sharp image edges
        // would fold the UV field and tear the green curtains. Warp smoothly,
        // then blend only green light from the original/moving reconstructions.
        d = turnY(direction,(0.043*sweep+0.018*fold)*upper);
        d.y += (0.030*fold+0.012*sweep)*upper;
        d.x += 0.024*fold*upper;
        d.z += 0.033*sweep*upper;
        d = normalize(d);
        vec3 moved = backgroundAt(d);
        float green = max(greenCurtain(original),greenCurtain(moved));
        float flow = green*upper;
        vec3 color = mix(original,moved,0.94*flow);
        float pulse = sin(time*0.70+phase*2.0)-sin(phase*2.0);
        color *= 1.0 + 0.11*pulse*flow;
        return color;
    }
    if (abs(style-2.0)<0.25) {
        // Full-sphere nebula drift, not just added objects over a still image.
        d = turnX(turnY(direction,time*0.006),time*0.003);
        d.y += 0.018*(sin(time*0.21+direction.x*4.0)-sin(direction.x*4.0));
        return backgroundAt(normalize(d));
    }
    bool skyline = abs(style-8.0)<0.25 || abs(style-9.0)<0.25 || abs(style-10.0)<0.25;
    if (skyline) upper = smoothstep(0.28,0.62,direction.y);
    float speed = abs(style-1.0)<0.25 ? 0.014 : abs(style-6.0)<0.25 ? 0.010 : 0.0065;
    if (upper < 0.001) return backgroundAt(direction);
    // A rigid yaw keeps cloud shapes intact for arbitrarily long sessions.
    // Multiplying growing angles by a height mask folds the UV field after
    // a few minutes, spiralling the horizon and pulling land into the sky.
    d = turnY(direction,time*speed);
    d = turnX(d,0.025*sin(time*speed*0.45));
    float phase = direction.x*3.0 + direction.z*4.0;
    d.y += 0.016*(sin(time*0.24+phase)-sin(phase))*upper;
    // Blend the colours at the anchored horizon, not unbounded rotation angles.
    return mix(backgroundAt(direction),backgroundAt(normalize(d)),upper);
}

float hash3(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1137, 0.1379));
    p += dot(p, p.yzx + 19.19);
    return fract((p.x + p.y) * p.z);
}

// World-direction noise has no longitude wrap, face edges or polar singularity.
float coherentNoise(vec3 p) {
    vec3 cell = floor(p), f = fract(p);
    f = f*f*f*(f*(f*6.0 - 15.0) + 10.0);
    float a = mix(hash3(cell), hash3(cell + vec3(1,0,0)), f.x);
    float b = mix(hash3(cell + vec3(0,1,0)), hash3(cell + vec3(1,1,0)), f.x);
    float c = mix(hash3(cell + vec3(0,0,1)), hash3(cell + vec3(1,0,1)), f.x);
    float d = mix(hash3(cell + vec3(0,1,1)), hash3(cell + vec3(1,1,1)), f.x);
    return mix(mix(a,b,f.y), mix(c,d,f.y), f.z);
}

float cloudField(vec3 p) {
    return coherentNoise(p)*0.51 + coherentNoise(p*2.03 + vec3(5.1,2.7,9.2))*0.27
         + coherentNoise(p*4.07 + vec3(13.4,7.3,1.8))*0.14
         + coherentNoise(p*8.11 + vec3(2.9,17.6,8.2))*0.08;
}

vec3 cosmicStars(vec3 d, float time) {
    // Overlapping 3-D cells on the sphere, antialiased in angular/screen space.
    vec3 p = d * 155.0;
    vec3 cell = floor(p);
    vec3 f = fract(p);
    float h = hash3(cell);
    vec3 center = vec3(hash3(cell + vec3(8,0,0)), hash3(cell + vec3(0,8,0)), hash3(cell + vec3(0,0,8)));
    center = 0.25 + center*0.5;
    float distanceToStar = length(f - center);
    float aa = max(length(fwidth(p))*0.48, 0.028);
    float radius = 0.018 + h*0.018;
    float point = 1.0 - smoothstep(radius, radius + aa, distanceToStar);
    float glow = exp(-distanceToStar*distanceToStar*65.0)*0.22;
    float twinkle = 0.72 + 0.28*sin(time*(0.5 + h) + h*37.0);
    float visible = smoothstep(0.81, 0.86, h);
    float cross = exp(-abs(f.x-center.x)*75.0 - abs(f.y-center.y)*8.0 - abs(f.z-center.z)*65.0)
                + exp(-abs(f.x-center.x)*8.0 - abs(f.y-center.y)*75.0 - abs(f.z-center.z)*65.0);
    cross *= smoothstep(0.986,0.997,h)*0.65;
    return mix(vec3(0.60,0.74,1.0), vec3(1.0,0.85,0.98), h) * (point + glow + cross) * visible * twinkle;
}

vec3 planet(vec3 background, vec3 d, vec3 axis, float radius, float time, bool ringed) {
    float front = dot(d,axis);
    if (front < 0.94) return background;
    vec3 right = normalize(cross(vec3(0,1,0),axis));
    vec3 up = cross(axis,right);
    vec2 uv = vec2(dot(d,right),dot(d,up)) / sin(radius);
    float distanceToCenter = length(uv);
    float aa = max(fwidth(distanceToCenter),0.003);
    if (ringed) {
        vec2 tilted = mat2(0.91,-0.415,0.415,0.91)*uv;
        float ringDistance = length(tilted*vec2(1,3.8));
        float ringAA = max(fwidth(ringDistance),0.008);
        float rings = smoothstep(1.35-ringAA,1.35+ringAA,ringDistance)
                    * (1.0-smoothstep(2.3-ringAA,2.3+ringAA,ringDistance));
        float bands = 0.50 + 0.25*sin(ringDistance*75.0) + 0.12*sin(ringDistance*121.0);
        background = mix(background,vec3(0.48,0.30,0.57)*bands,rings*0.85);
    } else {
        float halo = exp(-max(0.0,distanceToCenter-1.0)*19.0);
        background += vec3(0.27,0.06,0.56)*halo*0.35;
    }
    if (distanceToCenter > 1.0+aa) return background;
    float z = sqrt(max(0.0,1.0-dot(uv,uv)));
    vec3 normal = normalize(right*uv.x + up*uv.y + axis*z);
    float light = 0.09 + 0.48*max(0.0,dot(normal,normalize(vec3(-0.7,0.6,0.9))));
    vec3 surface = turnY(normal,time*0.035);
    float n = coherentNoise(surface*7.0 + vec3(4.2,7.1,3.9));
    vec3 rock = vec3(0.042,0.032,0.065)*(0.8+n*0.8)*light;
    if (ringed) {
        float bands = 0.5+0.5*sin(surface.y*51.0 + n*3.0);
        rock = mix(vec3(0.10,0.06,0.19),vec3(0.37,0.23,0.40),bands)*light;
    } else {
        float crackDistance = abs(n-0.5);
        float crackAA = max(fwidth(n),0.002);
        float cracks = 1.0-smoothstep(0.005,0.005+crackAA,crackDistance);
        float fineNoise = coherentNoise(surface*14.0);
        float thin = (1.0-smoothstep(0.003,0.003+max(fwidth(fineNoise),0.002),abs(fineNoise-0.5)))*0.26;
        float glow = exp(-crackDistance*38.0)*0.22;
        rock += vec3(0.83,0.26,1.0)*(cracks+thin+glow)*(0.83+0.17*sin(time*0.9));
        rock += vec3(0.25,0.06,0.51)*pow(1.0-z,4.0);
    }
    float mask = 1.0-smoothstep(1.0-aa,1.0+aa,distanceToCenter);
    return mix(background,rock,mask);
}

vec3 cosmicSky(vec3 direction, float time) {
    vec3 d = turnX(turnY(direction,time*0.006),time*0.003);
    vec3 p = d * 3.4;
    float warp = coherentNoise(p*0.7 + vec3(time*0.014,0,time*0.008));
    vec3 q = p + vec3(warp, -warp*0.7, warp*0.6);
    float density = cloudField(q + vec3(0,time*0.018,time*0.011));
    float ribbon = 0.30 + 0.70*exp(-abs(d.y + 0.24*d.x - 0.13 + (warp-0.5)*0.55)*3.2);
    float dust = smoothstep(0.23, 0.68, density) * ribbon;
    float filament = pow(max(0.0, 1.0 - abs(density - 0.52)*8.5), 5.0)*ribbon;
    float hue = 0.5 + 0.5*sin(d.z*3.1 + d.x*2.4 + warp*2.5 + time*0.035);
    vec3 color = backgroundAt(d)*0.92;
    // The newly generated fine wisps are the backdrop; analytic 3-D fields add
    // living depth without replacing them with broad, flat noise blobs.
    color += mix(vec3(0.10,0.11,0.46), vec3(0.48,0.075,0.68), hue)*dust*0.13;
    color += mix(vec3(0.18,0.31,0.83), vec3(0.83,0.23,1.0), hue)*filament*0.085;
    color += cosmicStars(d, time)*0.66;
    // A small luminous ring, rather than a huge object covering the background.
    vec3 axis = normalize(vec3(-0.54,0.44,0.72));
    float angle = acos(clamp(dot(d,axis), -1.0, 1.0));
    float aa = max(fwidth(angle), 0.0008);
    float ring = exp(-pow((angle - 0.071) / max(0.004, aa), 2.0));
    float halo = exp(-pow((angle - 0.071)*35.0, 2.0));
    color += vec3(0.44,0.19,0.77)*(ring*0.8 + halo*0.15)*(0.87 + 0.13*sin(time*0.8));
    if (TextureMat[3].z > 0.5) {
        // The two classic worlds wander independently too, without changing
        // the existing nebula rotation or forcing every planet into one orbit.
        vec2 first = odinSkyOrbitAngles(6.0,time), second = odinSkyOrbitAngles(7.0,time);
        color = planet(color,direction,odinSkyOrbitDirection(6.0,time),0.10*odinSkyOrbitScale(first.y),time,false);
        color = planet(color,direction,odinSkyOrbitDirection(7.0,time),0.055*odinSkyOrbitScale(second.y),time,true);
    } else {
        color = planet(color,d,normalize(vec3(0.35,0.39,0.94)),0.10,time,false);
        color = planet(color,d,normalize(vec3(-0.87,0.65,0.8)),0.055,time,true);
    }
    return color;
}

void main() {
    vec3 direction = normalize(skyDirection);
    float animated = TextureMat[3].z;
    float time = TextureMat[3].x * animated;
    float style = TextureMat[3].y;
    vec3 color;
    if (abs(style-4.0) < 0.25) {
        color = cosmicSky(direction, time);
    } else {
        // The horizon/landscape never moves. Motion fades in high above it.
        float upperSky = smoothstep(0.12, 0.42, direction.y);
        color = movingBackdrop(direction,time,style,animated);
        if (abs(style-3.0)<0.25) {
            float aurora = smoothstep(0.015,0.12,color.g - max(color.r,color.b)*0.85)*upperSky;
            color *= 1.0 + 0.10*aurora*sin(time*0.23 + direction.x*8.0 + direction.z*6.0)*animated;
            color += cosmicStars(direction,time)*upperSky*0.46;
        } else if (abs(style-2.0)<0.25) {
            color *= 1.0 + 0.035*upperSky*sin(time*0.15 + dot(direction,vec3(3,4,5)))*animated;
        } else if (abs(style-7.0)<0.25) {
            color += cosmicStars(direction,time)*upperSky*0.24;
        } else if (abs(style-9.0)<0.25) {
            color += cosmicStars(direction,time)*upperSky*0.16;
        }
    }
    // Sub-quantisation spatial dither prevents visible bands; never temporal noise.
    float dither = (fract(dot(gl_FragCoord.xy, vec2(0.754877666,0.569840296))) - 0.5) / 255.0;
    fragColor = vec4(clamp(color + dither, 0.0, 1.0), 1.0) * ColorModulator;
}
