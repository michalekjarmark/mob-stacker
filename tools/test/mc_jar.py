"""Prints the path of Loom's Mojmap-named vanilla Minecraft jar for a version (built at least once).

    python tools/test/mc_jar.py 1.21.1
    javap -p -cp "$(python tools/test/mc_jar.py 1.21.1)" net.minecraft.world.entity.Mob

The jar to read vanilla's signatures from (javap), and the one tools/VanillaKeys.java wants. It is
vanilla: Forge/NeoForge nodes patch their own copy elsewhere.
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

if __name__ == "__main__":
    version = sys.argv[1]
    cache = ROOT / ".gradle" / "loom-cache" / "minecraftMaven" / "net" / "minecraft"
    jars = sorted((p for p in cache.glob(f"minecraft-merged-*/{version}-*/*.jar") if not p.name.endswith("-sources.jar")),
                  key=lambda p: p.stat().st_mtime, reverse=True)
    if not jars:
        sys.exit(f"no Minecraft {version} jar in {cache}: build a {version} node first")
    print(jars[0])
