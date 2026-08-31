#!/usr/bin/env python3
"""
Pre-build validation for the Anikage Android repo.

Runs in CI BEFORE the (expensive) Gradle build so obvious breakage never
wastes a build slot. Checks:

  1. Repository hygiene — no README / docs / demo / placeholder files
     (the repo intentionally ships source + build files only).
  2. Version consistency — app/build.gradle versionName/versionCode must
     match Config.kt APP_VERSION / APP_VERSION_CODE.
  3. Structural sanity — required files exist (manifest, gradle files,
     wrapper, signing keystore), Kotlin sources present, no leftover
     TODO()/STOPSHIP markers in release code.
  4. Signing config — the release build type must reference the committed
     keystore (not the debug keystore), so every build has the same
     signature and updates install over previous releases.

Exit code 0 = safe to build.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
errors: list[str] = []

# ---------------------------------------------------------------------------
# 1. Repository hygiene — source + build files only, nothing else
# ---------------------------------------------------------------------------

BANNED_EXACT = {
    "README", "README.md", "README.txt", "README.rst",
    "CONTRIBUTING.md", "CHANGELOG.md", "LICENSE.md",
    "demo", "example", "placeholder", "sample_app",
}

BANNED_DIRS = {"docs", "documentation", "demo", "examples", "website"}

for path in ROOT.rglob("*"):
    rel = path.relative_to(ROOT)
    parts = rel.parts
    if parts and parts[0] in {".git", "build", ".gradle", ".gradle-home"}:
        continue
    name = path.name
    if path.is_file():
        if name in BANNED_EXACT or name.lower() in BANNED_EXACT:
            errors.append(f"banned file present: {rel}")
        if name.lower().endswith(".md") and name.lower() != "worklog.md":
            errors.append(f"markdown file present (no docs allowed): {rel}")
    elif path.is_dir():
        if name.lower() in BANNED_DIRS:
            errors.append(f"banned directory present: {rel}")

# ---------------------------------------------------------------------------
# 2. Version consistency between app/build.gradle and Config.kt
# ---------------------------------------------------------------------------

gradle = (ROOT / "app" / "build.gradle").read_text(encoding="utf-8")
config = (ROOT / "app" / "src" / "main" / "java" / "com" / "anikage" / "app" / "Config.kt").read_text(
    encoding="utf-8"
)

def grab(pattern: str, text: str, label: str) -> str:
    m = re.search(pattern, text)
    if not m:
        errors.append(f"could not find {label}")
        return ""
    return m.group(1)

gradle_name = grab(r'versionName\s+"([^"]+)"', gradle, "versionName in app/build.gradle")
gradle_code = grab(r"versionCode\s+(\d+)", gradle, "versionCode in app/build.gradle")
config_name = grab(r'APP_VERSION\s*=\s*"([^"]+)"', config, "APP_VERSION in Config.kt")
config_code = grab(r"APP_VERSION_CODE\s*=\s*(\d+)", config, "APP_VERSION_CODE in Config.kt")

if gradle_name and config_name and gradle_name != config_name:
    errors.append(f"versionName mismatch: build.gradle={gradle_name} Config.kt={config_name}")
if gradle_code and config_code and gradle_code != config_code:
    errors.append(f"versionCode mismatch: build.gradle={gradle_code} Config.kt={config_code}")
if gradle_code and gradle_code.isdigit() and int(gradle_code) < 7:
    errors.append(f"versionCode {gradle_code} is below the v1.5.0 baseline (7)")

# ---------------------------------------------------------------------------
# 3. Structural sanity
# ---------------------------------------------------------------------------

REQUIRED = [
    "app/build.gradle",
    "build.gradle",
    "settings.gradle",
    "gradlew",
    "gradle/wrapper/gradle-wrapper.properties",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/java/com/anikage/app/MainActivity.kt",
    "app/src/main/java/com/anikage/app/Config.kt",
    "keystore/anikage-release.jks",
]
for req in REQUIRED:
    if not (ROOT / req).exists():
        errors.append(f"required file missing: {req}")

kotlin_files = list((ROOT / "app" / "src" / "main").rglob("*.kt"))
if len(kotlin_files) < 20:
    errors.append(f"suspiciously few Kotlin sources: {len(kotlin_files)}")

for kf in kotlin_files:
    text = kf.read_text(encoding="utf-8", errors="replace")
    for marker in ("TODO()", "STOPSHIP", "FIXME()"):
        if marker in text:
            errors.append(f"unfinished marker {marker!r} in {kf.relative_to(ROOT)}")

# ---------------------------------------------------------------------------
# 4. Signing config — release must use the committed keystore
# ---------------------------------------------------------------------------

if "signingConfig signingConfigs.release" not in gradle:
    errors.append("release buildType does not use the release signingConfig")
if "keystore/anikage-release.jks" not in gradle:
    errors.append("release signingConfig does not reference keystore/anikage-release.jks")
if "signingConfigs.debug" in gradle:
    errors.append("debug keystore still referenced in release signing (update installs would break)")

# ---------------------------------------------------------------------------
# Report
# ---------------------------------------------------------------------------

if errors:
    print("VALIDATION FAILED")
    for e in errors:
        print(f"  - {e}")
    sys.exit(1)

print(f"VALIDATION OK — v{gradle_name} (code {gradle_code}), {len(kotlin_files)} Kotlin sources")
