"""Build original, tiny pixel-art relic assets and Java/Bedrock packs. Standard library only."""
"""Build original, tiny pixel-art relic assets and Java/Bedrock packs. Standard library only."""
import hashlib, json, struct, zlib, zipfile, tempfile
from pathlib import Path
import crop_assets
import portal_assets
import aeternum_assets
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT.parent / 'tools'))
import restoration_assets
(ROOT / 'target').mkdir(exist_ok=True)
BUILD = Path(tempfile.mkdtemp(prefix='evergarden-packs-', dir=ROOT/'target'))
DIST = ROOT / 'dist'
PALETTE = {'d':(20,18,35,255),'p':(79,48,113,255),'v':(151,94,199,255),
           'c':(62,177,185,255),'g':(151,255,238,255),'w':(232,255,248,255),'s':(88,105,124,255),
           'o':(255,133,51,255),'r':(220,50,40,255),'y':(255,215,60,255)}
ITEMS = {
 'void_key':('trial_key','Evergarden Key'),
 'rift_pickaxe':('netherite_pickaxe','Rift Excavator'),
 'smelter_pickaxe':('netherite_pickaxe',"Smelter's Pickaxe"),
 'storm_bow':('bow','Storm Verdict'),
 'nova_bow':('bow','Nova Bow'),
 'rift_blade':('netherite_sword','Rift Blade'),
 'eternal_aegis':('shield','Eternal Aegis'),
 'scroll_eternity':('paper','Scroll of Eternity'),
 'scroll_limit_break':('paper','Limit Break Scroll'),
 'scroll_unique':('paper','Ancient Wisdom Scroll'),
 'astral_dust':('sugar','Astral Dust'),
 'key_shard':('prismarine_shard','Evergarden Key Shard'),
 'repair_stone':('flint','Vault Repair Stone'),
 'void_elixir':('honey_bottle','Void Walker Elixir')}

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

def bow_attachable(bedrock, name):
 # Follow Bedrock's vanilla/custom bow attachable layout. A texture_mesh is
 # required here; a cube-bound item does not inherit bow wield behaviour.
 geometries=[]
 for suffix,texture,position in [
   ('standby','default',[2.0,1.0,-2.0]),
   ('pulling_0','pulling_0',[2.0,1.0,-1.0]),
   ('pulling_1','pulling_1',[2.01,1.0,-1.0]),
   ('pulling_2','pulling_2',[2.01,1.0,-1.0])]:
  geometries.append({
   'description':{'identifier':f'geometry.voidscape.{name}_{suffix}',
                  'texture_width':16.0,'texture_height':16.0},
   'bones':[{'name':'rightitem','texture_meshes':[{
    'local_pivot':[6.0,0.0,6.0],'position':position,
    'rotation':[0.0,-135.0,90.0],'texture':texture}]}]})
 write_json(bedrock/f'models/entity/{name}.geo.json', {
  'format_version':'1.16.0','minecraft:geometry':geometries})
 controller='controller.render.voidscape_'+name
 write_json(bedrock/f'render_controllers/{name}.json', {
  'format_version':'1.10','render_controllers':{controller:{
   'arrays':{
    'textures':{'array.bow_texture_frames':['texture.default','texture.pulling_0','texture.pulling_1','texture.pulling_2']},
    'geometries':{'array.bow_geo_frames':['geometry.default','geometry.pulling_0','geometry.pulling_1','geometry.pulling_2']}},
   'geometry':'array.bow_geo_frames[variable.bow_tex_idx]',
   'materials':[{'*':'variable.is_enchanted ? material.enchanted : material.default'}],
   'textures':['array.bow_texture_frames[variable.bow_tex_idx]','texture.enchanted']
  }}})
 write_json(bedrock/f'attachables/{name}.json', {
  'format_version':'1.10.0','minecraft:attachable':{'description':{
   'identifier':'voidscape:'+name,
   'materials':{'default':'entity_alphatest','enchanted':'entity_alphatest_glint'},
   'textures':{'default':'textures/items/'+name,'pulling_0':'textures/items/'+name+'_pulling_0',
               'pulling_1':'textures/items/'+name+'_pulling_1','pulling_2':'textures/items/'+name+'_pulling_2',
               'enchanted':'textures/misc/enchanted_item_glint'},
   'geometry':{'default':f'geometry.voidscape.{name}_standby',
               'pulling_0':f'geometry.voidscape.{name}_pulling_0',
               'pulling_1':f'geometry.voidscape.{name}_pulling_1',
               'pulling_2':f'geometry.voidscape.{name}_pulling_2'},
   'animations':{'wield':'animation.bow.wield','wield_first_person_pull':'animation.bow.wield_first_person_pull'},
   'scripts':{
    'pre_animation':[
     'variable.charge_amount = math.clamp((query.main_hand_item_max_duration - (query.main_hand_item_use_duration - query.frame_alpha + 1.0)) / 10.0, 0.0, 1.0);',
     'variable.bow_tex_idx = query.main_hand_item_use_duration == 0.0 ? 0 : variable.charge_amount / 0.5 + 1;'],
    'animate':['wield',{'wield_first_person_pull':'query.main_hand_item_use_duration > 0.0 && context.is_first_person'}]},
   'render_controllers':[controller]}}})

def write_json(path, value):
 path.parent.mkdir(parents=True,exist_ok=True)
 path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf8')

def png(path, pixels, size=32):
 def chunk(kind,data):return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
 raw=b''.join(b'\x00'+bytes(c for pixel in row for c in pixel) for row in pixels)
 path.parent.mkdir(parents=True,exist_ok=True)
 path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',size,size,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b''))

def bedrock_bow_png(source, dest):
 # Bedrock texture_mesh treats a 32px item as a 32-model-unit plane. Vanilla
 # bows are 16px, so retain the Java 32px art but make a dedicated 16px copy.
 data=source.read_bytes();pos=8;compressed=b'';width=height=None
 while pos<len(data):
  length=struct.unpack('>I',data[pos:pos+4])[0];kind=data[pos+4:pos+8];payload=data[pos+8:pos+8+length];pos+=12+length
  if kind==b'IHDR':width,height=struct.unpack('>II',payload[:8])
  elif kind==b'IDAT':compressed+=payload
  elif kind==b'IEND':break
 if width!=32 or height!=32:raise ValueError(f'Expected generated 32x32 bow texture: {source}')
 raw=zlib.decompress(compressed);rows=[];stride=width*4
 for y in range(height):
  row=raw[y*(stride+1):(y+1)*(stride+1)]
  if not row or row[0]!=0:raise ValueError(f'Unsupported PNG filter in {source}')
  rows.append([tuple(row[1+x*4:1+x*4+4]) for x in range(width)])
 small=[]
 for y in range(0,32,2):
  out=[]
  for x in range(0,32,2):
   block=[rows[y+dy][x+dx] for dy in range(2) for dx in range(2)]
   alpha=max(pixel[3] for pixel in block)
   opaque=[pixel for pixel in block if pixel[3]==alpha]
   out.append(tuple(sum(pixel[channel] for pixel in opaque)//len(opaque) for channel in range(4)))
  small.append(out)
 png(dest,small,size=16)

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
 elif name in ('nova_bow','storm_bow'):
  # Vanilla bow transforms expect the arrow to point to the upper left of
  # the texture, with the string behind the grip (towards the lower right).
  # Transform the drawing coordinates before rasterizing to keep crisp lines.
  def bow_line(x1,y1,x2,y2,c,width=1):
   def point(x,y):
    return round(16+0.5657*(y-x)),round(16-0.5657*(x+y-32))
   line(*point(x1,y1),*point(x2,y2),c,width)
  color='v' if name=='nova_bow' else 'g'
  points=[(9,3),(17,5),(23,12),(23,19),(17,26),(9,28)]
  for a,b in zip(points,points[1:]):bow_line(*a,*b,'d',3)
  for a,b in zip(points,points[1:]):bow_line(*a,*b,color,2)
  nock=9-draw*2
  bow_line(10,4,nock,16,'s');bow_line(nock,16,10,29,'s')
  bow_line(23,13,23,18,'c',3);bow_line(23,14,23,17,'w')
  if draw:
   # The arrow follows the string instead of floating at a fixed position.
   tip=34-draw*2
   bow_line(nock,16,tip,16,'g')
   bow_line(tip-4,13,tip,16,'w');bow_line(tip-4,19,tip,16,'w')
 elif name=='rift_blade':
  line(4,28,10,22,'d',4);line(5,28,11,22,'p',2)
  line(6,18,15,27,'d',3);line(6,19,15,28,'v')
  for n in range(17):
   x=11+n;y=20-n;rect(x,y,x+3,y+3,'d');rect(x+1,y,x+2,y+2,'c')
  line(13,19,29,3,'g');rect(11,20,13,22,'w')
 elif name=='eternal_aegis':
  for y in range(3,29):
   width=11 if y<18 else max(1,11-(y-18))
   rect(16-width,y,16+width,y,'d');rect(17-width,y,15+width,y,'p')
  rect(7,5,25,7,'v');line(16,7,16,25,'c',2);line(9,14,23,14,'c',2)
  for d in range(5):rect(16-d,13+abs(d-2),16+d,15+abs(d-2),'g')
  rect(15,13,17,16,'w')
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
    z.writestr(info,f.read_bytes())

def main():
 java=BUILD/'java';bedrock=BUILD/'bedrock';DIST.mkdir(parents=True,exist_ok=True)
 write_json(java/'pack.mcmeta',{'pack':{'description':'Evergarden | Ancient Relics','min_format':[88,0],'max_format':[88,0]}})
 write_json(bedrock/'manifest.json',{'format_version':2,'header':{'name':'Evergarden','description':'Evergarden detailed 64px crops, relics and azure portal','uuid':'df4aee6d-9e8e-4ec8-9df1-7974c6bea203','version':[3,5,1],'min_engine_version':[1,21,0]},'modules':[{'type':'resources','uuid':'ef4aee6d-9e8e-4ec8-9df1-7974c6bea204','version':[3,5,1]}]})
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
   if base=='bow':bedrock_bow_png(equipment_art,dest_bedrock)
   else:dest_bedrock.write_bytes(data)
  else:
   pixels=icon(name)
   if name in MASKS:
    accent={'thorn':(95,201,151,255),'astral':(164,157,245,255),'chrono':(226,186,86,255)}[name.split('_')[0]]
    pixels=[[accent if px in (PALETTE['v'],PALETTE['c']) else px for px in row] for row in pixels]
   png(java/f'assets/voidscape/textures/item/{name}.png',pixels);png(bedrock/f'textures/items/{name}.png',pixels)
  textures['voidscape.'+name]={'textures':'textures/items/'+name}
  parent='handheld' if base in ('netherite_pickaxe','netherite_sword') else 'generated'
  model={'parent':'minecraft:item/'+parent,'textures':{'layer0':'voidscape:item/'+name}}
  if base=='bow':model['parent']='minecraft:item/bow'
  if base=='shield':
   model['display']={
    'thirdperson_righthand':{'rotation':[0,-90,55],'translation':[0,4,0.5],'scale':[0.85,0.85,0.85]},
    'thirdperson_lefthand':{'rotation':[0,90,-55],'translation':[0,4,0.5],'scale':[0.85,0.85,0.85]},
    'firstperson_righthand':{'rotation':[0,-90,25],'translation':[1.13,3.2,1.13],'scale':[0.68,0.68,0.68]},
    'firstperson_lefthand':{'rotation':[0,90,-25],'translation':[1.13,3.2,1.13],'scale':[0.68,0.68,0.68]},
    'gui':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[1,1,1]},
    'fixed':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[1,1,1]},
    'ground':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[0.5,0.5,0.5]}}
   blocking_model={
    'parent':'minecraft:item/'+parent,
    'textures':{'layer0':'voidscape:item/'+name},
    'display':{
     'thirdperson_righthand':{'rotation':[45,-135,0],'translation':[-3.5,11,-2],'scale':[1,1,1]},
     'thirdperson_lefthand':{'rotation':[45,135,0],'translation':[3.5,11,-2],'scale':[1,1,1]},
     'firstperson_righthand':{'rotation':[0,-90,0],'translation':[-2,4,-4],'scale':[1.1,1.1,1.1]},
     'firstperson_lefthand':{'rotation':[0,90,0],'translation':[2,4,-4],'scale':[1.1,1.1,1.1]},
     'gui':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[1,1,1]},
     'fixed':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[1,1,1]},
     'ground':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[0.5,0.5,0.5]}}}
   write_json(java/f'assets/voidscape/models/item/{name}_blocking.json',blocking_model)
  if name in MASKS:
   model=mask_assets(java,bedrock,name)
  write_json(java/f'assets/voidscape/models/item/{name}.json',model)
  definition={'model':{'type':'minecraft:model','model':'voidscape:item/'+name}}
  if base=='shield':
   definition={'model':{'type':'minecraft:condition','property':'minecraft:using_item','on_false':definition['model'],'on_true':{'type':'minecraft:model','model':'voidscape:item/'+name+'_blocking'}}}
  if base=='bow':
   stages=[]
   for n in range(3):
    stage=f'{name}_pulling_{n}'
    stage_art = ROOT / 'art' / 'equipment' / f'{stage}.png'
    if stage_art.is_file():
     data = stage_art.read_bytes()
     dest_java = java / f'assets/voidscape/textures/item/{stage}.png'
     dest_bedrock = bedrock / f'textures/items/{stage}.png'
     dest_java.parent.mkdir(parents=True, exist_ok=True)
     dest_java.write_bytes(data)
     dest_bedrock.parent.mkdir(parents=True, exist_ok=True)
     bedrock_bow_png(stage_art,dest_bedrock)
    else:
     png(java/f'assets/voidscape/textures/item/{stage}.png',icon(name,n+1))
    write_json(java/f'assets/voidscape/models/item/{stage}.json',{'parent':'minecraft:item/bow','textures':{'layer0':'voidscape:item/'+stage}})
    textures['voidscape.'+stage]={'textures':'textures/items/'+stage}
    stages.append({'threshold':[0,0.65,0.9][n],'model':{'type':'minecraft:model','model':'voidscape:item/'+stage}})
   definition={'model':{'type':'minecraft:condition','property':'minecraft:using_item','on_false':definition['model'],'on_true':{'type':'minecraft:range_dispatch','property':'minecraft:use_duration','scale':0.05,'fallback':stages[0]['model'],'entries':stages}}}
   bow_attachable(bedrock, name)
  write_json(java/f'assets/voidscape/items/{name}.json',definition)
  selectors.setdefault(base,[]).append({'when':'voidscape:'+name,'model':definition['model']})
  cat='equipment' if base in ('netherite_pickaxe','netherite_sword','bow','shield','carved_pumpkin') else 'items'
  mappings['items'].setdefault('minecraft:'+base,[]).append({'type':'definition','model':'minecraft:'+base,
    'predicate':{'type':'match','property':'custom_model_data','index':0,'value':'voidscape:'+name},
    'bedrock_identifier':'voidscape:'+name,'display_name':title,
    'bedrock_options':{'icon':'voidscape.'+name,'allow_offhand':True,'display_handheld':base in ('netherite_pickaxe','netherite_sword','bow'),'creative_category':cat}})
  if name=='void_elixir' or name in MASKS:
   # RelicService and GuardianAppearance set a direct item_model. Geyser v2
   # indexes mappings by that component before checking any predicate.
   entry=mappings['items']['minecraft:'+base][-1]
   entry['model']='voidscape:'+name
   del entry['predicate']
 crop_assets.register_crop_assets(java, bedrock, textures, mappings, selectors, write_json, png)
 portal_assets.register(java, bedrock, textures, mappings, selectors, write_json)
 restoration_assets.register_altars(java, bedrock, textures, mappings, selectors, write_json)
 fallback_overrides = {}
 aeternum_assets.register_aeternum_assets(java, bedrock, textures, mappings, selectors, write_json, fallback_overrides)
 for base,cases in selectors.items():
  if base=='carved_pumpkin':
   continue
  fallback=fallback_overrides.get(base, {'type':'minecraft:model','model':'minecraft:item/'+base})
  if base=='bow':
   entries=[{'threshold':t,'model':{'type':'minecraft:model','model':f'minecraft:item/bow_pulling_{n}'}} for n,t in enumerate([0,0.65,0.9])]
   fallback={'type':'minecraft:condition','property':'minecraft:using_item','on_false':fallback,'on_true':{'type':'minecraft:range_dispatch','property':'minecraft:use_duration','scale':0.05,'fallback':entries[0]['model'],'entries':entries}}
  if base=='shield':
   fallback={'type':'minecraft:condition','property':'minecraft:using_item','on_false':{'type':'minecraft:special','base':'minecraft:item/shield','model':{'type':'minecraft:shield'}},'on_true':{'type':'minecraft:special','base':'minecraft:item/shield_blocking','model':{'type':'minecraft:shield'}},'transformation':{'left_rotation':[0.0,0.0,0.0,1.0],'right_rotation':[0.0,0.0,0.0,1.0],'scale':[1.0,-1.0,-1.0],'translation':[0.0,0.0,0.0]}}
  write_json(java/f'assets/minecraft/items/{base}.json',{'model':{'type':'minecraft:select','property':'minecraft:custom_model_data','index':0,'cases':cases,'fallback':fallback}})
 for base,fb in fallback_overrides.items():
  if base not in selectors:
   write_json(java/f'assets/minecraft/items/{base}.json',{'model':fb})
 core_cases=[]
 core_definitions=[]
 core_items={
  'lightning_strike':'Core of Lightning','frost_nova':'Core of Frost',
  'shadow_step':'Core of Shadows','natures_bloom':'Core of Nature',
  'earth_wall':'Core of Earth','dragons_breath':'Core of Dragon',
  'void_pull':'Core of the Void','sonic_boom':'Core of the Warden',
  'blaze_barrage':'Core of the Blaze','wither_ray':'Core of Wither',
  'shulker_levitation':'Core of Levitation','meteor_strike':'Core of Meteor',
  'iron_armor':'Core of Iron','vex_legion':'Core of Evocation','guardian_beam':'Core of the Guardian'
 }
 for c_id,c_title in core_items.items():
  source=ROOT/f'art/cores/core_{c_id}.png'
  if source.is_file():
   data=source.read_bytes()
   for dest in (java/f'assets/advance_magic/textures/item/core_{c_id}.png',bedrock/f'textures/items/core_{c_id}.png'):
    dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(data)
   write_json(java/f'assets/advance_magic/models/item/core_{c_id}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'advance_magic:item/core_{c_id}'}})
   write_json(java/f'assets/advance_magic/items/core_{c_id}.json',{'model':{'type':'minecraft:model','model':f'advance_magic:item/core_{c_id}'}})
   textures[f'advance_magic.core_{c_id}']={'textures':f'textures/items/core_{c_id}'}
   core_cases.append({'when':f'advance_magic:core_{c_id}','model':{'type':'minecraft:model','model':f'advance_magic:item/core_{c_id}'}})
   core_definitions.append({'type':'definition','model':'minecraft:heart_of_the_sea',
                            'predicate':{'type':'match','property':'custom_model_data','index':0,'value':f'advance_magic:core_{c_id}'},
                            'bedrock_identifier':f'advance_magic:core_{c_id}','display_name':c_title,
                            'bedrock_options':{'icon':f'advance_magic.core_{c_id}','allow_offhand':True,'display_handheld':False,'creative_category':'items'}})
 if core_cases:
  write_json(java/'assets/minecraft/items/heart_of_the_sea.json',{'model':{'type':'minecraft:select','property':'minecraft:custom_model_data','index':0,'cases':core_cases,'fallback':{'type':'minecraft:model','model':'minecraft:item/heart_of_the_sea'}}})
  # Advance Magic registers these core identifiers; do not register them twice.
 # Include Advance Magic's missing icons in the pack that already serves crops.
 with zipfile.ZipFile(ROOT.parent/'advance-magic/dist/advance-magic-bedrock.mcpack') as magic:
  magic_atlas=json.loads(magic.read('textures/item_texture.json'))['texture_data']
  for key,entry in magic_atlas.items():
   if key in textures:continue  # Evergarden already has its own core artwork.
   source=entry['textures']+'.png'
   dest=bedrock/source
   dest.parent.mkdir(parents=True,exist_ok=True)
   dest.write_bytes(magic.read(source))
   textures[key]=entry
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
 version=[3,8,2]
 manifest['header']['version']=version
 for module in manifest['modules']:module['version']=version
 write_json(bedrock/'manifest.json',manifest)
 print('Bedrock content version: '+'.'.join(map(str,version)))
 archive(java,DIST/'evergarden-java.zip');archive(bedrock,DIST/'evergarden-bedrock.mcpack')
 hashes={f.name:hashlib.sha1(f.read_bytes()).hexdigest() for f in [DIST/'evergarden-java.zip',DIST/'evergarden-bedrock.mcpack']}
 write_json(DIST/'pack-hashes.json',hashes)
 print(json.dumps(hashes,indent=2))

if __name__=='__main__':main()
