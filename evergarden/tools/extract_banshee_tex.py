import json
import base64
from io import BytesIO
from PIL import Image

d = json.load(open('evergarden/src/main/resources/modelengine/rivers/arkhaia_banshee_wail.bbmodel.bak', encoding='utf-8'))
for t in d.get('textures', []):
    source = t.get('source', '')
    if source.startswith('data:image/png;base64,'):
        b64 = source.split(',', 1)[1]
        img_data = base64.b64decode(b64)
        im = Image.open(BytesIO(img_data))
        out_name = f"evergarden/tools/{t.get('name', 'banshee_tex.png')}"
        im.save(out_name)
        print(f"Saved texture {out_name}: {im.size}, mode={im.mode}")
