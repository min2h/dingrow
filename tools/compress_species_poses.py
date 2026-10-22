#!/usr/bin/env python3
"""Resize the 512px species key poses to 384px webp and refresh SpeciesPoses.kt.

The zip is the contact-sheet pack (50 species x blink/happy/sleep/eat/walk_left).
These stay interim poses until a rigged .riv exists for that species.
"""

import io
import sys
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
DRAWABLE = ROOT / "composeApp/src/commonMain/composeResources/drawable"
IOS_ART = ROOT / "iosApp/PetWidget/Art"
OUT_KT = ROOT / "composeApp/src/commonMain/kotlin/app/mongle/ui/SpeciesPoses.kt"


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("usage: compress_species_poses.py frames.zip")
    with zipfile.ZipFile(sys.argv[1]) as archive:
        names = sorted(name for name in archive.namelist() if name.endswith(".webp"))
        if len(names) != 250:
            raise SystemExit(f"expected 250 frames, got {len(names)}")
        total = 0
        for name in names:
            image = Image.open(io.BytesIO(archive.read(name))).convert("RGBA")
            image = image.resize((384, 384), Image.Resampling.LANCZOS)
            buffer = io.BytesIO()
            image.save(buffer, "WEBP", quality=72, method=6, lossless=False)
            data = buffer.getvalue()
            total += len(data)
            (DRAWABLE / name).write_bytes(data)
            (IOS_ART / name).write_bytes(data)
    stems = [name[:-5] for name in names]
    early = [stem for stem in stems if stem[0] < "m"]
    late = [stem for stem in stems if stem[0] >= "m"]
    OUT_KT.write_text(render(stems, early, late))
    print(f"{len(stems)} frames, {total / 1024 / 1024:.2f} MB")


def when_fn(name: str, items: list[str]) -> str:
    rows = "\n".join(f'        "{item}" -> Res.drawable.{item}' for item in items)
    return f"""    private fun {name}(id: String): DrawableResource = when (id) {{
{rows}
        else -> Res.drawable.base_idle
    }}
"""


def render(stems: list[str], early: list[str], late: list[str]) -> str:
    ids = "\n".join(f'        "{stem}",' for stem in stems)
    return f"""package app.mongle.ui

import app.mongle.generated.resources.Res
import app.mongle.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource

/**
 * Interim per-species key poses (blink, happy, sleep, eat, walk_left).
 * The live pet crossfades between these until a rigged Rive file exists for the species.
 * tiger, whale, and sunflower walks are three-quarter views and can be mirrored.
 * sweetpotato's walk is missing limbs, so the stride stays small and unmirrored.
 */
object SpeciesPoses {{
    const val count: Int = {len(stems)}
    fun has(id: String): Boolean = id in ids
    fun mirrorSafeWalk(speciesId: String): Boolean = speciesId == "tiger" || speciesId == "whale" || speciesId == "sunflower"
    fun weakWalk(speciesId: String): Boolean = speciesId == "sweetpotato"
    fun drawable(id: String): DrawableResource? {{
        if (id !in ids) return null
        val head = id[0]
        return if (head < 'm') poseEarly(id) else poseLate(id)
    }}
{when_fn("poseEarly", early)}
{when_fn("poseLate", late)}
    private val ids = setOf(
{ids}
    )
}}
"""


if __name__ == "__main__":
    main()
