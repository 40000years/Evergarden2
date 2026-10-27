import json
from pathlib import Path
import sys

from PIL import Image, ImageDraw

ROOT = Path('.').resolve()
sys.path.insert(0, str(ROOT / 'evergarden/tools'))
from boss_render import Rig, render

out_dir = ROOT / 'previews/original-inspection'
out_dir.mkdir(parents=True, exist_ok=True)

def render_bak(model_file, label, out_png, clip='idle', yaws=(0, 30, -30, 180)):
    with open(model_file, 'r', encoding='utf-8') as f:
        d = json.load(f)
    rig = Rig(d)
    sz = 512
    tiles = []
    for yaw in yaws:
        c = clip if clip in rig.clips else 'hover' if 'hover' in rig.clips else list(rig.clips.keys())[0]
        img, _ = render(rig, clip=c, time=0.0, yaw=yaw, elevation=6.0, size=sz)
        d = ImageDraw.Draw(img)
        d.rectangle([0,0,sz,36], fill=(0,0,0,200))
        d.text((15,10), f"{label} (yaw={yaw})", fill=(255,255,255))
        tiles.append(img)
    sheet = Image.new('RGBA', (sz * 2, sz * 2))
    sheet.paste(tiles[0], (0, 0))
    sheet.paste(tiles[1], (sz, 0))
    sheet.paste(tiles[2], (0, sz))
    sheet.paste(tiles[3], (sz, sz))
    sheet.save(out_png)
    print(f"Saved {out_png}")

render_bak('evergarden/src/main/resources/modelengine/rivers/arkhaia_acheron.bbmodel.bak', 'ORIGINAL ACHERON', out_dir / 'acheron_original_inspect.png')
render_bak('evergarden/src/main/resources/modelengine/rivers/arkhaia_banshee_wail.bbmodel.bak', 'ORIGINAL BANSHEE', out_dir / 'banshee_original_inspect.png', clip='hover')
