#!/usr/bin/env python3
"""Build, smoke-test, and checksum platform-specific portable and bundled-runtime ZIPs."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import stat
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "target"
MAIN = "io.github.adrianpinson.worldtemps.ui.DesktopLauncher"


def run(command, **kwargs):
    print("+", " ".join(map(str, command)), flush=True)
    return subprocess.run(list(map(str, command)), cwd=ROOT, check=True, **kwargs)


def tool(name):
    executable = name + (".exe" if os.name == "nt" else "")
    configured = os.environ.get("JAVA_HOME")
    path = Path(configured) / "bin" / executable if configured else None
    if path and path.is_file():
        return path
    found = shutil.which(executable)
    if not found:
        raise RuntimeError(f"{name} is missing. Install a full JDK 17+ and set JAVA_HOME.")
    return Path(found)


def platform_name():
    system = {"Linux": "linux", "Windows": "windows", "Darwin": "macos"}.get(platform.system())
    architecture = {"x86_64": "x64", "amd64": "x64", "arm64": "arm64", "aarch64": "arm64"}.get(platform.machine().lower())
    if not system or not architecture:
        raise RuntimeError("Unsupported packaging platform: " + platform.platform())
    return f"{system}-{architecture}"


def zip_tree(source, output):
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for path in sorted(source.rglob("*")):
            name = path.relative_to(source.parent).as_posix()
            if path.is_symlink():
                info = zipfile.ZipInfo(name)
                info.create_system = 3
                info.external_attr = (stat.S_IFLNK | 0o777) << 16
                archive.writestr(info, os.readlink(path))
            elif path.is_file():
                archive.write(path, name)


def unpack(archive, directory):
    directory.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive) as source:
        for item in source.infolist():
            path = directory / item.filename
            if not path.resolve().is_relative_to(directory.resolve()):
                raise RuntimeError("Invalid archive path")
            mode = item.external_attr >> 16
            if item.is_dir():
                path.mkdir(parents=True, exist_ok=True)
                continue
            path.parent.mkdir(parents=True, exist_ok=True)
            if stat.S_ISLNK(mode):
                path.symlink_to(source.read(item).decode())
            else:
                with source.open(item) as content, path.open("wb") as output:
                    shutil.copyfileobj(content, output)
                if mode and os.name != "nt":
                    path.chmod(mode & 0o777)


def native_launcher(image):
    if platform.system() == "Windows":
        return image / "WorldTemps.exe"
    if platform.system() == "Darwin":
        return image / "Contents" / "MacOS" / "WorldTemps"
    return image / "bin" / "WorldTemps"


def smoke(command, report, bundled_root=None):
    report.unlink(missing_ok=True)
    run(command + ["--smoke-test", "--report", report], timeout=45)
    text = report.read_text()
    if not text.startswith("WorldTemps packaged runtime: OK\n"):
        raise RuntimeError("Package smoke report is missing or invalid")
    if bundled_root:
        java_home = Path(next(line.split("=", 1)[1] for line in text.splitlines() if line.startswith("java.home=")))
        if not java_home.resolve().is_relative_to(bundled_root.resolve()):
            raise RuntimeError("The native launcher used an external Java installation")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--skip-build", action="store_true", help="Use an existing -Pdesktop-package build")
    args = parser.parse_args()
    label = platform_name()
    version = ET.parse(ROOT / "pom.xml").getroot().find("{http://maven.apache.org/POM/4.0.0}version").text
    if not args.skip_build:
        maven = ["cmd.exe", "/d", "/c", "mvn"] if os.name == "nt" else [shutil.which("mvn") or "mvn"]
        run(maven + ["--batch-mode", "--no-transfer-progress", "-Pdesktop-package", f"-Dpackage.platform={label}", "clean", "verify"])
    app = TARGET / "app"
    portable = TARGET / f"worldtemps-{label}-portable.zip"
    if not (app / "worldtemps-core.jar").is_file() or not portable.is_file():
        raise RuntimeError("Desktop staging is missing; build with -Pdesktop-package and the matching package.platform.")
    classifier = {"Linux": "linux", "Windows": "win", "Darwin": "mac"}[platform.system()]
    if label.endswith("arm64"):
        classifier += "-aarch64"
    if not list((app / "lib").glob(f"javafx-graphics-*-{classifier}.jar")):
        raise RuntimeError("JavaFX dependencies do not match the packaging OS/architecture.")
    if os.name != "nt":
        (app / "start-worldtemps.sh").chmod(0o755)
    runtime = TARGET / "runtime"
    native = TARGET / "native"
    probe = TARGET / "package-probe"
    for directory in [runtime, native, probe]:
        if directory.exists():
            shutil.rmtree(directory)
    run([tool("jlink"), "--add-modules", "java.base,java.desktop,java.logging,java.net.http,java.sql,java.xml,jdk.unsupported,jdk.crypto.ec",
         "--strip-debug", "--no-header-files", "--no-man-pages", "--compress=2", "--output", runtime])
    run([tool("jpackage"), "--type", "app-image", "--name", "WorldTemps", "--app-version", version,
         "--vendor", "Adrian Pinson", "--description", "Historical temperature exploration and city comparisons",
         "--input", app, "--main-jar", "worldtemps-core.jar", "--main-class", MAIN,
         "--runtime-image", runtime, "--dest", native])
    image = native / ("WorldTemps.app" if platform.system() == "Darwin" else "WorldTemps")
    smoke([native_launcher(image)], TARGET / "native-smoke.txt", image)
    releases = TARGET / "releases"
    releases.mkdir(exist_ok=True)
    bundle = releases / f"WorldTemps-{label}"
    if bundle.exists():
        shutil.rmtree(bundle)
    bundle.mkdir()
    shutil.copytree(image, bundle / image.name, symlinks=True)
    shutil.copy2(ROOT / "packaging" / "README.txt", bundle / "README.txt")
    commit = subprocess.run(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True, capture_output=True).stdout.strip() or "unknown"
    dirty = bool(subprocess.run(["git", "status", "--porcelain"], cwd=ROOT, text=True, capture_output=True).stdout.strip())
    provenance = {"name": "WorldTemps", "version": version, "platform": label, "source_commit": commit,
                  "working_tree_dirty": dirty, "java": str(tool("java")), "runtime_bundled": True}
    (bundle / "PROVENANCE.json").write_text(json.dumps(provenance, indent=2) + "\n")
    native_zip = releases / f"worldtemps-{label}-bundled.zip"
    zip_tree(bundle, native_zip)
    shutil.copy2(portable, releases / portable.name)
    unpack(native_zip, probe / "native with spaces")
    unpack(portable, probe / "portable with spaces")
    extracted = probe / "native with spaces" / bundle.name / image.name
    smoke([native_launcher(extracted)], TARGET / "native-extracted-smoke.txt", extracted)
    portable_home = probe / "portable with spaces" / "WorldTemps"
    smoke([tool("java"), "-jar", portable_home / "worldtemps-core.jar"], TARGET / "portable-smoke.txt")
    launcher = portable_home / ("start-worldtemps.cmd" if os.name == "nt" else "start-worldtemps.sh")
    launch_command = ["cmd.exe", "/d", "/c", launcher] if os.name == "nt" else [launcher]
    smoke(launch_command, TARGET / "launcher-smoke.txt")
    checksums = []
    for archive in [native_zip, releases / portable.name]:
        digest = hashlib.sha256(archive.read_bytes()).hexdigest()
        checksums.append(f"{digest}  {archive.name}")
    (releases / f"SHA256SUMS-{label}.txt").write_text("\n".join(checksums) + "\n")
    print("Verified packages:", native_zip.name, portable.name)


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, subprocess.CalledProcessError, subprocess.TimeoutExpired) as error:
        print("Packaging failed:", error, file=sys.stderr)
        sys.exit(1)
