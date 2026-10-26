#!/usr/bin/env python3
"""Run the fixed-seed query benchmark in a separate JVM with a fixed 512 MiB heap."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--skip-build", action="store_true")
args = parser.parse_args()
if not args.skip_build:
    maven = ["cmd.exe", "/d", "/c", "mvn"] if os.name == "nt" else [shutil.which("mvn") or "mvn"]
    subprocess.run(maven + ["--batch-mode", "--no-transfer-progress", "test-compile"], cwd=root, check=True)
java = Path(os.environ["JAVA_HOME"]) / "bin" / ("java.exe" if os.name == "nt" else "java") if os.environ.get("JAVA_HOME") else Path(shutil.which("java") or "java")
classpath = os.pathsep.join([str(root / "target/test-classes"), str(root / "target/classes")])
subprocess.run([str(java), "-Xms512m", "-Xmx512m", "-cp", classpath,
                "io.github.adrianpinson.worldtemps.service.QueryBenchmark"], cwd=root, check=True)
