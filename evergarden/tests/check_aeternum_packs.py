"""Check downloaded official Aeternum assets alongside the Evergarden release."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument("--downloads", type=Path, required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
java_path = args.downloads.parent / "Aeternum-Foods-26.x.zip"
assert hashlib.sha1(java_path.read_bytes()).hexdigest() == "f7137350c381dfb933f96e869bfaced4a292bcff"
items = json.loads((args.downloads / "aeternum_food_items.json").read_text(encoding="utf-8-sig"))
blocks = json.loads((args.downloads / "aeternum_food_blocks.json").read_text(encoding="utf-8-sig"))
own = json.loads((root / "dist/geyser-mappings.json").read_text())
definitions = [entry for entries in items["items"].values() for entry in entries]
own_ids = {entry["bedrock_identifier"] for entries in own["items"].values() for entry in entries}
foreign_ids = {entry["bedrock_identifier"] for entry in definitions}
assert len(foreign_ids) == len(definitions) == 11
assert not own_ids & foreign_ids
with zipfile.ZipFile(java_path) as java, zipfile.ZipFile(args.downloads / "Aeternum-Foods-Bedrock.mcpack") as bedrock, zipfile.ZipFile(root / "dist/evergarden-java.zip") as garden:
    assert java.testzip() is None and bedrock.testzip() is None
    expected_stages = [f"{crop}_stage_{stage}" for crop in ("onion", "tomato") for stage in range(4)]
    expected_stages += ["rice_seedling", "rice_stalk", "rice_young_top", "rice_ripe_top"]
    for stage in expected_stages:
        assert json.loads(java.read(f"assets/aeternum/items/crop_display/{stage}.json"))["model"]
    for name in java.namelist():
        if not name.endswith(".json"):
            continue
        data = json.loads(java.read(name))
        if "/models/" in name:
            for ref in [data.get("parent", ""), *data.get("textures", {}).values()]:
                if ref.startswith("aeternum:"):
                    kind, suffix = ("models", ".json") if ref == data.get("parent") else ("textures", ".png")
                    assert f"assets/aeternum/{kind}/{ref.split(':', 1)[1]}{suffix}" in java.namelist(), (name, ref)
    item_atlas = json.loads(bedrock.read("textures/item_texture.json"))["texture_data"]
    block_atlas = json.loads(bedrock.read("textures/terrain_texture.json"))["texture_data"]
    for entry in definitions:
        textures = item_atlas[entry["bedrock_options"]["icon"]]["textures"]
        for texture in textures if isinstance(textures, list) else [textures]:
            assert texture + ".png" in bedrock.namelist()
    states = blocks["blocks"]["minecraft:light"]["state_overrides"]
    for state in states.values():
        for material in state["material_instances"].values():
            textures = block_atlas[material["texture"]]["textures"]
            for texture in textures if isinstance(textures, list) else [textures]:
                assert texture + ".png" in bedrock.namelist()
    shared = set(java.namelist()) & set(garden.namelist())
    # Evergarden now includes the official Aeternum namespace and merges its vanilla carriers.
    # Shared art/models must be identical; our selectors must retain the official fallback.
    for name in shared:
        if name.startswith("assets/aeternum/"):
            assert java.read(name) == garden.read(name), ("Changed official asset", name)
        elif name.startswith("assets/minecraft/items/"):
            official_model = json.loads(java.read(name))["model"]
            merged_model = json.loads(garden.read(name))["model"]
            assert merged_model == official_model or merged_model.get("fallback") == official_model, name
        elif name.startswith("assets/"):
            raise AssertionError(("Unexpected shared asset", name))
    elixir = json.loads(garden.read("assets/voidscape/items/void_elixir.json"))
    assert elixir["model"]["model"] == "voidscape:item/void_elixir"
print(f"PASS Aeternum Java SHA-1, 12 crop models, model textures, 11 Bedrock items, {len(states)} crop block states, and pack overlap")
