#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <odinclient:paper_lantern.glsl>

in vec2 objectUv;
flat in int objectKind;
flat in int planetKind;
flat in float objectSeed;
flat in float objectFade;
flat in float skyStyle;
flat in float animationTime;
flat in float objectElevation;
out vec4 fragColor;

float hash3(vec3 p) {
    p = fract(p * vec3(0.1031,0.1137,0.1379));
    p += dot(p,p.yzx + 19.19);
    return fract((p.x+p.y)*p.z);
}
float lifeNoise(vec3 p) {
    vec3 c = floor(p), f = fract(p);
    f = f*f*(3.0-2.0*f);
    float a = mix(hash3(c),hash3(c+vec3(1,0,0)),f.x);
    float b = mix(hash3(c+vec3(0,1,0)),hash3(c+vec3(1,1,0)),f.x);
    float d = mix(hash3(c+vec3(0,0,1)),hash3(c+vec3(1,0,1)),f.x);
    float e = mix(hash3(c+vec3(0,1,1)),hash3(c+vec3(1,1,1)),f.x);
    return mix(mix(a,b,f.y),mix(d,e,f.y),f.z);
}
float surfaceField(vec3 p) {
    return lifeNoise(p)*0.54 + lifeNoise(p*2.03+vec3(5.1,9.2,2.7))*0.27
         + lifeNoise(p*4.07+vec3(13.4,1.8,7.3))*0.13 + lifeNoise(p*8.11)*0.06;
}
float mask(float distance,float radius) {
    float aa = max(fwidth(distance)*1.1,0.002);
    return 1.0-smoothstep(radius-aa,radius+aa,distance);
}
float ellipse(vec2 p,vec2 shape) { return mask(length(p/shape),1.0); }
float roundedBox(vec2 p,vec2 halfSize,float radius) {
    vec2 q = abs(p)-halfSize+radius;
    float d = length(max(q,0.0))+min(max(q.x,q.y),0.0)-radius;
    return mask(d,0.0);
}

void main() {
    if (objectFade < 0.005) discard;
    vec2 p = objectUv;
    float time = animationTime, h = objectSeed;
    float alpha = 0.0;
    vec3 rgb = vec3(1.0);
    if (objectKind == 0) {
        // Analytic lit sphere, rotating surface and a thin atmosphere. No sprite pixels.
        bool ringed = planetKind == 1;
        bool fractured = planetKind == 2 || planetKind == 3;
        float radius = ringed ? 0.52 : 0.80;
        vec2 uv = p/radius;
        float r = length(uv);
        float sphere = mask(r,1.0);
        float z = sqrt(max(0.0,1.0-dot(uv,uv)));
        vec3 normal = normalize(vec3(uv,z));
        float angle = time * (0.06+h*0.03);
        vec3 surface = vec3(cos(angle)*normal.x+sin(angle)*normal.z,normal.y,
                            -sin(angle)*normal.x+cos(angle)*normal.z);
        float n = surfaceField(surface*4.0+vec3(h*20.0));
        float fine = lifeNoise(surface*17.0)*0.3;
        float light = 0.16+0.84*max(0.0,dot(normal,normalize(vec3(-0.55,0.55,0.75))));
        vec3 base;
        if (ringed) {
            float bands = 0.5+0.5*sin(surface.y*32.0+n*4.0);
            base = mix(vec3(0.32,0.16,0.38),vec3(0.83,0.64,0.46),bands);
            vec2 tilted = mat2(0.94,-0.342,0.342,0.94)*p;
            float ringRadius = length(tilted*vec2(1.0,3.4));
            float rings = smoothstep(0.61,0.64,ringRadius)*(1.0-smoothstep(0.93,0.96,ringRadius));
            float stripe = 0.55+0.14*sin(ringRadius*75.0)/(1.0+fwidth(ringRadius)*150.0);
            rgb = vec3(0.76,0.60,0.52)*stripe;
            alpha = rings*0.85;
        } else if (fractured) {
            // Dark 3D stone with emissive fissures, matching the violet lava
            // worlds in Cosmic Flow. Both the surface and veins rotate together.
            base = vec3(0.015,0.009,0.026)*(0.8+n*0.8);
        } else if (planetKind == 4) {
            float bands = 0.5+0.5*sin(surface.y*28.0+n*4.0);
            base = mix(vec3(0.38,0.20,0.13),vec3(0.83,0.58,0.32),bands);
        } else {
            float land = smoothstep(0.44,0.55,n+fine*0.22);
            base = mix(vec3(0.045,0.14,0.29),vec3(0.18,0.28,0.22),land);
            base *= 0.86+fine*0.70;
            float clouds = smoothstep(0.45,0.67,surfaceField(surface*10.0+vec3(time*0.014,0,0)));
            base = mix(base,vec3(0.73,0.83,0.89),clouds*0.68);
            base = mix(base,vec3(0.88,0.93,0.98),smoothstep(0.80,0.98,abs(surface.y)));
        }
        vec3 atmosphere = vec3(0.17,0.48,0.85)*pow(1.0-z,3.0)*0.48;
        if (fractured) {
            float field = surfaceField(surface*5.5+vec3(h*13.0));
            float distanceToVein = abs(field-0.50);
            float aa = max(fwidth(field)*1.15,0.0015);
            float cracks = 1.0-smoothstep(0.007,0.007+aa,distanceToVein);
            float fineField = lifeNoise(surface*15.0+vec3(h*9.0));
            float fineAA = max(fwidth(fineField)*1.2,0.002);
            float fineCracks = (1.0-smoothstep(0.003,0.003+fineAA,abs(fineField-0.50)))*0.29;
            float bloom = exp(-distanceToVein*65.0)*0.16;
            vec3 tint = planetKind == 3 ? vec3(0.23,0.74,1.0) : vec3(0.76,0.13,1.0);
            float pulse = 0.86+0.14*sin(time*0.70+h*23.0);
            atmosphere = tint*pow(1.0-z,4.0)*0.14;
            base += tint*(cracks+fineCracks+bloom)*pulse/max(light,0.16);
        }
        rgb = mix(rgb,base*light+atmosphere,sphere);
        float halo = exp(-max(0.0,r-1.0)*30.0)*0.10;
        if (sphere < 0.01 && !ringed) {
            rgb=fractured ? (planetKind==3?vec3(0.12,0.45,0.68):vec3(0.46,0.09,0.68)) : vec3(0.20,0.44,0.75);
            alpha=halo;
        }
        else alpha=max(alpha,sphere);
    } else if (objectKind == 1) {
        float x = p.x*0.5+0.5;
        float width = 0.013+0.095*x*x;
        float tail = mask(abs(p.y),width)*smoothstep(0.0,0.88,x)*(1.0-smoothstep(0.88,1.0,x));
        float head = ellipse(p-vec2(0.77,0),vec2(0.05,0.25));
        float glow = exp(-p.y*p.y*20.0)*pow(x,2.0)*(1.0-smoothstep(0.85,1.0,x));
        alpha = min(1.0,max(head,tail*0.90)+glow*0.18);
        rgb = mix(vec3(0.35,0.62,1.0),vec3(0.94,0.97,1.0),x);
    } else if (objectKind == 2) {
        // Curved flapping wings with tapered tips, a small body and a head.
        float x = abs(p.x);
        float flap = sin(time*(4.5+h*1.8)+h*30.0);
        float curve = 0.08 + 0.44*flap*x - 0.19*x*x;
        float wing = mask(abs(p.y-curve),mix(0.037,0.009,smoothstep(0.0,0.85,x)))
                   * (1.0-smoothstep(0.78,0.91,x));
        float body = ellipse(p-vec2(0,-0.025),vec2(0.055,0.15));
        float head = ellipse(p-vec2(0,0.11),vec2(0.048,0.05));
        alpha = max(wing,max(body,head))*0.86;
        rgb = abs(skyStyle-5.0)<0.25 ? vec3(0.20,0.16,0.15) : vec3(0.17,0.21,0.26);
    } else if (objectKind == 4) {
        float density = lifeNoise(vec3(p*2.4+vec2(time*0.018,0),h*10.0));
        density += lifeNoise(vec3(p*4.7+vec2(time*0.027,0),h*10.0))*0.34;
        float edge = (1.0-smoothstep(0.38,0.98,abs(p.x))) * (1.0-smoothstep(0.22,0.96,abs(p.y)));
        alpha = smoothstep(0.48,0.79,density)*edge*(skyStyle<1.5?0.12:0.19);
        rgb = abs(skyStyle-5.0)<0.25 ? vec3(1.0,0.75,0.55) : vec3(0.80,0.90,0.98);
    } else if (objectKind == 5) {
        vec4 lantern = odinPaperLantern(p,objectElevation,time,h);
        rgb = lantern.rgb;
        alpha = lantern.a;
    } else {
        float r = length(p);
        float core = mask(r,0.11);
        float glow = exp(-r*r*9.0)*0.30;
        float twinkle = 0.68+0.32*sin(time*(0.8+h)+h*25.0);
        alpha = (core*0.85+glow)*twinkle;
        bool warm = abs(skyStyle-9.0)<0.25 || abs(skyStyle-8.0)<0.25 || objectKind==6;
        rgb = warm ? vec3(1.0,0.78,0.28) : vec3(0.68,0.82,1.0);
        if (objectKind==6) alpha*=0.65;
    }
    alpha *= objectFade * ColorModulator.a;
    if (alpha < 0.003) discard;
    fragColor = vec4(rgb*ColorModulator.rgb,clamp(alpha,0.0,1.0));
}
