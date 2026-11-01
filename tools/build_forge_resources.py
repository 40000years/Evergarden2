"""Package authored Forge imagegen art, preserving alpha with nearest-neighbor size conversion."""
from pathlib import Path
import hashlib,io,json,zipfile
from PIL import Image,ImageDraw,ImageFont

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'evergarden-forge/src/main/resources/resource-packs'
ART=ROOT/'evergarden-forge/art'
SOURCE=ART/'generated-v2/source'
NS='evergarden_forge'
VERSION=[0,5,1]
SIZE=64
GEMS={'fire':'fire_charge','water':'heart_of_the_sea','wind':'ghast_tear',
      'earth':'armadillo_scute','lightning':'glowstone_dust','light':'quartz',
      'dark':'ender_pearl','life':'emerald'}
MATS={'common':'iron_nugget','uncommon':'prismarine_crystals','rare':'echo_shard',
      'epic':'amethyst_shard','mythic':'resin_brick'}

def encode(v):return (json.dumps(v,ensure_ascii=False,separators=(',',':'))+'\n').encode()

def png(im):
    b=io.BytesIO();im.save(b,format='PNG',optimize=True);return b.getvalue()

def artwork(identifier):
    path=SOURCE/(identifier+'.png')
    with Image.open(path) as original:
        original.load()
        assert original.width==original.height and original.width>=SIZE,path
        assert 'A' in original.getbands(),f'Missing alpha: {path}'
        im=original.convert('RGBA').resize((SIZE,SIZE),Image.Resampling.NEAREST)
    low,high=im.getextrema()[3]
    assert low==0 and high>=240,f'Unusable transparency: {path}'
    bounds=im.getbbox()
    assert bounds and 0<bounds[0]<bounds[2]<SIZE and 0<bounds[1]<bounds[3]<SIZE,f'Clipped art: {path}'
    return im

def catalog(previews):
    font=ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf',14)
    title=ImageFont.truetype('C:/Windows/Fonts/segoeuib.ttf',18)
    sheet=Image.new('RGB',(840,80+10*148),(24,22,34));d=ImageDraw.Draw(sheet)
    d.text((18,10),'Evergarden Forge | Gem & material art 0.5.1',font=title,fill='#f1e6db')
    for i in range(5):d.text((i*168+48,47),f'PURITY {i+1}',font=font,fill='#d5bd94')
    for i,(name,im) in enumerate(previews):
        x,y=(i%5)*168,80+(i//5)*148;icon=im.resize((112,112),Image.Resampling.NEAREST)
        sheet.paste(icon,(x+28,y),icon);d.text((x+9,y+119),name,font=font,fill='#e1d8ec')
    sheet.save(ART/'catalog-v2.png')
    small=Image.new('RGB',(840,10*104),(40,37,48));d=ImageDraw.Draw(small)
    for i,(name,im) in enumerate(previews):
        x,y=(i%5)*168,(i//5)*104
        for size,offset in ((16,16),(32,60)):
            item=im.resize((size,size),Image.Resampling.NEAREST)
            icon=item.resize((size*2,size*2),Image.Resampling.NEAREST)
            small.paste(icon,(x+offset,y+5),icon)
        d.text((x+8,y+72),name,font=font,fill='#e1d8ec')
    small.save(ART/'generated-v2/inventory-review.png')

def main():
    OUT.mkdir(parents=True,exist_ok=True);ART.mkdir(parents=True,exist_ok=True)
    # Load all 46 originals before changing any pack or shipped texture.
    definitions=[(f'gem/{kind}_{tier}',base) for kind,base in GEMS.items() for tier in range(1,6)]
    definitions += [('material/'+kind,base) for kind,base in MATS.items()]
    definitions += [('gem_dust','gunpowder')]
    previews=[(key,artwork(key.replace('/','_'))) for key,_ in definitions]
    java={'pack.mcmeta':encode({'pack':{'description':'Evergarden Forge | Gems & Materials','min_format':[88,0],'max_format':[88,0]}})}
    bedrock={};mapping={};atlas={};provenance=[]
    for (key,base),(_,im) in zip(definitions,previews):
        data=png(im);identifier=key.replace('/','_');icon=NS+'.'+identifier
        java[f'assets/{NS}/textures/item/{key}.png']=data
        java[f'assets/{NS}/models/item/{key}.json']=encode({'parent':'minecraft:item/generated','textures':{'layer0':NS+':item/'+key}})
        java[f'assets/{NS}/items/{key}.json']=encode({'model':{'type':'minecraft:model','model':NS+':item/'+key}})
        bedrock['textures/items/forge/'+identifier+'.png']=data;atlas[icon]={'textures':'textures/items/forge/'+identifier}
        mapping.setdefault('minecraft:'+base,[]).append({'type':'definition','model':NS+':'+key,'bedrock_identifier':NS+':'+identifier,
            'display_name':'Forge','bedrock_options':{'icon':icon,'allow_offhand':True,'display_handheld':False}})
        (ART/(identifier+'.png')).write_bytes(data)
        source=SOURCE/(identifier+'.png')
        provenance.append({'model':NS+':'+key,'source':source.relative_to(ROOT).as_posix(),
            'source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'texture_sha256':hashlib.sha256(data).hexdigest()})
    bedrock['textures/item_texture.json']=encode({'resource_pack_name':'Evergarden Forge','texture_name':'atlas.items','texture_data':atlas})
    bedrock['manifest.json']=encode({'format_version':2,'header':{'name':'Evergarden Forge','description':'Forge gems and crafting materials',
        'uuid':'d17e1d40-8932-4c65-9d9a-4fed0e351050','version':VERSION,'min_engine_version':[1,21,0]},
        'modules':[{'type':'resources','uuid':'d17e1d40-8932-4c65-9d9a-4fed0e351051','version':VERSION}]})
    for filename,files in [('forge-java.zip',java),('forge-bedrock.mcpack',bedrock)]:
        with zipfile.ZipFile(OUT/filename,'w',zipfile.ZIP_DEFLATED) as z:
            for name,data in sorted(files.items()):z.writestr(zipfile.ZipInfo(name,(2026,11,1,0,0,0)),data,compress_type=zipfile.ZIP_DEFLATED)
    (OUT/'geyser-mappings.json').write_bytes(encode({'format_version':2,'items':mapping}))
    (OUT/'pack-hashes.json').write_bytes(encode({n:hashlib.sha1((OUT/n).read_bytes()).hexdigest() for n in ('forge-java.zip','forge-bedrock.mcpack')}))
    (ART/'generated-v2/manifest.json').write_text(json.dumps({'tool':'built-in imagegen','version':VERSION,'texture_size':SIZE,
        'conversion':'nearest-neighbor downscale only','items':provenance},indent=2)+'\n',encoding='utf8')
    catalog(previews)
    print(f'Packaged {len(previews)} authored {SIZE}px icons with identical Java/Bedrock textures')
if __name__=='__main__':main()
