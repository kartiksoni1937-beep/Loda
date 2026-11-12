#!/usr/bin/env python3
"""Static mixin-target audit for OdinClient against Minecraft 1.21.11 (Yarn).

Verifies, without launching the game:
  1. every @Mixin target class exists (project bytecode/source or Minecraft jar);
  2. every "method = ..." handler target exists on the target class (inherited
     members and synthetic lambdas included);
  3. every @At target="Lowner;name(desc)" resolves to a real owner/member and the
     descriptor's arity matches the real member.

An unresolvable descriptor is exactly what produces
"InjectionError: Critical injection failure (0/1) succeeded. Scanned 0 target(s)".
"""

import glob
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "src/main/java")
CLASSES = os.path.join(ROOT, "build/classes/java/main")
MC_JAR = os.path.expanduser(
    "~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/"
    "1.21.11-net.fabricmc.yarn.1_21_11.1.21.11+build.6-v2/"
    "minecraft-merged-1.21.11-net.fabricmc.yarn.1_21_11.1.21.11+build.6-v2.jar"
)

_member_cache = {}
_simple_index = None


def javap(binary_name, classpath):
    return subprocess.run(
        ["javap", "-p", "-cp", classpath, binary_name.replace("/", ".")],
        capture_output=True, text=True,
    )


def parse_members(out):
    members = {}
    for line in out.splitlines():
        line = line.strip()
        if not line or line.startswith(("Compiled", "}")) or line.endswith("{"):
            continue
        m = re.search(r"([\w$]+)\(([^)]*)\)", line)
        if not m:
            continue
        name, params = m.group(1), m.group(2).strip()
        count = 0 if not params else len(split_type_params(params))
        members.setdefault(name, set()).add(count)
    return members


def split_type_params(params):
    parts, depth, cur = [], 0, ""
    for ch in params:
        if ch == "<":
            depth += 1
        elif ch == ">":
            depth -= 1
        elif ch == "," and depth == 0:
            parts.append(cur)
            cur = ""
            continue
        cur += ch
    if cur.strip():
        parts.append(cur)
    return parts


def class_members(binary_name, classpath=None, seen=None):
    """Declared + inherited members, for project classes (compiled) or the MC jar."""
    if classpath is None:
        classpath = MC_JAR
    key = (binary_name, classpath)
    if key in _member_cache:
        return _member_cache[key]
    if seen is None:
        seen = set()
    if binary_name in seen:
        return None
    seen.add(binary_name)

    out = javap(binary_name, classpath)
    if out.returncode != 0:
        _member_cache[key] = None
        return None

    members = parse_members(out.stdout)
    header = out.stdout.splitlines()
    supertypes = []
    for line in header:
        if re.search(r"^(public |final |abstract |sealed |non-sealed )*(class|interface|enum|record)\b", line):
            ext = re.search(r"\bextends\s+([\w.$<>]+)", line)
            if ext:
                supertypes.append(ext.group(1))
            impl = re.search(r"\bimplements\s+(.+?)\s*\{?$", line)
            if impl:
                supertypes += [t.strip() for t in impl.group(1).split(",")]
            break
    for parent in supertypes:
        parent = re.sub(r"<.*>", "", parent).replace(".", "/")
        inherited = class_members(parent, classpath, seen)
        if inherited:
            for name, arities in inherited.items():
                members.setdefault(name, set()).update(arities)
    _member_cache[key] = members
    return members


def project_members(internal_name):
    if os.path.isdir(CLASSES):
        members = class_members(internal_name, CLASSES)
        if members is not None:
            return members
    path = os.path.join(SRC, internal_name + ".java")
    if not os.path.exists(path):
        return None
    text = open(path, encoding="utf-8", errors="replace").read()
    members = {}
    for m in re.finditer(r"([\w$]+)\s*\(", text):
        members.setdefault(m.group(1), set())
    return members


def simple_name_index():
    index = {}
    for path in glob.glob(os.path.join(SRC, "**/*.java"), recursive=True):
        internal = os.path.relpath(path, SRC)[:-5].replace(os.sep, "/")
        index.setdefault(internal.rsplit("/", 1)[-1], []).append(internal)
    return index


def to_internal(token, text):
    global _simple_index
    token = token.strip()
    if token.startswith("L") and token.endswith(";"):
        token = token[1:-1]
    if "/" in token:
        return token
    if "." in token:
        return token.replace(".", "/")
    imports = dict(
        (m.group(2), m.group(1).replace(".", "/") + "/" + m.group(2))
        for m in re.finditer(r"import\s+(?:static\s+)?([\w.]+)\.(\w+)\s*;", text)
    )
    if token in imports:
        return imports[token]
    if _simple_index is None:
        _simple_index = simple_name_index()
    hits = _simple_index.get(token, [])
    return hits[0] if len(hits) == 1 else token


def resolve_members(owner):
    if owner.startswith("odinclient/") or os.path.exists(os.path.join(SRC, owner + ".java")):
        return project_members(owner)
    return class_members(owner, MC_JAR)


DESC_RE = re.compile(r"^L([^;]+);([\w$<>]+)(\((.*?)\))?(.*)$")


def split_desc(params):
    out, i = [], 0
    while i < len(params):
        ch = params[i]
        if ch == "[":
            while i < len(params) and params[i] == "[":
                i += 1
            if i < len(params) and params[i] == "L":
                i = params.index(";", i) + 1
            else:
                i += 1
            out.append("x")
            continue
        if ch == "L":
            i = params.index(";", i) + 1
            out.append("x")
            continue
        out.append(ch)
        i += 1
    return out


def main():
    problems = []
    checked_targets = checked_methods = checked_at = 0
    files = sorted(glob.glob(os.path.join(SRC, "odinclient/mixin/**/*.java"), recursive=True))
    for path in files:
        rel = os.path.relpath(path, ROOT)
        text = open(path, encoding="utf-8", errors="replace").read()

        m = re.search(r"@Mixin\(\s*(?:value\s*=\s*)?([\w.$]+)\.class", text)
        if m:
            target_internal = to_internal(m.group(1), text)
        else:
            m = re.search(r'@Mixin\(\s*targets\s*=\s*"([^"]+)"', text)
            if not m:
                continue
            target_internal = to_internal(m.group(1), text)
        checked_targets += 1
        members = resolve_members(target_internal)
        if members is None:
            problems.append((rel, "MIXIN-TARGET", f"target class {target_internal} not found"))
            continue

        for mm in re.finditer(r'method\s*=\s*(\{[^}]*\}|"[^"]*")', text):
            raw = mm.group(1)
            names = re.findall(r'"([^"]*)"', raw) or [raw]
            for entry in names:
                part = entry.strip()
                if not part:
                    continue
                target_name = part.split("(")[0].strip()
                if target_name in ("<init>", "<clinit>"):
                    continue
                checked_methods += 1
                if target_name not in members:
                    problems.append(
                        (rel, "HANDLER-TARGET",
                         f"{target_internal}.{target_name} not found on target or its supertypes"))

        for am in re.finditer(r'target\s*=\s*"([^"]+)"', text):
            value = am.group(1)
            if value.startswith(("field:", "method:", "new:", "class:")) or not value.startswith("L"):
                continue
            pm = DESC_RE.match(value)
            checked_at += 1
            if not pm:
                problems.append((rel, "AT-DESC", f"unparseable @At target {value}"))
                continue
            owner, name, _full, params, _ret = pm.groups()
            owner = to_internal(owner, text)
            expected = None
            if params is not None:
                expected = len(split_desc(params))
            omembers = resolve_members(owner)
            if omembers is None:
                problems.append((rel, "AT-OWNER", f"@At owner {owner} does not exist ({value})"))
                continue
            if name not in omembers:
                problems.append((rel, "AT-MEMBER", f"@At member {owner}.{name} does not exist"))
                continue
            arities = omembers[name]
            if expected is not None and arities and expected not in arities:
                problems.append(
                    (rel, "AT-DESC",
                     f"@At descriptor arity {expected} != real arity {sorted(arities)} "
                     f"for {owner}.{name}"))

    print(f"mixin files: {len(files)}  targets: {checked_targets}  "
          f"handler targets: {checked_methods}  @At descriptors: {checked_at}")
    if not problems:
        print("RESULT: clean - every mixin target resolves on Minecraft 1.21.11")
        return 0
    print(f"RESULT: {len(problems)} problem(s)")
    for rel, code, msg in problems:
        print(f"  [{code}] {rel}: {msg}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
