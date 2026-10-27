#!/usr/bin/env python3
"""Render actual Paper-generated Evergarden biome scenery from exposed-block CSVs.

CSV rows are x,y,z,MATERIAL_NAME. Positions come from real generated chunks;
lighting, sky, material fallback colours and camera projection are presentation.
These pictures are geometry previews, not in-game screenshots.

The textured renderer reuses preview_sky_whale.py with NumPy and Pillow. When
NumPy is unavailable, a fast Pillow voxel painter supplies an honest colour
preview. Vanilla textures are read directly from an optional local client JAR.

Example:
  python tools/render_biome_scenery.py output/biome-world/server/scenery \
      --output-dir previews/biome-world/scenery --width 1600
"""
from __future__ import annotations

import argparse
from collections import Counter
import csv
import hashlib
import json
import math
import os
from pathlib import Path
import time

from PIL import Image, ImageDraw, ImageFilter, ImageFont

SCENES = {
    "glacier": ("THE GLACIAL MASSIFS", "Natural ice arches, blue ice spires and a deep frozen skyland.", 235, 24),
    "ancient-forest": ("THE ANCIENT FOREST", "Giant branching trees, varied woodland and terrain-following roots.", 220, 25),
    "lunar-city": ("THE LOST LUNAR CITY", "Broken galleries, moon plazas and pale gardens in an abandoned district.", 225, 32),
    "gardens": ("THE EVERGARDEN GARDENS", "Wide blossom canopies and crystal gardens over the open sky.", 225, 27),
}
PALETTE = {
    "BLUE_ICE": (111, 169, 230), "PACKED_ICE": (165, 198, 235), "ICE": (167, 203, 234),
    "SNOW_BLOCK": (235, 245, 250), "SNOW": (235, 245, 250), "CALCITE": (227, 229, 222),
    "DIORITE": (181, 186, 187), "STONE": (124, 130, 128), "DEEPSLATE": (59, 65, 78),
    "TUFF": (106, 116, 109), "GRASS_BLOCK": (97, 143, 70), "DIRT": (125, 94, 68),
    "ROOTED_DIRT": (128, 98, 74), "PODZOL": (128, 105, 65), "MOSS_BLOCK": (95, 133, 57),
    "OAK_LOG": (114, 89, 59), "BIRCH_LOG": (218, 215, 193), "SPRUCE_LOG": (91, 70, 49),
    "DARK_OAK_LOG": (66, 50, 31), "CHERRY_LOG": (75, 47, 51), "PALE_OAK_LOG": (191, 188, 169),
    "OAK_LEAVES": (77, 127, 52), "BIRCH_LEAVES": (111, 151, 76), "SPRUCE_LEAVES": (65, 110, 82),
    "DARK_OAK_LEAVES": (66, 109, 43), "AZALEA_LEAVES": (96, 137, 64),
    "FLOWERING_AZALEA_LEAVES": (139, 146, 99), "CHERRY_LEAVES": (224, 157, 180),
    "PALE_OAK_LEAVES": (183, 195, 187), "MOSSY_COBBLESTONE": (113, 130, 94),
    "MOSSY_STONE_BRICKS": (117, 131, 104), "STONE_BRICKS": (134, 139, 138),
    "CHISELED_STONE_BRICKS": (130, 135, 135), "POLISHED_DEEPSLATE": (59, 65, 75),
    "CHISELED_QUARTZ_BLOCK": (220, 223, 216), "QUARTZ_PILLAR": (230, 231, 224),
    "SMOOTH_QUARTZ": (236, 236, 227), "SMOOTH_STONE": (174, 178, 179),
    "AMETHYST_BLOCK": (158, 115, 188), "PURPLE_STAINED_GLASS": (157, 118, 192),
    "LIGHT_BLUE_STAINED_GLASS": (109, 195, 219), "SEA_LANTERN": (181, 230, 225),
    "PEARLESCENT_FROGLIGHT": (237, 214, 239), "SHROOMLIGHT": (230, 160, 87),
    "BROWN_MUSHROOM_BLOCK": (153, 119, 83), "RED_MUSHROOM_BLOCK": (188, 73, 62),
    "MUSHROOM_STEM": (204, 206, 191), "SHORT_GRASS": (91, 143, 58), "FERN": (82, 136, 67),
    "IRON_CHAIN": (76, 83, 91), "GLOW_LICHEN": (145, 179, 143), "WATER": (96, 165, 207),
}
EMISSIVE = {"SEA_LANTERN", "SHROOMLIGHT", "PEARLESCENT_FROGLIGHT", "GLOWSTONE"}


def font(size: int, bold: bool = False):
    for name in ("C:/Windows/Fonts/" + ("segoeuib.ttf" if bold else "segoeui.ttf"),
                 "/usr/share/fonts/truetype/dejavu/" + ("DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf")):
        if Path(name).is_file():
            return ImageFont.truetype(name, size)
    return ImageFont.load_default(size=size)


def load_blocks(source: Path):
    blocks = {}
    with source.open(encoding="utf-8-sig", newline="") as stream:
        for row in csv.reader(stream):
            if not row or row[0].startswith("#") or row[0].lower() == "x":
                continue
            if len(row) != 4:
                raise ValueError(f"{source}: expected x,y,z,material; got {row!r}")
            x, y, z, material = row
            if material not in ("AIR", "CAVE_AIR", "VOID_AIR"):
                blocks[int(x), int(y), int(z)] = material
    if not blocks:
        raise ValueError(f"{source}: no generated blocks")
    return blocks


def find_client() -> Path | None:
    bases = [Path(os.environ.get("APPDATA", "")) / ".minecraft/versions",
             Path.home() / ".minecraft/versions",
             Path.home() / "Library/Application Support/minecraft/versions"]
    for base in bases:
        if base.is_dir():
            jars = sorted(base.glob("*/*.jar"), reverse=True)
            for jar in jars:
                if jar.stem.startswith("26.") or jar.stem.startswith("1."):
                    return jar
    return None


def presentation(image: Image.Image, title: str, subtitle: str, count: int, mode: str):
    width, height = image.size
    draw = ImageDraw.Draw(image, "RGBA")
    # Overwrite the inherited landmark banner with a biome-scene banner.
    draw.rectangle((0, 0, width, int(height * .15)), fill=(225, 234, 243, 255))
    x = int(width * .055)
    draw.text((x, int(height * .034)), "EVERGARDEN II  /  GREAT SKYLANDS", fill="#607087",
              font=font(max(12, int(width * .010)), True))
    draw.text((x, int(height * .060)), title, fill="#293c54", font=font(int(width * .025), True))
    draw.text((x, int(height * .110)), subtitle, fill="#546880", font=font(max(12, int(width * .010))))
    draw.rectangle((0, int(height * .955), width, height), fill=(233, 234, 236, 255))
    label = f"{count:,} EXPOSED GENERATED BLOCKS  |  {mode}  |  SIMULATED LIGHTING, NOT A GAME SCREENSHOT"
    draw.text((x, int(height * .966)), label, fill="#526276", font=font(max(10, int(width * .008))))


def render_fast(blocks, destination, title, subtitle, width, azimuth, elevation):
    """Orthographic visible-face painter; it never adds or changes block positions."""
    height = round(width * .80)
    az, el = math.radians(azimuth), math.radians(elevation)
    cam = (math.sin(az) * math.cos(el), math.sin(el), math.cos(az) * math.cos(el))
    right = (-math.cos(az), 0, math.sin(az))
    up = (-math.sin(el) * math.sin(az), math.cos(el), -math.sin(el) * math.cos(az))

    def projected(p):
        x, y, z = p
        return (x * right[0] + z * right[2], -(x * up[0] + y * up[1] + z * up[2]),
                x * cam[0] + y * cam[1] + z * cam[2])

    positions = {p: projected((p[0] + .5, p[1] + .5, p[2] + .5)) for p in blocks}
    xmin = min(p[0] for p in positions.values()); xmax = max(p[0] for p in positions.values())
    ymin = min(p[1] for p in positions.values()); ymax = max(p[1] for p in positions.values())
    scale = min(width * .90 / (xmax - xmin + 3), height * .72 / (ymax - ymin + 3))
    ox = width / 2 - (xmin + xmax) * scale / 2
    oy = height * .555 - (ymin + ymax) * scale / 2
    image = Image.new("RGB", (width, height))
    draw = ImageDraw.Draw(image)
    for row in range(height):
        t = row / height
        color = tuple(round(a * (1 - t) + b * t) for a, b in zip((163, 188, 220), (234, 229, 233)))
        draw.line((0, row, width, row), fill=color)
    clouds = Image.new("RGBA", image.size)
    cloud_draw = ImageDraw.Draw(clouds)
    for cx, cy, rx, ry in ((.17, .37, .28, .07), (.78, .43, .27, .07), (.62, .74, .34, .06)):
        cloud_draw.ellipse(((cx - rx) * width, (cy - ry) * height, (cx + rx) * width, (cy + ry) * height),
                           fill=(248, 245, 247, 75))
    image = Image.alpha_composite(image.convert("RGBA"), clouds.filter(ImageFilter.GaussianBlur(width / 32))).convert("RGB")
    draw = ImageDraw.Draw(image)
    signs = [1 if value > 0 else -1 for value in cam]
    faces = 0
    for index, position in enumerate(sorted(blocks, key=lambda p: positions[p][2])):
        material = blocks[position]
        color = PALETTE.get(material, (148, 141, 132))
        jitter = .97 + ((position[0] * 19 + position[1] * 37 + position[2] * 53) % 9) / 160
        for axis in (0, 2, 1):
            sign = signs[axis]; neighbor = list(position); neighbor[axis] += sign
            if tuple(neighbor) in blocks:
                continue
            tangents = [a for a in (0, 1, 2) if a != axis]
            polygon = []
            for va, vb in ((0, 0), (1, 0), (1, 1), (0, 1)):
                q = list(position); q[axis] += 1 if sign > 0 else 0
                q[tangents[0]] += va; q[tangents[1]] += vb
                u, v, _ = projected(q); polygon.append((ox + u * scale, oy + v * scale))
            shade = (1.0 if axis == 1 else .78 if axis == 0 else .65) * jitter
            if material in EMISSIVE:
                shade = 1.16
            fill = tuple(min(255, round(channel * shade)) for channel in color)
            draw.polygon(polygon, fill=fill)
            faces += 1
        if index and index % 50000 == 0:
            print(f"{destination.name}: {index:,}/{len(blocks):,} blocks", flush=True)
    presentation(image, title, subtitle, len(blocks), "MATERIAL COLOUR PREVIEW")
    destination.parent.mkdir(parents=True, exist_ok=True); image.save(destination)
    return {"path": destination.name, "width": width, "height": height, "rendered_faces": faces,
            "camera_azimuth": azimuth, "camera_elevation": elevation, "renderer": "Pillow material-colour painter"}


def contact_sheet(images, destination):
    columns = min(2, len(images)); rows = math.ceil(len(images) / columns)
    panel_width = 1000; panel_height = 800; gap = 22
    sheet = Image.new("RGB", (columns * panel_width + (columns + 1) * gap,
                              rows * panel_height + (rows + 1) * gap), "#142031")
    for index, file in enumerate(images):
        with Image.open(file) as image:
            image = image.convert("RGB").resize((panel_width, panel_height), Image.Resampling.LANCZOS)
            sheet.paste(image, (gap + (index % columns) * (panel_width + gap), gap + (index // columns) * (panel_height + gap)))
    sheet.save(destination)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("scenery", type=Path, help="Directory of glacier.csv, ancient-forest.csv, lunar-city.csv")
    parser.add_argument("--output-dir", type=Path, default=Path("previews/biome-world/scenery"))
    parser.add_argument("--minecraft-jar", type=Path)
    parser.add_argument("--width", type=int, default=1600)
    parser.add_argument("--renderer", choices=("auto", "textured", "fast"), default="auto")
    parser.add_argument("--views", nargs="+", choices=tuple(SCENES), default=list(SCENES))
    parser.add_argument("--shadows", action="store_true", help="Ray-traced sunlight; substantially slower")
    args = parser.parse_args()
    if not 600 <= args.width <= 4096:
        parser.error("--width must be between 600 and 4096")
    args.output_dir.mkdir(parents=True, exist_ok=True)
    native = None
    if args.renderer != "fast":
        try:
            import numpy as np
            if not hasattr(np, "asarray"):
                raise ImportError("NumPy package files are unavailable")
            import preview_sky_whale as native
            native.PALETTE.update(PALETTE)
            native.TINTED_LEAVES.add("DARK_OAK_LEAVES")
        except ImportError as error:
            if args.renderer == "textured":
                raise SystemExit(f"Textured renderer needs NumPy: {error}") from error
            print(f"Using Pillow material-colour renderer: {error}", flush=True)
    jar = args.minecraft_jar or find_client()
    textures = native.Textures(jar) if native else None
    manifest = {"description": "Exact blocks exported from actual Paper-generated chunks. Sky and lighting are simulated; not game screenshots.",
                "texture_source": str(jar) if native and jar else "material-colour palette", "scenes": []}
    for kind in args.views:
        source = args.scenery / (kind + ".csv")
        if not source.is_file():
            continue
        start = time.monotonic(); blocks = load_blocks(source)
        title, subtitle, azimuth, elevation = SCENES[kind]
        destination = args.output_dir / (kind + "-scenery.png")
        print(f"{kind}: {len(blocks):,} exposed blocks; {jar if native and jar else 'material colours'}", flush=True)
        if native:
            result = native.Renderer(blocks, textures).render(destination, title, subtitle,
                    width=args.width, height=round(args.width * .80), azimuth=azimuth,
                    elevation=elevation, shadows=args.shadows)
            with Image.open(destination) as rendered:
                image = rendered.convert("RGB")
            presentation(image, title, subtitle, len(blocks), "VANILLA TEXTURE PREVIEW" if jar else "MATERIAL COLOUR PREVIEW")
            image.save(destination); result["renderer"] = "preview_sky_whale.Renderer"
        else:
            result = render_fast(blocks, destination, title, subtitle, args.width, azimuth, elevation)
        result.update({"kind": kind, "source_csv": str(source), "source_sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
                       "exposed_block_count": len(blocks), "materials": dict(sorted(Counter(blocks.values()).items())),
                       "bounds_min": [min(p[axis] for p in blocks) for axis in (0, 1, 2)],
                       "bounds_max": [max(p[axis] for p in blocks) for axis in (0, 1, 2)],
                       "elapsed_seconds": round(time.monotonic() - start, 2)})
        manifest["scenes"].append(result)
        print(f"Saved {destination} ({result['elapsed_seconds']}s)", flush=True)
    if not manifest["scenes"]:
        raise SystemExit(f"No scenery CSV files found in {args.scenery}")
    if textures:
        manifest["fallback_materials"] = sorted(textures.fallback)
        if textures.jar:
            textures.jar.close()
    contact_sheet([args.output_dir / item["path"] for item in manifest["scenes"]], args.output_dir / "biome-scenery-contact-sheet.png")
    (args.output_dir / "scenery-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
