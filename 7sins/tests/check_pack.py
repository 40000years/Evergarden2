"""Check runtime item IDs, model bounds, textures and the embedded ZIP independently."""
import hashlib
import json
from pathlib import Path
import re
import zipfile

root=Path(__file__).resolve().parents[1]
source=(root/'src/main/java/com/example/sevensins/WrathModel.java').read_text(encoding='utf8')
names=re.findall(r'add\(at, "([a-z_]+)"',source)
assert len(names)==9 and len(set(names))==9
pack=root/'src/main/resources/resource-packs/7sins-java.zip'
with zipfile.ZipFile(pack) as z:
    assert not z.testzip()
    metadata=json.loads(z.read('pack.mcmeta'))['pack']
    assert metadata['min_format']==[88,0] and metadata['max_format']==[88,0]
    assert not any(name.startswith('assets/minecraft/') for name in z.namelist()), 'No vanilla asset overrides'
    cubes=0
    models=names+[name+'_unbound' for name in ('body','head','cleaver','core')]+['ground_sword']
    for name in models:
        item=json.loads(z.read(f'assets/sevensins/items/wrath/{name}.json'))
        assert item['model']['model']==f'sevensins:wrath/{name}'
        model=json.loads(z.read(f'assets/sevensins/models/wrath/{name}.json'))
        assert model['display']['fixed']['rotation']==[0,180,0], 'Item renderer flip must be compensated'
        for ref in model['textures'].values():
            ns,path=ref.split(':')
            png=z.read(f'assets/{ns}/textures/{path}.png')
            assert png.startswith(b'\x89PNG\r\n\x1a\n')
        for cube in model['elements']:
            assert all(-16<=v<=32 for p in ('from','to') for v in cube[p]), (name,cube)
            assert all(a<b for a,b in zip(cube['from'],cube['to']))
            if 'rotation' in cube:
                assert cube['rotation']['angle'] in [-45,-22.5,0,22.5,45]
            assert len(cube['faces'])==6
            cubes+=1
    print(f'PASS: {len(names)} runtime bones, {len(models)} model variants, {cubes} valid cubes, every texture resolved, no vanilla overrides')
record=json.loads((root/'art/pack-hashes.json').read_text())
assert record['java_sha1']==hashlib.sha1(pack.read_bytes()).hexdigest()
print('PASS: pack checksum matches manifest')
