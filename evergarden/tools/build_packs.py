"""Build original, tiny pixel-art relic assets and Java/Bedrock packs. Standard library only."""
"""Build original, tiny pixel-art relic assets and Java/Bedrock packs. Standard library only."""
import hashlib, json, struct, zlib, zipfile, tempfile
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT.parent / 'tools'))
import restoration_assets
(ROOT / 'target').mkdir(exist_ok=True)
BUILD = Path(tempfile.mkdtemp(prefix='evergarden-packs-', dir=ROOT/'target'))
DIST = ROOT / 'dist'
BEDROCK_PACK_VERSION = [3, 57, 1]  # Retired wand-crafting core assets removed.
PALETTE = {'d':(20,18,35,255),'p':(79,48,113,255),'v':(151,94,199,255),
           'c':(62,177,185,255),'g':(151,255,238,255),'w':(232,255,248,255),'s':(88,105,124,255),
           'o':(255,133,51,255),'r':(220,50,40,255),'y':(255,215,60,255)}
ITEMS = {
 'void_key':('trial_key','Evergarden Key'),
 'rift_pickaxe':('netherite_pickaxe','Rift Excavator'),
 'smelter_pickaxe':('netherite_pickaxe',"Smelter's Pickaxe"),
 'scroll_eternity':('paper','Scroll of Eternity'),
 'scroll_limit_break':('paper','Limit Break Scroll'),
 'scroll_unique':('paper','Ancient Wisdom Scroll')}

MASKS = {f'{theme}_{rank}': ('carved_pumpkin', f'{theme.title()} {rank.title()}')
         for theme in ('thorn','astral','chrono') for rank in ('mask','crown')}
ITEMS.update(MASKS)

def mask_boxes(name):
 boxes=[([3,3,3],[13,13,13])]
 if name.startswith('thorn'):
  boxes += [([1,10,5],[4,17,8]),([12,10,5],[15,17,8]),([-1,14,5],[3,16,8]),([13,14,5],[17,16,8])]
 elif name.startswith('astral'):
  boxes += [([1,7,6],[3,13,10]),([13,7,6],[15,13,10]),([6,13,6],[10,18,10])]
 else:
  boxes += [([1,10,4],[15,12,12]),([1,4,4],[3,11,12]),([13,4,4],[15,11,12]),([6,13,6],[10,16,10])]
 if name.endswith('crown'):
  boxes += [([1,17,4],[15,19,6]),([1,17,10],[15,19,12]),([1,17,6],[3,19,10]),([13,17,6],[15,19,10])]
 return boxes

def mask_assets(java,bedrock,name):
 boxes=mask_boxes(name)
 faces={direction:{'uv':[0,0,16,16],'texture':'#mask'} for direction in ['north','south','east','west','up','down']}
 model={'textures':{'mask':'voidscape:item/'+name},'elements':[{'from':a,'to':b,'faces':faces} for a,b in boxes],
        'display':{'head':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[1,1,1]},'gui':{'rotation':[20,35,0],'scale':[0.7,0.7,0.7]}}}
 write_json(java/f'assets/voidscape/models/item/{name}.json',model)
 # Bedrock binds the geometry to the wearer's equipment bone, including mob heads.
 cubes=[{'origin':[a[0]-8,a[1]-3,a[2]-8],'size':[b[i]-a[i] for i in range(3)],
         'uv':{face:{'uv':[0,0],'uv_size':[32,32]} for face in ['north','south','east','west','up','down']}} for a,b in boxes]
 write_json(bedrock/f'models/entity/{name}.geo.json',{'format_version':'1.16.0','minecraft:geometry':[{
  'description':{'identifier':'geometry.voidscape.'+name,'texture_width':32,'texture_height':32,'visible_bounds_width':3,'visible_bounds_height':3,'visible_bounds_offset':[0,1,0]},
  'bones':[{'name':'mask','binding':'q.item_slot_to_bone_name(context.item_slot)','pivot':[0,0,0],'cubes':cubes}]}]})
 write_json(bedrock/f'attachables/{name}.json',{'format_version':'1.10.0','minecraft:attachable':{'description':{
  'identifier':'voidscape:'+name,'materials':{'default':'entity_alphatest'},'textures':{'default':'textures/items/'+name},
  'geometry':{'default':'geometry.voidscape.'+name},'render_controllers':['controller.render.evergarden_mask']}}})
 return model

def write_json(path, value):
 path.parent.mkdir(parents=True,exist_ok=True)
 path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf8')

def png(path, pixels, size=32):
 def chunk(kind,data):return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
 raw=b''.join(b'\x00'+bytes(c for pixel in row for c in pixel) for row in pixels)
 path.parent.mkdir(parents=True,exist_ok=True)
 path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',size,size,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b''))

def icon(name,draw=0):
 p=[[(0,0,0,0) for _ in range(32)] for _ in range(32)]
 def rect(x1,y1,x2,y2,c):
  for y in range(max(0,y1),min(32,y2+1)):
   for x in range(max(0,x1),min(32,x2+1)):p[y][x]=PALETTE[c]
 def line(x1,y1,x2,y2,c,width=1):
  length=max(abs(x2-x1),abs(y2-y1),1)
  for n in range(length+1):
   x=round(x1+(x2-x1)*n/length);y=round(y1+(y2-y1)*n/length)
   rect(x,y,x+width-1,y+width-1,c)
 if name=='void_key':
  line(11,21,22,10,'d',3);line(12,21,22,11,'p',2);line(13,20,22,11,'c')
  for y in range(5,14):rect(18,y,26,y,'d')
  for y in range(6,13):rect(19,y,25,y,'v')
  for y in range(8,11):rect(21,y,23,y,'d')
  rect(22,9,22,9,'g')
  rect(7,24,11,26,'d');rect(8,24,10,25,'c')
  rect(12,27,14,29,'d');rect(12,27,13,28,'g')
 elif name=='smelter_pickaxe':
  line(5,27,23,9,'d',4);line(6,27,23,10,'r',2);line(7,26,23,10,'o')
  line(6,6,22,5,'d',5);line(21,6,25,18,'d',5)
  line(7,7,21,6,'r',3);line(22,8,26,19,'o',2);line(8,6,21,5,'y');rect(18,8,21,11,'y')
 elif name=='rift_pickaxe':
  line(5,27,23,9,'d',4);line(6,27,23,10,'p',2);line(7,26,23,10,'c')
  line(6,6,22,5,'d',5);line(21,6,25,18,'d',5)
  line(7,7,21,6,'v',3);line(22,8,26,19,'c',2);line(8,6,21,5,'g');rect(18,8,21,11,'g')
 elif name=='void_shard':
  for y in range(3,29):
   width=max(1,8-abs(y-16)//2);rect(16-width,y,16+width,y,'d');rect(17-width,y,15+width,y,'p')
  line(16,5,11,18,'g',2);line(11,18,17,27,'c');line(17,7,22,17,'v',2)
 else:
  rect(5,6,26,26,'d');rect(7,8,24,24,'s');rect(9,10,22,24,'p')
  rect(8,13,13,15,'g');rect(18,13,23,15,'g');rect(14,18,17,24,'d')
  line(5,7,2,2,'v',3);line(25,7,28,2,'v',3)
  if name=='captain_mask':rect(3,4,28,8,'d');rect(10,2,21,5,'v');rect(14,3,17,7,'g')
 return p

def archive(folder,path):
 with zipfile.ZipFile(path,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
  for f in sorted(folder.rglob('*')):
   if f.is_file():
    info=zipfile.ZipInfo(f.relative_to(folder).as_posix(),(2026,9,8,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
    data=f.read_bytes()
    if f.suffix in ('.json','.mcmeta'):data=json.dumps(json.loads(data),ensure_ascii=False,separators=(',',':')).encode('utf8')
    z.writestr(info,data)

def main():
 java=BUILD/'java';bedrock=BUILD/'bedrock';DIST.mkdir(parents=True,exist_ok=True)
 write_json(java/'pack.mcmeta',{'pack':{'description':'Evergarden | Ancient Relics','min_format':[88,0],'max_format':[88,0]}})
 write_json(bedrock/'manifest.json',{'format_version':2,'header':{'name':'Evergarden','description':'Evergarden relics','uuid':'df4aee6d-9e8e-4ec8-9df1-7974c6bea203','version':BEDROCK_PACK_VERSION,'min_engine_version':[1,21,0]},'modules':[{'type':'resources','uuid':'ef4aee6d-9e8e-4ec8-9df1-7974c6bea204','version':BEDROCK_PACK_VERSION}]})
 textures={};mappings={'format_version':2,'items':{}};selectors={}
 write_json(bedrock/'render_controllers/evergarden_mask.json',{'format_version':'1.8.0','render_controllers':{'controller.render.evergarden_mask':{'geometry':'Geometry.default','materials':[{'*':'Material.default'}],'textures':['Texture.default']}}})
 for name,(base,title) in ITEMS.items():
  equipment_art = ROOT / 'art' / 'equipment' / f'{name}.png'
  if equipment_art.is_file():
   data = equipment_art.read_bytes()
   dest_java = java / f'assets/voidscape/textures/item/{name}.png'
   dest_bedrock = bedrock / f'textures/items/{name}.png'
   dest_java.parent.mkdir(parents=True, exist_ok=True)
   dest_java.write_bytes(data)
   dest_bedrock.parent.mkdir(parents=True, exist_ok=True)
   dest_bedrock.write_bytes(data)
  else:
   pixels=icon(name)
   if name in MASKS:
    accent={'thorn':(95,201,151,255),'astral':(164,157,245,255),'chrono':(226,186,86,255)}[name.split('_')[0]]
    pixels=[[accent if px in (PALETTE['v'],PALETTE['c']) else px for px in row] for row in pixels]
   png(java/f'assets/voidscape/textures/item/{name}.png',pixels);png(bedrock/f'textures/items/{name}.png',pixels)
  textures['voidscape.'+name]={'textures':'textures/items/'+name}
  parent='handheld' if base in ('netherite_pickaxe','netherite_sword') else 'generated'
  model={'parent':'minecraft:item/'+parent,'textures':{'layer0':'voidscape:item/'+name}}
  if name in MASKS:
   model=mask_assets(java,bedrock,name)
  write_json(java/f'assets/voidscape/models/item/{name}.json',model)
  definition={'model':{'type':'minecraft:model','model':'voidscape:item/'+name}}
  write_json(java/f'assets/voidscape/items/{name}.json',definition)
  selectors.setdefault(base,[]).append({'when':'voidscape:'+name,'model':definition['model']})
  cat='equipment' if base in ('netherite_pickaxe','netherite_sword','bow','shield','carved_pumpkin') else 'items'
  mappings['items'].setdefault('minecraft:'+base,[]).append({'type':'definition','model':'minecraft:'+base,
    'predicate':{'type':'match','property':'custom_model_data','index':0,'value':'voidscape:'+name},
    'bedrock_identifier':'voidscape:'+name,'display_name':title,
    'bedrock_options':{'icon':'voidscape.'+name,'allow_offhand':True,'display_handheld':base in ('netherite_pickaxe','netherite_sword','bow'),'creative_category':cat}})
  if name in MASKS:
   # RelicService and GuardianAppearance set a direct item_model. Geyser v2
   # indexes mappings by that component before checking any predicate.
   entry=mappings['items']['minecraft:'+base][-1]
   entry['model']='voidscape:'+name
   del entry['predicate']
 restoration_assets.register_altars(java, bedrock, textures, mappings, selectors, write_json)
 for base,cases in selectors.items():
  if base=='carved_pumpkin':
   continue
  fallback={'type':'minecraft:model','model':'minecraft:item/'+base}
  write_json(java/f'assets/minecraft/items/{base}.json',{'model':{'type':'minecraft:select','property':'minecraft:custom_model_data','index':0,'cases':cases,'fallback':fallback}})
 import boss_orb_assets
 boss_orb_assets.register(java,bedrock,textures,mappings,selectors,write_json)
 # Keep fallback routing for active Soul Orbs and the Core of Legend.
 heart_cases=selectors.get('heart_of_the_sea',[])
 if heart_cases:
  write_json(java/'assets/minecraft/items/heart_of_the_sea.json',{'model':{'type':'minecraft:select','property':'minecraft:custom_model_data','index':0,'cases':heart_cases,'fallback':{'type':'minecraft:model','model':'minecraft:item/heart_of_the_sea'}}})
 import adventurer_weapon_assets
 adventurer_weapon_assets.register(java,bedrock,textures,mappings)
 # The relic pickaxes replace their flat items with 3D models (after the netherite_pickaxe routing above).
 import relic_pickaxe_assets
 relic_pickaxe_assets.register(java,bedrock,textures)
 write_json(bedrock/'textures/item_texture.json',{'resource_pack_name':'voidscape','texture_name':'atlas.items','texture_data':textures})
 write_json(DIST/'geyser-mappings.json',mappings)
 key_art=ROOT/'art'/'equipment'/'void_key.png'
 if key_art.is_file():
  (java/'pack.png').write_bytes(key_art.read_bytes())
  (bedrock/'pack_icon.png').write_bytes(key_art.read_bytes())
 else:
  png(java/'pack.png',icon('void_key'));png(bedrock/'pack_icon.png',icon('void_key'))
 # Bedrock caches UUID + version, not the Java ZIP SHA-1. Increment this
 # release version whenever the Bedrock assets or their item routing change.
 manifest=json.loads((bedrock/'manifest.json').read_text(encoding='utf8'))
 version=BEDROCK_PACK_VERSION
 manifest['header']['version']=version
 for module in manifest['modules']:module['version']=version
 write_json(bedrock/'manifest.json',manifest)
 print('Bedrock content version: '+'.'.join(map(str,version)))
 archive(java,DIST/'evergarden-java.zip');archive(bedrock,DIST/'evergarden-bedrock.mcpack')
 hashes={f.name:hashlib.sha1(f.read_bytes()).hexdigest() for f in [DIST/'evergarden-java.zip',DIST/'evergarden-bedrock.mcpack']}
 write_json(DIST/'pack-hashes.json',hashes)
 import judge_assets
 judge_assets.BEDROCK_VERSION=version
 judge_assets.build()
 print(json.dumps(hashes,indent=2))

if __name__=='__main__':main()
