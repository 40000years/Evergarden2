"""Convert the two generated transparent sources to crisp 128px game textures."""
import argparse
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--wand', type=Path, required=True)
parser.add_argument('--core', type=Path, required=True)
args = parser.parse_args()
for source, target in ((args.wand, ROOT / 'art/wands/heavens_judgment.png'),
                       (args.core, ROOT / 'art/cores/core_heavens_judgment.png')):
    image = Image.open(source).convert('RGBA').resize((128, 128), Image.Resampling.NEAREST)
    # Minecraft's flat item cutout requires binary alpha, without edge halos.
    image.putalpha(image.getchannel('A').point(lambda alpha: 255 if alpha >= 128 else 0))
    target.parent.mkdir(parents=True, exist_ok=True)
    image.save(target)
    print(target)
