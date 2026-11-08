"""Build the 171 original forms, native exports, client portraits, preview gallery and Bedrock pack.

Existing pets and unrelated pack entries are retained byte for byte. The current
delivered JAR is the frozen baseline; no public pack upload or live deployment occurs.
"""
import argparse
from concurrent.futures import ProcessPoolExecutor,as_completed
import hashlib
import io
import json
from pathlib import Path
import runpy
import sys
import zipfile

ROOT=Path(__file__).resolve().parents[1]
sys.path[:0]=[str(ROOT/'evergarden/tools'),str(ROOT/'tools')]
from pet_expansion_catalog import ENTRIES,SPECIES
RES=ROOT/'evergarden/src/main/resources'
OUT=ROOT/'output/pet-expansion-release'
PREVIEW=ROOT/'previews/pet-collection'
DATE=(2026,11,8,0,0,0)


def encode(value):return (json.dumps(value,ensure_ascii=False,separators=(',',':'))+'\n').encode('utf8')


def archive(data):
    with zipfile.ZipFile(io.BytesIO(data)) as source:return {n:source.read(n) for n in source.namelist() if not n.endswith('/')}


def write_zip(path,files):
    with zipfile.ZipFile(path,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as target:
        for name,data in sorted(files.items()):target.writestr(zipfile.ZipInfo(name,DATE),data,compress_type=zipfile.ZIP_DEFLATED)


def blueprint(species):
    import pet_model_engine as pme
    entry=next(e for e in ENTRIES if e['id']==species);rows=[]
    for stage in pme.STAGES:
        model,texture=pme.build_model(species,stage)
        target=pme.OUT/(model['name']+'.bbmodel')
        target.write_text(json.dumps(model,ensure_ascii=False,separators=(',',':'))+'\n',encoding='utf8')
        texture.save(pme.ART/(model['name']+'.png'))
        rows.append(dict(id=model['name'],species=species,stage=stage,title=entry['display'],rarity=entry['rarity'],
                         cubes=len(model['elements'])-1,bones=len(model['animations'][0]['animators']),
                         animations=[a['name'] for a in model['animations']],texture_size=1024,
                         sha256=hashlib.sha256(target.read_bytes()).hexdigest()))
    return rows


def blueprints(workers,species_list=SPECIES):
    import pet_model_engine as pme
    pme.OUT.mkdir(parents=True,exist_ok=True);pme.ART.mkdir(parents=True,exist_ok=True)
    if set(species_list)==set(SPECIES):
        with zipfile.ZipFile(OUT/'before/evergarden.jar') as old:existing=json.loads(old.read('modelengine/pets/manifest.json'))
    else:existing=json.loads((pme.OUT/'manifest.json').read_text(encoding='utf8'))
    tiers={e['id']:e['rarity'] for e in ENTRIES}
    for entry in existing:entry['rarity']=tiers.get(entry['species'],'mythical' if entry['species'] in pme.MYTHICAL else 'common')
    new=[]
    with ProcessPoolExecutor(max_workers=workers) as pool:
        tasks={pool.submit(blueprint,s):s for s in species_list}
        for task in as_completed(tasks):
            rows=task.result();new.extend(rows);print('Sculpted '+tasks[task]+': '+','.join(str(r['cubes']) for r in rows)+' parts',flush=True)
    by_id={entry['id']:entry for entry in existing+new}
    ordered=[by_id[f'everpet_{s}_{stage}'] for s in pme.SPECIES for stage in pme.STAGES]
    assert len(ordered)==282
    (pme.OUT/'manifest.json').write_text(json.dumps(ordered,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    with zipfile.ZipFile(OUT/'before/evergarden.jar') as old:
        for name in old.namelist():
            if name.startswith('modelengine/pets/') and name.endswith('.bbmodel'):assert old.read(name)==(RES/name).read_bytes(),name
    additions=[entry for entry in ordered if entry['species'] in SPECIES]
    (OUT/'sculpt-report.json').write_bytes(encode(dict(species=57,variants=171,parts=sum(r['cubes'] for r in additions),
                         min_parts=min(r['cubes'] for r in additions),max_parts=max(r['cubes'] for r in additions),models=additions)))


def portrait(species):
    import pet_portraits as pp
    import preview_pet_models as previews
    from PIL import Image,ImageDraw,ImageFont
    entry=next(e for e in ENTRIES if e['id']==species)
    span=previews.span_for(species);files={};sheet=Image.new('RGB',(1320,510),'#171522')
    draw=ImageDraw.Draw(sheet);font=ImageFont.truetype('C:/Windows/Fonts/tahoma.ttf',20)
    draw.text((20,16),entry['display']+'  /  '+entry['thai'],font=font,fill='#efdcb7')
    for i,stage in enumerate(('baby','medium','adult')):
        frame=previews.frame(species,stage,span,size=520)
        frame.save(PREVIEW/f'{species}-{stage}.png',optimize=True)
        thumb=frame.copy();thumb.thumbnail((440,440));sheet.paste(thumb,(i*440,52))
        draw.text((i*440+20,474),('BABY','MEDIUM','ADULT')[i],font=font,fill='#a5d6d0')
        cropped=pp._crop(frame)
        for dim in (False,True):
            name=f'pet_{species}_{stage}'+('_dim' if dim else '')
            image=pp.menu.portrait(cropped,stage,dim).image()
            output=io.BytesIO();image.save(output,format='PNG',optimize=True)
            files[f'assets/evergarden/textures/item/menu/{name}.png']=output.getvalue()
            files[f'assets/evergarden/models/item/menu/{name}.json']=encode({'parent':'minecraft:item/generated','textures':{'layer0':f'evergarden:item/menu/{name}'}})
            files[f'assets/evergarden/items/menu_{name}.json']=encode({'model':{'type':'minecraft:model','model':f'evergarden:item/menu/{name}'}})
    sheet.save(PREVIEW/f'{species}-growth.png',optimize=True)
    return files


def portraits(workers,species_list=SPECIES):
    PREVIEW.mkdir(parents=True,exist_ok=True)
    if set(species_list)==set(SPECIES):
        with zipfile.ZipFile(OUT/'before/evergarden.jar') as old:files=archive(old.read('pet-portraits.zip'))
    else:files=archive((RES/'pet-portraits.zip').read_bytes())
    previous=dict(files)
    with ProcessPoolExecutor(max_workers=workers) as pool:
        tasks={pool.submit(portrait,s):s for s in species_list}
        for task in as_completed(tasks):files.update(task.result());print('Rendered '+tasks[task]+' / 3 growth forms + 6 menu portraits',flush=True)
    assert all(files[n]==data for n,data in previous.items() if not any('/pet_'+s+'_' in n or '/menu_pet_'+s+'_' in n for s in species_list))
    write_zip(RES/'pet-portraits.zip',files)
    gallery()


def gallery():
    from PIL import Image,ImageDraw,ImageFont
    roster=json.loads((RES/'pet-expansion.json').read_text(encoding='utf8'))['species']
    # Recompose labels from the cached exact mesh renders. Tahoma includes Thai;
    # Pillow does not perform the browser's automatic per-glyph font fallback.
    label=ImageFont.truetype('C:/Windows/Fonts/tahoma.ttf',20)
    for entry in roster:
        sheet=Image.new('RGB',(1320,510),'#171522');draw=ImageDraw.Draw(sheet)
        draw.text((20,16),entry['display']+'  /  '+entry['thai'],font=label,fill='#efdcb7')
        for i,stage in enumerate(('baby','medium','adult')):
            image=Image.open(PREVIEW/f'{entry["id"]}-{stage}.png');image.thumbnail((440,440));sheet.paste(image,(i*440,52))
            draw.text((i*440+20,474),('BABY','MEDIUM','ADULT')[i],font=label,fill='#a5d6d0')
        sheet.save(PREVIEW/f'{entry["id"]}-growth.png',optimize=True)
    for tier in ('rare','common'):
        names=[e for e in roster if e['rarity']==tier];columns=5
        sheet=Image.new('RGB',(1600,370*((len(names)+columns-1)//columns)),'#171522');draw=ImageDraw.Draw(sheet)
        font=ImageFont.truetype('C:/Windows/Fonts/tahoma.ttf',16)
        for i,entry in enumerate(names):
            x=i%columns*320;y=i//columns*370
            image=Image.open(PREVIEW/f'{entry["id"]}-adult.png');image.thumbnail((320,320));sheet.paste(image,(x,y))
            draw.text((x+10,y+323),entry['display'],font=font,fill='#efdcb7')
            draw.text((x+10,y+346),entry['thai'],font=font,fill='#a5d6d0')
        sheet.save(PREVIEW/f'{tier}-collection.png',optimize=True)
    # This local review artifact shows exact shipped mesh renders, plus real gameplay descriptions.
    payload=json.dumps(roster,ensure_ascii=False).replace('</','<\\/')
    html='''<!doctype html><html lang="th"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Evergarden · Pet Collection</title><style>
    :root{color-scheme:dark}body{margin:0;background:#14131d;color:#eee7dc;font:16px/1.7 "Segoe UI",sans-serif}main{max-width:1320px;margin:auto;padding:30px 24px}h1{font:48px Georgia,serif;margin:4px 0 12px;color:#e8c893}p{color:#b9b7c8}nav{display:flex;gap:12px;position:sticky;top:0;background:#14131df0;padding:15px 0;z-index:1}input,select{font:inherit;padding:10px 14px;background:#24222f;border:1px solid #4b465e;border-radius:9px;color:inherit}input{flex:1;min-width:120px}article{padding:26px 0;border-bottom:1px solid #3b3547}h2{font-size:25px;margin:0;color:#efd4a7}img{width:100%;height:auto;border-radius:14px}.badge{display:inline-block;background:#323b37;border-radius:5px;padding:1px 9px;font-size:13px;color:#acd5b0}.rare{background:#443452;color:#dbb9f2}.growth{display:grid;grid-template-columns:repeat(3,1fr);gap:15px;color:#b8c8d5}details{background:#1f1c2a;margin-top:14px;padding:12px 18px;border-radius:9px}summary{cursor:pointer;color:#e1c292}code{color:#9bcec6;font-size:13px}@media(max-width:700px){h1{font-size:35px}.growth{grid-template-columns:1fr}nav{flex-wrap:wrap}}
    </style><main><p>EVERGARDEN / THE LIVING COLLECTION</p><h1>57 New Companions</h1><p>สัตว์ใหม่ 57 ชนิด · 171 ร่าง · ปกติ 40 และหายาก 17<br>ภาพเรนเดอร์จากโมเดลและเทกเจอร์ที่บรรจุจริง ใช้สเกลกล้องเดียวกันในแต่ละชนิด<br>บัฟแสดงค่าร่างโตเต็มวัย: ร่างกลางมีรอบฟื้นฟูช้าลง 1.4 เท่า ร่างเด็ก 2 เท่า · บัฟทำงานจากตัวแรก</p><nav><input id="q" placeholder="ค้นหาชื่อไทย / English / บัฟ"><select id="tier"><option value="">ทุกระดับ</option><option value="rare">หายาก 17</option><option value="common">ปกติ 40</option></select></nav><p id="count"></p><section id="list"></section></main><script>const data=PAYLOAD;const esc=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));function render(){const q=document.querySelector('#q').value.toLowerCase(),tier=document.querySelector('#tier').value;const rows=data.filter(e=>(!tier||e.rarity===tier)&&JSON.stringify(e).toLowerCase().includes(q));document.querySelector('#count').textContent=rows.length+' ชนิด';document.querySelector('#list').innerHTML=rows.map(e=>`<article><span class="badge ${e.rarity}">${e.rarity==='rare'?'หายาก':'ปกติ'}</span><h2>${esc(e.thai)} <small>· ${esc(e.display)}</small></h2><p>${esc(e.design)}</p><img loading="lazy" src="${e.id}-growth.png" alt="${esc(e.display)} baby medium adult"><div class="growth">${e.growth.map((g,i)=>`<div><b>${['เด็ก','กลาง','โตเต็มวัย'][i]}</b><br>${esc(g)}</div>`).join('')}</div><details><summary>${esc(e.passiveName)} · บัฟและวิธีพบ</summary><p>${esc(e.passive)}</p><p>ช่วยสู้: ${esc(e.traitName)} · ${esc(e.trait)}</p><p>อาหาร: ${esc(e.food)} · ถิ่น: ${esc(e.habitat)} · ${e.flies?'บินได้':'เดินบนพื้น'}</p><code>/evergarden pets wild spawn ${e.id} baby</code></details></article>`).join('')}document.querySelector('#q').oninput=render;document.querySelector('#tier').onchange=render;render();</script></html>'''.replace('PAYLOAD',payload)
    (PREVIEW/'index.html').write_text(html,encoding='utf8')
    write_zip(ROOT/'dist/evergarden-pet-collection-preview.zip',{p.name:p.read_bytes() for p in PREVIEW.iterdir() if p.suffix in ('.png','.html','.gif')})


def bedrock():
    import pet_bedrock_assets
    with zipfile.ZipFile(OUT/'before/evergarden.jar') as old:
        files=archive(old.read('resource-packs/evergarden-bedrock.mcpack'))
        mappings=json.loads(old.read('resource-packs/geyser-mappings.json'))
        hashes=json.loads(old.read('resource-packs/pack-hashes.json'));design=json.loads(old.read('judge-design.json'))
    previous=dict(files)
    new=pet_bedrock_assets.assets()
    selected={name:data for name,data in new.items() if any(f'everpet_{s}_' in name or name==f'textures/menu/pet_{s}_{stage}.png' for s in SPECIES for stage in ('baby','medium','adult'))}
    assert sum(n.startswith('entity/') for n in selected)==171
    assert not(set(files)&set(selected)), 'New Bedrock entities unexpectedly replace existing entries'
    files.update(selected)
    atlas=json.loads(files['textures/item_texture.json'])
    with zipfile.ZipFile(RES/'pet-portraits.zip') as portraits:
        paper=mappings['items'].setdefault('minecraft:paper',[])
        for species in SPECIES:
            for stage in ('baby','medium','adult'):
                for suffix in ('','_dim'):
                    name=f'menu_pet_{species}_{stage}{suffix}';icon='evergarden.'+name;texture='textures/items/evergarden_menu/'+name
                    assert not any(e.get('model')=='evergarden:'+name for e in paper)
                    files[texture+'.png']=portraits.read(f'assets/evergarden/textures/item/menu/{name[5:]}.png')
                    atlas['texture_data'][icon]={'textures':texture}
                    paper.append(dict(type='definition',model='evergarden:'+name,bedrock_identifier='evergarden:'+name,display_name='Pet',
                                      bedrock_options=dict(icon=icon,allow_offhand=False,display_handheld=False)))
    manifest=json.loads(files['manifest.json']);version=list(manifest['header']['version']);version=[version[0],version[1]+1,0]
    manifest['header']['version']=version
    for module in manifest['modules']:module['version']=version
    files['manifest.json']=encode(manifest);files['textures/item_texture.json']=encode(atlas)
    assert all(files[n]==data for n,data in previous.items() if n not in ('manifest.json','textures/item_texture.json'))
    dist=ROOT/'evergarden/dist';pack=dist/'evergarden-bedrock.mcpack';write_zip(pack,files)
    (dist/'geyser-mappings.json').write_bytes(encode(mappings))
    hashes['evergarden-bedrock.mcpack']=hashlib.sha1(pack.read_bytes()).hexdigest();(dist/'pack-hashes.json').write_bytes(encode(hashes))
    design['bedrock-version']='.'.join(map(str,version));(RES/'judge-design.json').write_bytes(encode(design))
    print(f'Bedrock: 171 new entities, 342 portrait items, content {version}; {pack.stat().st_size:,} bytes',flush=True)


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--stage',choices=('all','blueprints','native','portraits','bedrock','gallery'),default='all');parser.add_argument('--workers',type=int,default=4);parser.add_argument('--species',choices=SPECIES,nargs='+');args=parser.parse_args()
    OUT.mkdir(parents=True,exist_ok=True)
    selected=tuple(args.species or SPECIES)
    if args.stage in ('all','blueprints'):blueprints(args.workers,selected)
    if args.stage in ('all','native'):runpy.run_path(str(ROOT/'tools/export_native_pet_models.py'),run_name='__main__')
    if args.stage in ('all','portraits'):portraits(args.workers,selected)
    if args.stage in ('all','bedrock'):bedrock()
    if args.stage=='gallery':gallery()


if __name__=='__main__':main()
