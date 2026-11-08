"""Compares two builds of the same jar class by class.

    python tools/test/compare_jars.py old.jar new.jar [--javap <path to javap>]

For each class in both jars it compares `javap -c -p` with the constant-pool indices taken out, so
a different constant order (another compiler or remapper version) does not count, a different
instruction does. Also lists files only in one jar and other files whose content differs (line
endings ignored). Used to check that a build change (e.g. the move to Stonecutter) left the jar's
code as it was. Slow-ish: one javap per class and jar.
"""
import argparse
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path


def javap_path(given):
    if given:
        return given
    home = os.environ.get("JAVA_HOME")
    if home:
        candidate = Path(home) / "bin" / ("javap.exe" if os.name == "nt" else "javap")
        if candidate.exists():
            return str(candidate)
    found = shutil.which("javap")
    if not found:
        sys.exit("javap not found: pass --javap or set JAVA_HOME")
    return found


NOISE = re.compile(r"#\d+(:#\d+)?")
SKIP = ("Classfile", "Last modified", "SHA-256", "MD5", "Compiled from")


def disassemble(javap, path):
    out = subprocess.run([javap, "-c", "-p", str(path)], capture_output=True, text=True).stdout
    return "\n".join(re.sub(r"\s+", " ", NOISE.sub("", line)).strip()
                     for line in out.splitlines() if not line.strip().startswith(SKIP))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("old")
    parser.add_argument("new")
    parser.add_argument("--javap")
    args = parser.parse_args()
    javap = javap_path(args.javap)

    with tempfile.TemporaryDirectory() as tmp:
        old_dir, new_dir = Path(tmp, "old"), Path(tmp, "new")
        zipfile.ZipFile(args.old).extractall(old_dir)
        zipfile.ZipFile(args.new).extractall(new_dir)
        old_files = {p.relative_to(old_dir).as_posix() for p in old_dir.rglob("*") if p.is_file()}
        new_files = {p.relative_to(new_dir).as_posix() for p in new_dir.rglob("*") if p.is_file()}
        for name in sorted(old_files - new_files):
            print("only in old: " + name)
        for name in sorted(new_files - old_files):
            print("only in new: " + name)
        classes = different = 0
        for name in sorted(old_files & new_files):
            if name.endswith(".class"):
                classes += 1
                if disassemble(javap, old_dir / name) != disassemble(javap, new_dir / name):
                    different += 1
                    print("code differs: " + name)
            elif (old_dir / name).read_bytes().replace(b"\r\n", b"\n") != (new_dir / name).read_bytes().replace(b"\r\n", b"\n"):
                print("content differs: " + name)
        print(f"{classes} classes compared, {different} with different code")


if __name__ == "__main__":
    main()
