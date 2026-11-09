"""Starts a production test server for one node, runs scenarios over RCON, stops it.

    python tools/test/run_server.py 1.21.1-fabric tools/test/scenarios/basic.txt
    python tools/test/run_server.py 1.20.1-fabric --jar old.jar --dir old tools/test/scenarios/basic.txt

For a Fabric node it runs `gradlew :<node>:prodServer -PstrictMixins`: a real server installed by Loom
with the built jar (or --jar, e.g. the last release, for the same test on both) and Fabric API, the
self-test command on, and every injector required to find its target. Loom has no production server
for Forge/NeoForge, so for those nodes it runs the dev server instead (`:<node>:runServer`, run dir
versions/<node>/run, --jar and --dir do not apply): on NeoForge from 1.20.5 that is close to
production (it runs on Mojang's names either way), on Forge 1.20.1 it does not test the SRG remap.
JAVA_HOME must point at a JDK 25 (Gradle runs on it).

The run dir is versions/<node>/run/<dir> (git-ignored). On first use it gets a server.properties
with RCON on, offline mode, a fixed seed (so two runs see the same world) and an eula.txt -
**writing it accepts the Minecraft EULA** (https://aka.ms/MinecraftEULA) for that test server.

Exit code: 0 when the server started and every scenario ran, 1 otherwise. Mixin errors in the log
are printed either way.
"""
import argparse
import os
import re
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import rcon  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]

PROPERTIES = """online-mode=false
enable-rcon=true
rcon.port={rcon}
rcon.password={password}
server-port={port}
spawn-protection=0
level-seed=12345
motd=mobstacker test server
"""


def prepare(run_dir, rcon_port, server_port, password, flat=False):
    run_dir.mkdir(parents=True, exist_ok=True)
    props = run_dir / "server.properties"
    if not props.exists():
        text = PROPERTIES.format(rcon=rcon_port, password=password, port=server_port)
        if flat:
            # The 1.20.1 rounds' scenarios stand their mobs on a flat world's ground (y -60); in a
            # normal world that is solid stone and they suffocate.
            text += "level-type=minecraft\\:flat\ndifficulty=easy\n"
        props.write_text(text, encoding="utf-8")
    eula = run_dir / "eula.txt"
    if not eula.exists():
        eula.write_text("eula=true\n", encoding="utf-8")
    # The port and password that count are the ones in the file (it may have been edited).
    text = props.read_text(encoding="utf-8")
    port = int(re.search(r"^rcon\.port=(\d+)", text, re.M).group(1))
    pw = re.search(r"^rcon\.password=(.*)$", text, re.M).group(1).strip()
    return port, pw


def wait_for_start(log, process, timeout):
    """Waits for "Done (" in the log; False if the server failed or the build ended first."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        text = log.read_text(encoding="utf-8", errors="replace") if log.exists() else ""
        if "Done (" in text:
            return True
        if process.poll() is not None or "Failed to start the minecraft server" in text or "BUILD FAILED" in text:
            return False
        time.sleep(1)
    return False


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("node", help="e.g. 1.21.1-fabric")
    parser.add_argument("scenarios", nargs="*", help="scenario files run in order")
    parser.add_argument("--jar", help="run this jar instead of the node's build")
    parser.add_argument("--dir", default="test", help="run dir name under versions/<node>/run/ (default: test)")
    parser.add_argument("--rcon-port", type=int, default=25575)
    parser.add_argument("--server-port", type=int, default=25599)
    parser.add_argument("--password", default="mstest")
    parser.add_argument("--timeout", type=int, default=600, help="seconds to wait for the server to start")
    parser.add_argument("--keep", action="store_true", help="leave the server running afterwards")
    parser.add_argument("--flat", action="store_true",
                        help="a flat world, for the scenarios from the 1.20.1 rounds (only when the run dir is new)")
    args = parser.parse_args()

    production = args.node.endswith("-fabric")
    run_dir = ROOT / "versions" / args.node / "run" / (args.dir if production else "")
    rcon_port, password = prepare(run_dir, args.rcon_port, args.server_port, args.password, args.flat)
    log = run_dir / "gradle-run.log"
    gradlew = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    env = dict(os.environ)
    if production:
        command = [str(gradlew), f":{args.node}:prodServer", "-PstrictMixins", f"-PprodDir={args.dir}", "--console=plain"]
        if args.jar:
            command.append(f"-PprodJar={Path(args.jar).resolve()}")
    else:
        command = [str(gradlew), f":{args.node}:runServer", "--args=nogui", "-PstrictMixins", "--console=plain"]
        # The dev run has no task option for JVM flags; the JVM reads this one itself.
        env["JAVA_TOOL_OPTIONS"] = (env.get("JAVA_TOOL_OPTIONS", "") + " -Dmobstacker.selftest=true").strip()
    print("$ " + " ".join(command))
    with open(log, "w", encoding="utf-8") as out:
        process = subprocess.Popen(command, cwd=ROOT, stdout=out, stderr=subprocess.STDOUT, env=env)

    ok = wait_for_start(log, process, args.timeout)
    text = log.read_text(encoding="utf-8", errors="replace")
    errors = [line for line in text.splitlines()
              if ("ERROR" in line and ("ixin" in line or "mobstacker" in line)) or line.startswith("Caused by")]
    if not ok:
        print("SERVER DID NOT START - see " + str(log))
        print("\n".join(errors[:20]))
        process.wait(timeout=60) if process.poll() is None else None
        return 1

    client = rcon.Rcon(rcon_port, password)
    try:
        for scenario in args.scenarios:
            print(f"### {scenario}")
            rcon.run(client, rcon.read_scenario(scenario))
        if not args.keep:
            client.command("stop")
    finally:
        client.close()
    if not args.keep:
        try:
            process.wait(timeout=120)
        except subprocess.TimeoutExpired:
            process.terminate()
    if errors:
        print("### errors in the log")
        print("\n".join(errors[:20]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
