"""Validate and render rare expansion models in memory, without writing ship assets."""
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

from pet_expansion_rare_sculpt import SPECIES, build
from pet_model_engine import build_model
from boss_render import Rig, render


def main():
    out = Path(__file__).resolve().parents[1] / 'art' / 'pets' / 'rare_expansion_preview'
    out.mkdir(parents=True, exist_ok=True)
    records = []
    sheet = Image.new('RGB', (1600, 1440), (20, 23, 32))
    draw = ImageDraw.Draw(sheet)
    stages = ('baby', 'medium', 'adult')
    for row, species in enumerate(SPECIES):
        for stage in stages:
            data = build(species, stage)
            assert 10 <= len(data['bones']) <= 25, (species,stage,'bones')
            assert len({p['name'] for p in data['parts']}) == len(data['parts'])
            if stage == 'adult':assert 150 <= len(data['parts']) <= 450
            for part in data['parts']:
                assert part['material'] in data['palette'], (species,part['material'])
                assert part['bone'] in data['bones']
                assert all(math.isfinite(v) for k in ('from','size','rotation','origin') for v in part.get(k,[]))
                assert all(v > 0 for v in part['size'])
            for name, (parent,pivot) in data['bones'].items():
                assert parent is None or parent in data['bones']
                seen=set()
                while parent is not None:
                    assert parent not in seen, (species,'cyclic bones')
                    seen.add(parent);parent=data['bones'][parent][0]
            records.append(dict(species=species,stage=stage,parts=len(data['parts']),bones=len(data['bones']),height=data['height']))
        model, _ = build_model(species,'adult')
        rig=Rig(model)
        pts=np.concatenate([w for _,w,_ in rig.cuboids(rig.matrices())])
        assert pts[:,1].min() >= -.001, (species,'rotated floor',pts[:,1].min())
        tile,_=render(rig,size=310,supersample=1,yaw=28,elevation=16,ground=False,bloom=False,background=(20,23,32))
        tile.save(out/(species+'_adult.png'))
        x=(row%5)*320;y=(row//5)*360
        sheet.paste(tile,(x+5,y));draw.text((x+8,y+315),species.replace('_',' ').title(),fill=(232,223,201))
        draw.text((x+8,y+333),f"{len(build(species,'adult')['parts'])} parts",fill=(148,165,178))
        print('rendered',species,flush=True)
    sheet.save(out/'rare_expansion_adults.png')
    (out/'rare_expansion_validation.json').write_text(json.dumps(records,indent=2),encoding='utf8')
    for species in ('prism_kirin','sakura_baku','sunforge_lion','opal_chimera','verdant_capricorn'):
        row=Image.new('RGB',(1440,480),(20,23,32))
        for col,stage in enumerate(stages):
            model,_=build_model(species,stage)
            tile,_=render(Rig(model),size=480,supersample=1,yaw=28,elevation=16,ground=False,bloom=False,background=(20,23,32))
            row.paste(tile,(col*480,0))
        row.save(out/(species+'_growth.png'))
    print('validated',len(records),'sculptures; output',out,flush=True)


if __name__=='__main__':main()
