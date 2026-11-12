// Independent smooth wandering paths for the six sprite worlds and Cosmic
// Flow's two original planets. Stable integer seeds: no frame-random jitter,
// common rotation, short repeating circle or changes to the sky background.
float odinSkyPathHash(float body, float point, uint salt) {
    uint value = uint(int(point))*0x9e3779b9u + uint(int(body))*0x85ebca6bu + salt;
    value ^= value >> 16u;
    value *= 0x7feb352du;
    value ^= value >> 15u;
    value *= 0x846ca68bu;
    value ^= value >> 16u;
    return float(value & 0x00ffffffu)/16777216.0;
}

float odinSkyPathDuration(float body) {
    return 54.0 + 22.0*odinSkyPathHash(body,0.0,0x68bc21ebu);
}

vec2 odinSkyPathPoint(float body, float point) {
    // Begin with the existing spread, then choose fresh goals independently.
    if (point == 0.0) {
        return vec2(0.70 + body*0.78539816339,
                    0.79 + 0.67*sin(body*0.78539816339-2.35619449019));
    }
    return vec2(6.28318530718*odinSkyPathHash(body,point,0x02e5be93u),
                0.12 + 1.34*odinSkyPathHash(body,point,0x967a889bu));
}

float odinSkyShortAngle(float angle) {
    return mod(angle+3.14159265359,6.28318530718)-3.14159265359;
}

float odinSkyVerticalTangent(float before, float after) {
    // Monotone Hermite tangents keep the path above the horizon and below the
    // pole without clamps, abrupt bounces or a discontinuous billboard turn.
    if (before*after <= 0.0) return 0.0;
    return 2.0*before*after/(before+after);
}

vec2 odinSkyOrbitAngles(float body, float time) {
    float progress = max(time,0.0)/odinSkyPathDuration(body);
    float point = floor(progress), f = fract(progress);
    vec2 a = odinSkyPathPoint(body,point-1.0), b = odinSkyPathPoint(body,point);
    vec2 c = odinSkyPathPoint(body,point+1.0), d = odinSkyPathPoint(body,point+2.0);
    a.x = b.x+odinSkyShortAngle(a.x-b.x);
    c.x = b.x+odinSkyShortAngle(c.x-b.x);
    d.x = c.x+odinSkyShortAngle(d.x-c.x);
    vec2 startTangent = vec2(0.5*(c.x-a.x),odinSkyVerticalTangent(b.y-a.y,c.y-b.y));
    vec2 endTangent = vec2(0.5*(d.x-b.x),odinSkyVerticalTangent(c.y-b.y,d.y-c.y));
    float f2 = f*f, f3 = f2*f;
    return (2.0*f3-3.0*f2+1.0)*b + (f3-2.0*f2+f)*startTangent
         + (-2.0*f3+3.0*f2)*c + (f3-f2)*endTangent;
}

vec3 odinSkyOrbitDirection(float lane, float time) {
    vec2 angle = odinSkyOrbitAngles(lane,time);
    return vec3(sin(angle.x)*cos(angle.y),sin(angle.y),cos(angle.x)*cos(angle.y));
}

float odinSkyOrbitScale(float elevation) {
    // The ceiling is narrower in angular space; receding worlds retain room
    // between silhouettes instead of overlapping close to the zenith.
    return 1.0 - 0.50*smoothstep(0.85,1.46,elevation);
}
