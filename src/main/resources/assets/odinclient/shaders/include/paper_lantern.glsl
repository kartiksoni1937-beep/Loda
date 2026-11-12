// Analytic lit paper lantern, shared by Sky Arts and the standalone effect.
// Ray/ellipsoid intersection reveals the real underside as the viewer looks up.
float odinLanternCoverage(float distance, float radius) {
    float aa = max(fwidth(distance)*1.10,0.002);
    return 1.0-smoothstep(radius-aa,radius+aa,distance);
}

vec4 odinPaperLantern(vec2 p, float elevation, float time, float variation) {
    float angle = clamp(elevation,-0.20,1.54);
    float s = sin(angle), c = cos(angle);
    vec3 eye = vec3(0.0,-s,c);
    vec3 ray = vec3(p.x,p.y*c,p.y*s);
    vec3 dimensions = vec3(0.43,0.69,0.43);
    vec3 r = ray/dimensions, v = eye/dimensions;
    float a = dot(v,v), b = dot(r,v), discriminant = b*b-a*(dot(r,r)-1.0);
    float root = sqrt(max(0.0,discriminant));
    float frontDepth = (-b+root)/a, backDepth = (-b-root)/a;
    vec3 front = ray+eye*frontDepth, back = ray+eye*backDepth;
    float edge = max(fwidth(discriminant),0.004);
    float silhouette = smoothstep(-edge,edge,discriminant);
    float bottom = -0.48;
    float frontWall = smoothstep(bottom-0.012,bottom+0.012,front.y)*silhouette;
    float backWall = smoothstep(bottom-0.012,bottom+0.012,back.y)*silhouette*(1.0-frontWall);
    vec3 paperPoint = mix(back,front,frontWall);
    vec3 normal = normalize(paperPoint/(dimensions*dimensions));

    // Red/orange translucent paper with broad side shading, thin folds and
    // restrained fiber variation. Distance-filtered: no pixel grid or moire.
    float fiberFootprint = max(length(fwidth(paperPoint.xy)),0.001);
    float detail = 1.0/(1.0+fiberFootprint*90.0);
    float fibers = sin(paperPoint.x*103.0+variation*19.0)*sin(paperPoint.y*89.0)*detail;
    float azimuth = atan(paperPoint.z,paperPoint.x);
    float folds = 0.94+0.06*cos((azimuth+time*0.15)*12.0);
    float light = 0.43+0.57*max(0.0,dot(normal,normalize(vec3(-0.55,0.18,1.0))));
    float warmth = exp(-dot((paperPoint.xy-vec2(0.0,-0.29))*vec2(2.7,1.9),
                            (paperPoint.xy-vec2(0.0,-0.29))*vec2(2.7,1.9)));
    vec3 paper = mix(vec3(0.75,0.17,0.075),vec3(1.0,0.54,0.16),warmth*0.63);
    paper *= light*folds*(1.0+fibers*0.025);
    paper = mix(paper,paper*0.62,backWall*0.65);
    float alpha = max(frontWall,backWall)*0.96;
    vec3 rgb = paper;

    // Open bottom with a thin bamboo hoop, dark recess and a white-gold flame.
    float opening = 0.0, hoop = 0.0;
    if (s > 0.025) {
        float planeDepth = (bottom-ray.y)/eye.y;
        vec3 planePoint = ray+eye*planeDepth;
        float radius = length(planePoint.xz);
        float openingRadius = 0.43*sqrt(1.0-bottom*bottom/(0.69*0.69));
        // The cap lies between the two ellipsoid intersections. It is visible
        // only where the front paper was cut away, never through intact paper.
        float cutAway = 1.0-frontWall;
        opening = odinLanternCoverage(radius,openingRadius)*cutAway*silhouette;
        hoop = odinLanternCoverage(abs(radius-openingRadius),0.014)*cutAway*silhouette;
        float flicker = 0.91+0.09*sin(time*3.3+variation*23.0);
        vec2 flamePosition = p-vec2(0.0,-0.37*c);
        vec2 flameShape = vec2(0.060,0.13*c+0.028*s);
        vec2 flameUv = flamePosition/flameShape;
        float taper = clamp(0.76-0.44*flameUv.y,0.22,1.0);
        float flame = odinLanternCoverage(length(vec2(flameUv.x/taper,flameUv.y)),1.0);
        float glow = exp(-dot(flamePosition*vec2(6.0,6.8),flamePosition*vec2(6.0,6.8)))*flicker;
        vec3 recess = vec3(0.14,0.045,0.018)+vec3(0.70,0.32,0.065)*glow;
        float support = odinLanternCoverage(min(abs(planePoint.x),abs(planePoint.z)),0.009);
        recess = mix(recess,vec3(0.42,0.24,0.085),support*0.72);
        float flameCore = exp(-flameUv.x*flameUv.x*8.0-flameUv.y*flameUv.y*0.7);
        vec3 fire = mix(vec3(1.0,0.40,0.075),vec3(1.0,0.97,0.73),flameCore);
        recess = mix(recess,fire,flame*flicker);
        rgb = mix(rgb,recess,opening);
        rgb = mix(rgb,vec3(0.77,0.49,0.22),hoop*0.85);
        alpha = max(alpha,max(opening,hoop)*0.98);
    }
    float halo = exp(-dot(p*vec2(1.6,1.4),p*vec2(1.6,1.4))*3.0)*0.065;
    if (alpha < 0.02) rgb = vec3(1.0,0.51,0.16);
    return vec4(rgb,max(alpha,halo));
}
