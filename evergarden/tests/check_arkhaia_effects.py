"""Check owned violet effects and both clients' routes, without a graphical client."""
from pathlib import Path
import io,json,zipfile
from PIL import Image
ROOT=Path(__file__).resolve().parents[2]
with zipfile.ZipFile(ROOT/'evergarden/dist/judge-java.zip') as java,zipfile.ZipFile(ROOT/'evergarden/dist/evergarden-bedrock.mcpack') as bedrock:
    for name in ('arkhaia_rose','arkhaia_orbit','arkhaia_ray','arkhaia_gate','arkhaia_crown','arkhaia_fracture','arkhaia_weakpoint'):
        png=java.read(f'assets/voidscape/textures/effect/{name}.png')
        assert png==bedrock.read(f'textures/particle/{name}.png')
        im=Image.open(io.BytesIO(png)).convert('RGBA');assert im.size==(1024,1024)
        pixels=[p for p in im.get_flattened_data() if p[3]>64]
        assert pixels and sum(p[2] for p in pixels)>sum(p[0] for p in pixels)>sum(p[1] for p in pixels),'violet palette'
        assert im.getchannel('A').getextrema()==(0,255),'transparent spell art'
        model=json.loads(java.read(f'assets/voidscape/items/{name}.json'))
        assert model['model']['model']=='voidscape:effect/'+name
        for suffix in ('','_flat'):
            effect=json.loads(bedrock.read(f'particles/{name}{suffix}.particle.json'))['particle_effect']
            assert effect['description']['identifier']=='voidscape:'+name+suffix
            assert effect['description']['basic_render_parameters']['texture']=='textures/particle/'+name
    assert java.read('assets/voidscape/textures/effect/arkhaia_rose.png')!=java.read('assets/voidscape/textures/effect/judge_seal.png')
print('PASS: violet compositions, transparent art, Java model routes and Bedrock plane emitters')
