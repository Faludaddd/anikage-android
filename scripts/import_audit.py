#!/usr/bin/env python3
"""Import audit — checks that every Kotlin file's used symbols have imports.

Catches the class of error that broke CI (missing import for an extension
function / extension property / composable that Kotlin cannot resolve).
"""
import os
import re
import sys

ROOT = "app/src/main/java"

# (pattern_in_code, required_import_substring, description)
RULES = [
    (re.compile(r"\bRow\s*\("), "foundation.layout.Row", "Row"),
    (re.compile(r"\bColumn\s*\("), "foundation.layout.Column", "Column"),
    (re.compile(r"\.padding\s*\("), "foundation.layout.padding", "Modifier.padding"),
    (re.compile(r"\.size\s*\("), "foundation.layout.size", "Modifier.size"),
    (re.compile(r"\.height\s*\("), "foundation.layout.height", "Modifier.height"),
    (re.compile(r"\.width\s*\("), "foundation.layout.width", "Modifier.width"),
    (re.compile(r"\.fillMaxWidth\b"), "foundation.layout.fillMaxWidth", "fillMaxWidth"),
    (re.compile(r"\.fillMaxSize\b"), "foundation.layout.fillMaxSize", "fillSize"),
    (re.compile(r"\.fillMaxHeight\b"), "foundation.layout.fillMaxHeight", "fillMaxHeight"),
    (re.compile(r"\.aspectRatio\s*\("), "foundation.layout.aspectRatio", "aspectRatio"),
    (re.compile(r"\.offset\s*\("), "foundation.layout.offset", "Modifier.offset"),
    (re.compile(r"\.border\s*\("), "foundation.border", "Modifier.border"),
    (re.compile(r"\.background\s*\("), "foundation.background", "Modifier.background"),
    (re.compile(r"\.clip\s*\("), "ui.draw.clip", "Modifier.clip"),
    (re.compile(r"\.shadow\s*\("), "ui.draw.shadow", "Modifier.shadow"),
    (re.compile(r"\.alpha\s*\("), "ui.draw.alpha", "Modifier.alpha"),
    (re.compile(r"\b\d+(?:\.\d+)?\.dp\b"), "ui.unit.dp", "dp"),
    (re.compile(r"\b\d+(?:\.\d+)?\.sp\b"), "ui.unit.sp", "sp"),
    (re.compile(r"\bBorderStroke\s*\("), "foundation.BorderStroke", "BorderStroke"),
    (re.compile(r"\bWebTheme\b"), "Config.WebTheme", "WebTheme"),
    (re.compile(r"\bAsyncImage\s*\("), "coil.compose.AsyncImage", "AsyncImage"),
    (re.compile(r"\.graphicsLayer\b"), "ui.graphics.graphicsLayer", "graphicsLayer"),
    (re.compile(r"\.clickable\s*\("), "foundation.clickable", "clickable"),
]

# Icons.Default.X / Icons.Filled.X usage requires filled.X import
ICON_RE = re.compile(r"Icons\.(?:Default|Filled)\.(\w+)")
# fully-qualified icon access is invalid for extension properties
FQ_ICON_RE = re.compile(r"androidx\.compose\.material\.icons\.Icons\.")


def strip_comments_strings(src: str) -> str:
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    src = re.sub(r"//.*", "", src)
    src = re.sub(r'"(?:[^"\\]|\\.)*"', '""', src)
    return src


def strip_kdoc(src: str) -> str:
    return re.sub(r"/\*\*.*?\*/", "", src, flags=re.S)


def audit_file(path: str) -> list:
    src = open(path, encoding="utf-8").read()
    code = strip_comments_strings(strip_kdoc(src))
    # imports (search full source incl. comments-free region)
    imports = set(re.findall(r"^import\s+(\S+)", src, flags=re.M))
    package = re.search(r"^package\s+(\S+)", src, flags=re.M)
    own_package = package.group(1) if package else ""

    problems = []

    def has_import(needed: str) -> bool:
        for imp in imports:
            if needed in imp:
                # exact segment match beats substring (Row vs RowImpl)
                if imp.endswith(needed) or imp == needed:
                    return True
        return False

    for pattern, needed, desc in RULES:
        if not pattern.search(code):
            continue
        # same-package or star imports cover it
        star = any(i.endswith("*") for i in imports)
        if star:
            continue
        if own_package and needed.startswith(own_package):
            continue
        # fully-qualified usage in code is valid for classes/functions
        fq = needed.replace(".", ".") 
        if f"{fq}" in code and needed.split(".")[-1] in code:
            # e.g. "androidx.compose.foundation.BorderStroke(" present?
            if re.search(re.escape(needed).replace(r"\.", r"\.") + r"\s*\(", code):
                continue
        if not has_import(needed):
            # some symbols are also provided via wildcard import of their parent
            problems.append(f"missing import for {desc} (need ~ {needed})")

    # Icon extension properties
    for m in ICON_RE.finditer(code):
        icon = m.group(1)
        if not any(imp.endswith(f".{icon}") and "material.icons" in imp for imp in imports):
            problems.append(f"missing import androidx.compose.material.icons.filled.{icon}")
    if FQ_ICON_RE.search(code):
        problems.append("fully-qualified Icons reference — extension property needs plain import")

    return problems


def main():
    total = 0
    for dirpath, _, files in os.walk(ROOT):
        for f in sorted(files):
            if not f.endswith(".kt"):
                continue
            path = os.path.join(dirpath, f)
            problems = audit_file(path)
            if problems:
                total += len(problems)
                print(f"\n{path}:")
                for p in problems:
                    print(f"  - {p}")
    if total == 0:
        print("AUDIT OK — no missing imports detected")
    else:
        print(f"\nAUDIT FOUND {total} issue(s)")
        sys.exit(1)


if __name__ == "__main__":
    main()
