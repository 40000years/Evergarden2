"""Build the 10,000 HP Seraphon encounter and adapt archived golden spell-circle vectors."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import argparse,hashlib,io,json,os,shutil,subprocess,sys
from PIL import Image,ImageDraw

ROOT=Path(__file__).resolve().parents[1]
RELEASE=ROOT/'output/seraphon-release-20261023'
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--base-jar',type=Path,default=ROOT/'output/boss-hitbox-release-20261023/build-v2/evergarden.jar')
parser.add_argument('--output',type=Path,default=RELEASE/'build')
parser.add_argument('--magic-jar',type=Path,default=ROOT/'output/boss-hitbox-paper-v4-20261023/plugins/advance-magic.jar')
args=parser.parse_args();build=args.output.resolve();build.mkdir(parents=True,exist_ok=True)
classes=build/'classes';classes.mkdir(exist_ok=False)
base=args.base_jar.resolve();RELEASE.mkdir(parents=True,exist_ok=True)
if not (RELEASE/'evergarden-before.jar').exists():shutil.copy2(base,RELEASE/'evergarden-before.jar')
families=['com/example/voidscape/'+n for n in ['VoidscapePlugin','boss/SeraphonBoss','boss/SeraphonVisual','boss/SeraphonEffects','boss/SeraphonRules','boss/JudgeSigils','boss/BossHitbox','boss/WorldBossManager']]
sources=[ROOT/'evergarden/src/main/java'/(n+'.java') for n in families]
pinned=(ROOT/'output/banshee-hunt-build-v1-20261015/evergarden-javac.args').read_text(encoding='utf8').splitlines()[6].strip('"')
classpath=os.pathsep.join((str(base),str(args.magic_jar.resolve()),pinned));(build/'classpath.txt').write_text(classpath,encoding='utf8')
options=['--release','25','-proc:none','-encoding','UTF-8','-cp',classpath,'-d',str(classes),*map(str,sources)]
argfile=build/'javac.args';argfile.write_text('\n'.join('"'+s.replace('\\','/')+'"' for s in options),encoding='utf8')
subprocess.run(['java',str(ROOT/'tools/CompileJava.java'),str(argfile)],check=True)

sys.path.insert(0,str(ROOT/'tools'))
from judgment_seals import geometry
art=ROOT/'evergarden/art/seraphon-effects';art.mkdir(parents=True,exist_ok=True)

def seal(safe=False,orbit=False):
    palette=((120,238,255),(225,255,255)) if safe else ((255,214,64),(255,248,208))
    shapes=geometry(1 if safe else 0)
    if orbit:shapes=[s for s in shapes if s[0]=='circle' and s[1]>=390 or s[0]=='path' and all((x-512)**2+(y-512)**2>390**2 for x,y in s[1])]
    image=Image.new('RGBA',(1024,1024));svg=['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024">']
    for extra,alpha in ((12,14),(6,35),(2,85),(0,255)):
        layer=Image.new('RGBA',image.size);draw=ImageDraw.Draw(layer)
        svg.append(f'<g fill="none" stroke-linecap="round" stroke-linejoin="round" opacity="{alpha/255:.4f}">')
        for kind,data,width,ivory in shapes:
            rgb=palette[1 if ivory and not extra else 0];color=(*rgb,alpha);stroke='#%02x%02x%02x'%rgb
            if kind=='circle':
                draw.ellipse((512-data,512-data,512+data,512+data),outline=color,width=width+extra)
                svg.append(f'<circle cx="512" cy="512" r="{data}" stroke="{stroke}" stroke-width="{width+extra}"/>')
            else:
                draw.line(data,fill=color,width=width+extra,joint='curve')
                svg.append('<polyline points="'+' '.join(f'{x:.2f},{y:.2f}' for x,y in data)+f'" stroke="{stroke}" stroke-width="{width+extra}"/>')
        image=Image.alpha_composite(image,layer);svg.append('</g>')
    svg.append('</svg>');return image,'\n'.join(svg)

images={}
for name,safe,orbit in [('seraphon_seal',False,False),('seraphon_orbit',False,True),('seraphon_sanctuary',True,False)]:
    im,svg=seal(safe,orbit);images[name]=im;(art/(name+'.svg')).write_text(svg,encoding='utf8')
im=Image.new('RGBA',(256,1024));d=ImageDraw.Draw(im)
for width,alpha in [(150,8),(90,18),(48,45),(20,150),(7,255)]:d.line([(128,0),(128,1024)],fill=(255,241,170,alpha),width=width)
for x in (58,198):d.line([(x,0),(x,1024)],fill=(255,213,78,180),width=3)
images['seraphon_ray']=im
im=Image.new('RGBA',(512,1024));d=ImageDraw.Draw(im)
outline=[(256,14),(360,220),(419,465),(365,728),(275,1000),(211,940),(93,706),(88,422),(172,172)]
d.polygon(outline,fill=(255,249,217,235),outline=(255,211,74,255),width=8)
d.line([(256,30),(244,960)],fill=(255,191,43,255),width=12)
for y in range(180,850,70):
    reach=150*(1-abs(y-500)/600)
    d.line([(247,y+65),(247-reach,y-45)],fill=(227,195,113,220),width=5)
    d.line([(247,y+65),(247+reach,y-45)],fill=(255,217,97,220),width=5)
images['seraphon_feather']=im

def jb(obj):return json.dumps(obj,separators=(',',':'),ensure_ascii=False).encode('utf8')
def archive(data):
    with ZipFile(io.BytesIO(data)) as z:return {n:z.read(n) for n in z.namelist() if not n.endswith('/')}
def packed(entries):
    buffer=io.BytesIO()
    with ZipFile(buffer,'w',ZIP_DEFLATED) as z:
        for n,data in entries.items():z.writestr(n,data)
    return buffer.getvalue()
with ZipFile(base) as z:original={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
entries=original.copy();java=archive(entries['resource-packs/judge-java.zip']);bedrock=archive(entries['resource-packs/evergarden-bedrock.mcpack'])
added_java=[];added_bedrock=[]
for name,im in images.items():
    stream=io.BytesIO();im.save(stream,format='PNG');png=stream.getvalue();(art/(name+'.png')).write_bytes(png)
    additions={f'assets/voidscape/textures/effect/{name}.png':png,
        f'assets/voidscape/models/effect/{name}.json':jb({'ambientocclusion':False,'textures':{'seal':'voidscape:effect/'+name},'elements':[{'from':[0,0,7.99],'to':[16,16,8.01],'shade':False,'faces':{s:{'texture':'#seal','uv':[0,0,16,16]} for s in ['north','south']}}]}),
        f'assets/voidscape/items/{name}.json':jb({'model':{'type':'minecraft:model','model':'voidscape:effect/'+name}})}
    java.update(additions);added_java.extend(additions)
    bedrock[f'textures/particle/{name}.png']=png;added_bedrock.append(f'textures/particle/{name}.png')
    for flat in (False,True):
        billboard={'size':['variable.line_width * 0.5','variable.line_height * 0.5'],'facing_camera_mode':'emitter_transform_xz' if flat else 'direction_z','uv':{'texture_width':im.width,'texture_height':im.height,'uv':[0,0],'uv_size':[im.width,im.height]}}
        if not flat:billboard['direction']={'mode':'custom','custom_direction':['variable.line_normal_x','variable.line_normal_y','variable.line_normal_z']}
        suffix='_flat' if flat else '';path=f'particles/{name}{suffix}.particle.json'
        bedrock[path]=jb({'format_version':'1.10.0','particle_effect':{'description':{'identifier':'voidscape:'+name+suffix,'basic_render_parameters':{'material':'particles_blend','texture':'textures/particle/'+name}},'components':{
            'minecraft:emitter_lifetime_once':{'active_time':.01},'minecraft:emitter_rate_instant':{'num_particles':1},
            'minecraft:emitter_shape_point':{'offset':['variable.line_offset_x','variable.line_offset_y','variable.line_offset_z'],'direction':[0,0,0]},
            'minecraft:particle_lifetime_expression':{'max_lifetime':.20},'minecraft:particle_initial_speed':0,
            'minecraft:particle_initial_spin':{'rotation':'variable.line_rotation','rotation_rate':0},'minecraft:particle_motion_dynamic':{},'minecraft:particle_appearance_billboard':billboard}}})
        added_bedrock.append(path)
meta=json.loads(bedrock['manifest.json']);previous_version=meta['header']['version'];version=[previous_version[0],previous_version[1]+1,0]
meta['header']['version']=version
for module in meta['modules']:module['version']=version
bedrock['manifest.json']=jb(meta)
for folder,changed,allowed in [('judge-java.zip',java,set(added_java)),('evergarden-bedrock.mcpack',bedrock,set(added_bedrock)|{'manifest.json'})]:
    old=archive(original['resource-packs/'+folder]);assert all(n in allowed or changed[n]==v for n,v in old.items()),folder
    entries['resource-packs/'+folder]=packed(changed)
    (build/folder).write_bytes(entries['resource-packs/'+folder])
hashes=json.loads(entries['resource-packs/pack-hashes.json'])
for n in ('judge-java.zip','evergarden-bedrock.mcpack'):hashes[n]=hashlib.sha1(entries['resource-packs/'+n]).hexdigest()
entries['resource-packs/pack-hashes.json']=jb(hashes)
belongs=lambda n:any(n==f+'.class' or n.startswith(f+'$') for f in families)
for n in list(entries):
    if belongs(n):del entries[n]
for p in classes.rglob('*.class'):
    n=p.relative_to(classes).as_posix();assert belongs(n),n;entries[n]=p.read_bytes()
entries['plugin.yml']=(ROOT/'evergarden/src/main/resources/plugin.yml').read_bytes()
config=entries['config.yml'].decode('utf8')
if '\nseraphon:' not in config:config+='\nseraphon:\n  enabled: true\n  health: 10000.0\n'
entries['config.yml']=config.encode('utf8')
for n,v in original.items():
    assert belongs(n) or n in {'plugin.yml','config.yml','resource-packs/judge-java.zip','resource-packs/evergarden-bedrock.mcpack','resource-packs/pack-hashes.json'} or entries[n]==v,n
jar=build/'evergarden.jar';jar.write_bytes(packed(entries))
model_files={n:hashlib.sha256(v).hexdigest() for n,v in original.items() if n.startswith('modelengine/')}
assert all(entries[n]==original[n] for n in model_files)
manifest={'artifact':str(jar),'sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'base_sha256':hashlib.sha256(base.read_bytes()).hexdigest(),'health':10000,'bedrock_pack_version':version,
    'sources':{p.relative_to(ROOT).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in sources},'preserved_model_files':model_files,
    'reused_art_source':'tools/judgment_seals.py','new_java_effect_entries':added_java,'new_bedrock_effect_entries':added_bedrock,
    'changed_entries':sorted(n for n in set(original)|set(entries) if original.get(n)!=entries.get(n))}
(build/'build-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf8')
print('PASS: built Seraphon, 5 adapted effects, Java/Bedrock packs '+'.'.join(map(str,version))+'; all existing model assets preserved')
print(jar)
