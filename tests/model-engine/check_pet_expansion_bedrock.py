"""Check the 171 new Bedrock pet forms against their authored Blockbench sources.

Read-only for game assets.  Writes only the release validation summary, without
comparing historical packs or invoking the exporter/build pipeline.
"""
from __future__ import annotations

import argparse
import base64
from collections import Counter
import hashlib
import io
import json
import math
from pathlib import Path
import zipfile

from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
RESOURCES = ROOT / "evergarden/src/main/resources"
STAGES = ("baby", "medium", "adult")
CLIPS = ("idle", "walk", "fly", "sit", "greet")
CLOCK = "q.anim_time + q.delta_time * q.property('voidscape:rate')"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--expected-version", default="3.72.0")
    parser.add_argument("--pack", type=Path, default=ROOT / "evergarden/dist/evergarden-bedrock.mcpack")
    parser.add_argument("--report", type=Path, default=ROOT / "output/pet-expansion-release/bedrock-checks.json")
    args = parser.parse_args()
    checks = 0
    variants = 0
    cubes = 0
    bone_count = 0
    keyframes = 0
    png_count = 0
    source_hashes = {}
    report = {"result": "FAIL", "connected_client_playtest": False}

    def check(value, label):
        nonlocal checks
        checks += 1
        assert value, label

    def close(left, right):
        return len(left) == len(right) and all(abs(a-b) < 1e-5 for a,b in zip(left,right))

    try:
        roster = json.loads((RESOURCES / "pet-expansion.json").read_text(encoding="utf8"))["species"]
        species = [entry["id"] for entry in roster]
        check(len(species) == len(set(species)) == 57, "57 unique new species")
        check(Counter(entry["rarity"] for entry in roster) == {"rare": 17, "common": 40}, "new rarity totals")
        expected_models = {f"everpet_{name}_{stage}" for name in species for stage in STAGES}
        check(len(expected_models) == 171, "three forms per new species")
        expected_version = [int(v) for v in args.expected_version.split(".")]
        with zipfile.ZipFile(args.pack) as pack:
            names = pack.namelist()
            check(len(names) == len(set(names)), "no duplicate archive paths")
            check(pack.testzip() is None, "Bedrock pack CRC")
            paths = set(names)
            manifest = json.loads(pack.read("manifest.json"))
            check(manifest["header"]["version"] == expected_version, "release content version")
            check(all(module["version"] == expected_version for module in manifest["modules"]), "module content versions")

            # Check identifiers across the current pack, including the existing
            # species, so a new export cannot silently replace a second entity.
            identifiers = {}
            entity_paths = {}
            for path in names:
                if not path.startswith("entity/") or not path.endswith(".json"):
                    continue
                entity = json.loads(pack.read(path)).get("minecraft:client_entity", {}).get("description")
                if not entity:
                    continue
                identifier = entity["identifier"]
                check(identifier not in identifiers, "unique client entity identifier " + identifier)
                identifiers[identifier] = path
                if identifier.startswith("voidscape:everpet_"):
                    entity_paths[identifier.removeprefix("voidscape:")] = path
            check(expected_models <= set(entity_paths), "all 171 new entity identifiers present")
            check(len(entity_paths) == 282, "all 94 species / 282 total pet forms retained")

            for model_id in sorted(expected_models):
                label = model_id + ": "
                source_path = RESOURCES / "modelengine/pets" / (model_id + ".bbmodel")
                source_bytes = source_path.read_bytes()
                source_hashes[source_path.name] = hashlib.sha256(source_bytes).hexdigest()
                source = json.loads(source_bytes)
                check(model_id == "everpet_" + source["meta"]["evergarden_pet_species"] + "_" + source["meta"]["evergarden_pet_stage"], label + "source metadata")
                check(source["resolution"] == {"width":1024,"height":1024}, label + "1024px authored atlas")
                elements = {e["uuid"]:e for e in source["elements"]}
                groups = []

                def visit(group, parent=None):
                    if group["name"] == "hitbox":
                        return
                    name = "gaze" if group["name"] == "hi_gaze" else group["name"]
                    groups.append((group,name,parent))
                    for child in group.get("children",[]):
                        if isinstance(child,dict):visit(child,name)

                for group in source["outliner"]:visit(group)
                geom_path = f"models/entity/{model_id}_native.geo.json"
                geometries = json.loads(pack.read(geom_path))["minecraft:geometry"]
                check(len(geometries) == 1,label + "single geometry")
                geometry = geometries[0]
                description = geometry["description"]
                geometry_id = f"geometry.voidscape.{model_id}_native"
                check(description["identifier"] == geometry_id,label + "geometry identifier")
                check((description["texture_width"],description["texture_height"]) == (1024,1024),label + "1024px exported atlas declaration")
                target_bones = geometry["bones"]
                bone_names = {bone["name"] for bone in target_bones}
                check(len(bone_names) == len(target_bones) == len(groups),label + "all unique authored bones")
                check("head" in bone_names and "body" in bone_names,label + "animatable head/body")
                parent_map = {bone["name"]:bone.get("parent") for bone in target_bones}
                for bone in target_bones:
                    chain = set()
                    current = bone["name"]
                    while current is not None:
                        check(current in parent_map and current not in chain,label + "resolved acyclic hierarchy " + bone["name"])
                        chain.add(current)
                        current = parent_map[current]
                for (group,name,parent),bone in zip(groups,target_bones):
                    check(bone["name"] == name and bone.get("parent") == parent,label + "original joint hierarchy " + name)
                    check(close(bone["pivot"],[-group["origin"][0],*group["origin"][1:]]),label + "original reflected joint pivot " + name)
                    source_cubes = [elements[e] for e in group.get("children",[]) if isinstance(e,str)]
                    check(len(source_cubes) == len(bone.get("cubes",[])),label + "all authored cubes " + name)
                    for original,cube in zip(source_cubes,bone.get("cubes",[])):
                        check(close(cube["origin"],[-original["to"][0],original["from"][1],original["from"][2]]),label + "cube origin")
                        check(close(cube["size"],[b-a for a,b in zip(original["from"],original["to"])]),label + "cube dimensions")
                        check(all(math.isfinite(v) for v in cube["origin"]+cube["size"]),label + "finite cube")
                        check(min(cube["size"]) > 0,label + "positive cube")
                        rotation = original.get("rotation",[0,0,0])
                        check(bool(any(rotation)) == ("rotation" in cube),label + "authored cube rotation presence")
                        if any(rotation):
                            check(close(cube["rotation"],[-rotation[0],-rotation[1],rotation[2]]),label + "compound cube rotation")
                            check(close(cube["pivot"],[-original["origin"][0],*original["origin"][1:]]),label + "cube rotation pivot")
                        visible_faces = {f for f,v in original["faces"].items() if v.get("texture") is not None}
                        check(set(cube["uv"]) == visible_faces,label + "textured/hidden faces")
                        for face in visible_faces:
                            uv = original["faces"][face]["uv"]
                            start = uv[:2]
                            span = [uv[2]-uv[0],uv[3]-uv[1]]
                            if face in ("up","down"):
                                start = [start[i]+span[i] for i in range(2)]
                                span = [-v for v in span]
                            check(close(cube["uv"][face]["uv"],start) and close(cube["uv"][face]["uv_size"],span),label + "original face UV")
                            check(all(0 <= v <= 1024 for v in uv),label + "atlas UV bounds")
                        cubes += 1
                bone_count += len(target_bones)

                entity_path = f"entity/{model_id}_native.entity.json"
                check(entity_paths[model_id] == entity_path,label + "canonical entity path")
                entity = json.loads(pack.read(entity_path))["minecraft:client_entity"]["description"]
                check(entity["geometry"] == {"default":geometry_id},label + "entity geometry reference")
                texture_path = f"textures/entity/{model_id}"
                check(entity["textures"] == {"default":texture_path},label + "canonical PNG reference")
                for reference in entity["textures"].values():
                    check(reference+".png" in paths,label + "resolved PNG reference")
                texture_bytes = pack.read(texture_path+".png")
                check(texture_bytes == base64.b64decode(source["textures"][0]["source"].split(",",1)[1]),label + "same source PNG bytes")
                with Image.open(io.BytesIO(texture_bytes)) as texture:
                    check(texture.format == "PNG" and texture.size == (1024,1024),label + "valid 1024x1024 PNG")
                    texture.verify()
                png_count += 1

                scripts = entity["scripts"]
                check(scripts["animate"] == ["combat","look"],label + "additive gaze follows authored pose")
                check(scripts["initialize"] == ["v.look_yaw = 0;","v.look_pitch = 0;"],label + "gaze initialization")
                pre_animation = scripts["pre_animation"]
                check(len(pre_animation) == 2 and all("q.delta_time" in v for v in pre_animation),label + "interpolated client gaze")
                check("voidscape:look_yaw" in pre_animation[0] and "voidscape:look_pitch" in pre_animation[1],label + "gaze properties resolve")
                check(not any("clip_time" in v or "previous_clip" in v for v in pre_animation),label + "no packet-driven clock reset")
                animation_path = f"animations/{model_id}_native.animation.json"
                animations = json.loads(pack.read(animation_path))["animations"]
                expected_animations = {f"animation.voidscape.{model_id}.{clip}" for clip in (*CLIPS,"look")}
                check(set(animations) == expected_animations,label + "five real clips plus additive look")
                check([c["name"] for c in source["animations"]] == list(CLIPS),label + "authored selectable clip order")
                signatures = set()
                for clip in source["animations"]:
                    name = clip["name"]
                    animation_id = f"animation.voidscape.{model_id}.{name}"
                    check(entity["animations"][name] == animation_id,label + "animation alias " + name)
                    animation = animations[animation_id]
                    check(animation["anim_time_update"] == CLOCK,label + "continuous client clip clock " + name)
                    check(animation["animation_length"] == clip["length"] and clip["length"] > 0,label + "authored clip duration " + name)
                    check(animation["loop"] == (True if clip["loop"] == "loop" else "hold_on_last_frame"),label + "authored clip looping " + name)
                    check(set(animation["bones"]) <= bone_names,label + "all animated bones resolve " + name)
                    expected_tracks = {}
                    for animator in clip["animators"].values():
                        if animator["name"] in ("gaze","hi_gaze"):continue
                        channels = {}
                        for frame in animator["keyframes"]:
                            v = [float(frame["data_points"][0][axis]) for axis in "xyz"]
                            if frame["channel"] == "position":
                                x = -v[0] if source.get("meta",{}).get("evergarden_animation_position_x") == "inverted" else v[0]
                                v = [-x,v[1],v[2]]
                            entry = v if frame["interpolation"] != "catmullrom" else {"post":v,"lerp_mode":"catmullrom"}
                            channels.setdefault(frame["channel"],{})[str(frame["time"])] = entry
                            keyframes += 1
                        expected_tracks[animator["name"]] = channels
                    check(animation["bones"] == expected_tracks,label + "every authored keyframe preserved " + name)
                    vectors = [tuple(frame["post"] if isinstance(frame,dict) else frame)
                               for bone in animation["bones"].values() for channel in bone.values() for frame in channel.values()]
                    check(len(set(vectors)) > 1 and any(any(abs(v)>1e-5 for v in vec) for vec in vectors),label + "nonempty changing authored motion " + name)
                    signatures.add(json.dumps(animation["bones"],sort_keys=True))
                check(len(signatures) == 5,label + "five distinct authored clips")
                look_id = f"animation.voidscape.{model_id}.look"
                look = animations[look_id]
                heads = {name for name in bone_names if name in ("head","head_l","head_r")}
                check(look == {"loop":True,"bones":{head:{"rotation":["v.look_pitch","v.look_yaw",0]} for head in heads}},label + "every head has additive gaze")
                check(entity["animations"]["look"] == look_id,label + "gaze alias resolves")
                controller_id = f"controller.animation.voidscape.{model_id}"
                controller_path = f"animation_controllers/{model_id}_native.controller.json"
                controllers = json.loads(pack.read(controller_path))["animation_controllers"]
                check(set(controllers) == {controller_id} and entity["animations"]["combat"] == controller_id,label + "animation controller resolves")
                controller = controllers[controller_id]
                check(controller["initial_state"] == "idle" and set(controller["states"]) == set(CLIPS),label + "five controller states")
                for index,name in enumerate(CLIPS):
                    state = controller["states"][name]
                    check(state["animations"] == [name],label + "controller state alias " + name)
                    check(state["transitions"] == [{other:f"q.property('voidscape:clip') == {j}"} for j,other in enumerate(CLIPS) if j!=index],label + "server clip indexes " + name)
                    check(state["blend_transition"] == .05,label + "smooth controller blending " + name)
                render_id = f"controller.render.voidscape.{model_id}"
                render_path = f"render_controllers/{model_id}_native.render.json"
                renderers = json.loads(pack.read(render_path))["render_controllers"]
                check(entity["render_controllers"] == [render_id] and set(renderers) == {render_id},label + "render controller resolves")
                check(renderers[render_id]["geometry"] == "Geometry.default" and renderers[render_id]["textures"] == ["Texture.default"],label + "render geometry/texture aliases")
                variants += 1

        report.update(result="PASS",checks=checks,new_species=57,new_rare_species=17,new_common_species=40,
                      variants=variants,total_retained_pet_variants=len(entity_paths),cubes=cubes,bones=bone_count,
                      authored_clips=variants*5,preserved_keyframes=keyframes,verified_pngs=png_count,
                      texture_resolution=[1024,1024],bedrock_version=expected_version,
                      additive_head_attention=True,continuous_client_clock=True,duplicate_entities=False,
                      source_sha256=source_hashes,pack_sha256=hashlib.sha256(args.pack.read_bytes()).hexdigest())
    except Exception as error:
        report.update(checks=checks,completed_variants=variants,error=f"{type(error).__name__}: {error}")
        raise
    finally:
        args.report.parent.mkdir(parents=True,exist_ok=True)
        args.report.write_text(json.dumps(report,indent=2)+"\n",encoding="utf8")
    print(f"PASS {checks:,} checks; {variants} new Bedrock forms; {cubes:,} original cubes; {keyframes:,} keyframes; {png_count} 1024px PNGs; version {args.expected_version}")


if __name__ == "__main__":
    main()
