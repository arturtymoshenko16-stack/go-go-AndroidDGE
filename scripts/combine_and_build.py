#!/usr/bin/env python3
"""Combine a base Android project ZIP with optional overlay ZIPs, then build its APK."""
from pathlib import Path
from zipfile import ZipFile
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
INPUT = ROOT / "build-input"
ADDONS = INPUT / "addons"
WORK = ROOT / "work" / "combined-project"
OUT = ROOT / "output" / "apk"
LOG = ROOT / "output" / "build.log"

def safe_extract(zip_path: Path, destination: Path):
    with ZipFile(zip_path) as zf:
        for info in zf.infolist():
            target = (destination / info.filename).resolve()
            if destination.resolve() not in target.parents and target != destination.resolve():
                raise ValueError(f"Unsafe path in {zip_path.name}: {info.filename}")
        zf.extractall(destination)

def find_project(folder: Path):
    for marker in ("settings.gradle", "settings.gradle.kts"):
        found = list(folder.rglob(marker))
        if found:
            return found[0].parent
    return None

def flatten_archive_root(extracted: Path):
    # Prefer the folder containing Gradle settings; otherwise flatten one wrapper folder.
    project = find_project(extracted)
    if project:
        return project
    children = list(extracted.iterdir())
    if len(children) == 1 and children[0].is_dir():
        return children[0]
    return extracted

def copy_overlay(source: Path, destination: Path):
    for item in source.iterdir():
        target = destination / item.name
        if item.is_dir():
            target.mkdir(parents=True, exist_ok=True)
            copy_overlay(item, target)
        else:
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(item, target)

def prepare():
    if WORK.exists():
        shutil.rmtree(WORK)
    WORK.mkdir(parents=True, exist_ok=True)
    ADDONS.mkdir(parents=True, exist_ok=True)

    base = INPUT / "base.zip"
    if not base.exists():
        candidates = list(ROOT.glob("*.zip"))
        def committed_at(path):
            try:
                value = subprocess.check_output(
                    ["git", "log", "-1", "--format=%ct", "--", path.name],
                    cwd=ROOT, text=True
                ).strip()
                return int(value or "0")
            except (subprocess.CalledProcessError, ValueError):
                return 0
        candidates.sort(key=committed_at, reverse=True)
        if not candidates:
            raise SystemExit("No base ZIP found. Put the main Android project at build-input/base.zip.")
        base = candidates[0]
        print(f"Using newest root ZIP as base: {base.name}")

    base_extract = ROOT / "work" / "base-extract"
    if base_extract.exists():
        shutil.rmtree(base_extract)
    base_extract.mkdir(parents=True)
    safe_extract(base, base_extract)
    copy_overlay(flatten_archive_root(base_extract), WORK)

    addons = sorted(ADDONS.glob("*.zip"))
    for addon in addons:
        temp = ROOT / "work" / ("addon-" + addon.stem)
        if temp.exists():
            shutil.rmtree(temp)
        temp.mkdir(parents=True)
        safe_extract(addon, temp)
        source = flatten_archive_root(temp)
        print(f"Applying overlay ZIP: {addon.name}")
        # Add-ons are applied alphabetically; later files replace earlier files on conflicts.
        copy_overlay(source, WORK)

    project = find_project(WORK)
    if not project:
        raise SystemExit("No settings.gradle or settings.gradle.kts found after combining ZIPs.")
    if project != WORK:
        temp_root = ROOT / "work" / "project-root"
        if temp_root.exists():
            shutil.rmtree(temp_root)
        shutil.move(str(project), str(temp_root))
        for child in list(temp_root.iterdir()):
            target = WORK / child.name
            if target.exists():
                if target.is_dir():
                    shutil.rmtree(target)
                else:
                    target.unlink()
            shutil.move(str(child), str(target))
        shutil.rmtree(temp_root, ignore_errors=True)
    print("Combined Android project is ready.")

def build():
    project = find_project(WORK)
    if not project:
        raise SystemExit("Combined project folder is missing Gradle settings.")
    wrapper = project / "gradlew"
    if wrapper.exists():
        wrapper.chmod(wrapper.stat().st_mode | 0o111)
        cmd = [str(wrapper), "assembleDebug", "--no-daemon", "--console=plain"]
    else:
        cmd = ["gradle", "assembleDebug", "--no-daemon", "--console=plain"]

    OUT.mkdir(parents=True, exist_ok=True)
    LOG.parent.mkdir(parents=True, exist_ok=True)
    with LOG.open("w", encoding="utf-8") as log:
        proc = subprocess.run(cmd, cwd=project, stdout=subprocess.PIPE,
                              stderr=subprocess.STDOUT, text=True)
        log.write(proc.stdout)
        print(proc.stdout)
        if proc.returncode:
            raise SystemExit(proc.returncode)

    apks = list(project.rglob("build/outputs/apk/**/*.apk"))
    if not apks:
        raise SystemExit("Gradle succeeded but no APK was found under build/outputs/apk.")
    for apk in apks:
        shutil.copy2(apk, OUT / apk.name)
        print(f"APK created: {OUT / apk.name}")

if __name__ == "__main__":
    if len(sys.argv) != 2 or sys.argv[1] not in {"prepare", "build"}:
        raise SystemExit("Usage: combine_and_build.py prepare|build")
    prepare() if sys.argv[1] == "prepare" else build()
