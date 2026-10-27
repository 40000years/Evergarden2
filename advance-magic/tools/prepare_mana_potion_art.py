"""Normalize imagegen crystal bottle assets into matching compact client sprites."""
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
if __name__=='__main__':
    for quality in ('low','medium','good','supreme'):
        image=Image.open(ROOT/f'art/potions/source/{quality}.png').convert('RGBA')
        box=image.getchannel('A').getbbox()
        if box is None:raise ValueError('Empty bottle: '+quality)
        image=image.crop(box)
        image.thumbnail((48,56),Image.Resampling.LANCZOS)
        sprite=Image.new('RGBA',(64,64))
        sprite.alpha_composite(image,((64-image.width)//2,(64-image.height)//2))
        sprite=sprite.resize((128,128),Image.Resampling.NEAREST)
        sprite.save(ROOT/f'art/potions/mana_potion_{quality}.png',optimize=True)
    print('Prepared four crystal flask sprites with genuine transparency')
