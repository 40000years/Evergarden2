"""Authored neutral starter staff, using the elemental staff native exporters.

The model is solid cuboid geometry, not a picture on a plane. A carved chestnut
branch holds a fixed amethyst cluster above a leather grip and restrained blaze
brass bindings. This file owns only art/wooden-staff and never regenerates the
eight elemental staffs. Review renders are asset renders, not client captures.
"""
from __future__ import annotations

import copy
import io
import itertools
import json
import math
from pathlib import Path
import random
import shutil

from PIL import Image, ImageDraw, ImageFont

import elemental_staff_assets as shared
import legendary_weapon_assets as legacy
import legendary_weapon_preview as renderer

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'advance-magic/art/wooden-staff'
PREVIEWS = ROOT / 'previews/wooden-staff'
NAME = 'wood'
TITLE = 'Amethystwood Staff'
KEY = 'advance_magic:staff_wood'
GRIP = 10.6
FIRST_SCALE = .64
MATERIALS = {
    'wood_dark': '#422819', 'wood': '#895837', 'wood_light': '#c38f59',
    'leather': '#4c3028', 'leather_light': '#99704a',
    'gold_dark': '#78502a', 'gold': '#bd9148', 'gold_light': '#e9c57e',
    'lightning_dark': '#4d356f', 'lightning': '#9562c5',
    'lightning_light': '#c79af1', 'lightning_core': '#ebd2ff',
}


def make_staff():
    """A connected branch staff with a smaller, asymmetric crystal crown."""
    w = shared.Staff(NAME)
    # The straight centre is deliberately wide and unobstructed at hand contact.
    w.box(7.48, 1.0, 7.48, 8.52, 19.5, 8.52, 'wood', 'staff')
    w.box(7.50, 1.25, 7.35, 7.71, 19.1, 8.65, 'wood_light', 'staff')
    w.box(8.25, 1.25, 7.39, 8.53, 19.1, 8.61, 'wood_dark', 'staff')
    # Slight taper and a carved branch crook rather than a machined straight pole.
    w.beam([(8, 17.2), (7.8, 20.6), (8.3, 22.6)], 1.34, 7.30, 8.70, 'wood_dark', 'crown')
    w.beam([(8, 17.4), (7.9, 20.5), (8.35, 22.6)], 1.0, 7.13, 8.87, 'wood', 'crown')
    w.beam([(7.63, 17.5), (7.54, 20.3), (7.95, 22.4)], .23, 6.98, 9.02, 'wood_light', 'crown')
    # Long left hook curls around the cluster; the shorter right fork supports it.
    left = [(8.2, 21), (6.55, 22.2), (5.35, 24), (5.4, 26.15), (6.1, 27.5), (7.05, 27.9)]
    right = [(8.1, 21.7), (9.8, 22.5), (10.8, 24.0), (10.95, 25.6), (10.35, 26.6)]
    w.beam(left, 1.25, 6.85, 9.15, 'wood_dark', 'crown')
    w.beam(left, .87, 6.63, 9.37, 'wood', 'crown')
    w.beam([(6.6, 22.6), (5.75, 24.1), (5.8, 26.05), (6.4, 27.1)], .25,
           6.47, 9.53, 'wood_light', 'crown')
    w.beam(right, 1.05, 6.96, 9.04, 'wood_dark', 'crown')
    w.beam(right, .70, 6.74, 9.26, 'wood', 'crown')
    w.beam([(9.75, 22.8), (10.35, 24.1), (10.5, 25.6)], .20,
           6.59, 9.41, 'wood_light', 'crown')
    # A solid wooden socket physically joins every crystal to the branch.
    w.poly([(6.4, 22.2), (6.8, 23.65), (9.8, 24.0), (10.35, 22.8), (8.3, 21.6)],
           6.70, 9.30, 'wood_dark', 'crown')
    w.beam([(6.6, 22.55), (7.8, 23.0), (9.5, 23.1), (10.1, 22.85)], .37,
           6.48, 9.52, 'wood', 'crown')

    def shard(cx, bottom, shoulder, top, width, depth, offset=0):
        # Six-sided outline, a broad violet facet and a narrow pale ridge. All
        # facets remain fixed to the socket, including during native idle poses.
        first_cube = len(w.cubes)
        l, r = cx-width/2, cx+width/2
        outline = [(l, bottom+.3), (l, shoulder), (cx+offset, top),
                   (r, shoulder-.12), (r, bottom+.3), (cx, bottom)]
        z0, z1 = 8-depth/2, 8+depth/2
        w.poly(outline, z0, z1, 'lightning_dark', 'crystal')
        w.poly([(l+.20, bottom+.4), (l+.20, shoulder), (cx+offset, top-.25),
                (cx+.13, bottom+.14)], z0-.12, z1+.12, 'lightning', 'crystal')
        w.poly([(cx+.13, bottom+.14), (cx+offset, top-.25),
                (r-.17, shoulder-.2), (r-.17, bottom+.43)],
               z0-.13, z1+.13, 'lightning_light', 'crystal')
        w.poly([(cx+offset-.13, shoulder), (cx+offset, top-.25),
                (cx+offset+.17, shoulder-.23), (cx+.22, bottom+.65)],
               z0-.18, z1+.18, 'lightning_core', 'crystal')
        # Taper depth as well as the front outline: the 90-degree view remains a
        # pointed 3D crystal, rather than the rectangular edge of an extrusion.
        pieces=w.cubes[first_cube:]
        del w.cubes[first_cube:]
        for cube in pieces:
            x0,y0,z0=cube['from'];x1,y1,z1=cube['to']
            cuts=sorted({y0,y1,*[v*.25 for v in range(math.ceil(max(y0,shoulder)*4),math.ceil(y1*4)) if y0 < v*.25 < y1]})
            for lower,upper in zip(cuts,cuts[1:]):
                taper=max(.08,min(1,(top-lower)/(top-shoulder)))
                w.box(x0,lower,8+(z0-8)*taper,x1,upper,8+(z1-8)*taper,cube['mat'],cube['bone'])

    # Rear crystal first; the large front shard and two smaller shoulders read as
    # actual amethyst, without the halo/levitating parts of a higher tier staff.
    shard(7.02, 22.6, 25.1, 27.35, 1.35, 1.65, -.18)
    shard(9.75, 22.8, 24.4, 26.55, 1.25, 1.7, .15)
    shard(8.38, 22.85, 27.35, 29.45, 2.05, 2.45, .25)
    # Two binding wraps overlap the branch and socket; warm brass echoes Blaze Rod.
    for y, width, height in ((1.0, 1.34, .58), (5.0, 1.25, .3),
                             (GRIP-2.1, 1.45, .38), (GRIP+1.85, 1.45, .38),
                             (18.5, 1.60, .65), (20.05, 1.55, .30)):
        w.box(8-width/2, y, 8-width/2, 8+width/2, y+height, 8+width/2, 'gold_dark', 'staff')
        w.box(8-width/2+.08, y+.12, 8-width/2-.09, 8+width/2-.08,
              y+height-.05, 8+width/2+.09, 'gold', 'staff')
        w.box(8-width/2+.16, y+.12, 8-width/2-.14, 8-width/2+.32,
              y+height-.06, 8+width/2+.14, 'gold_light', 'staff')
    # Ferrule and leather contact. Every wrap touches the wooden centre.
    w.box(7.62, .5, 7.62, 8.38, 1.05, 8.38, 'gold_dark', 'staff')
    w.box(7.35, GRIP-1.75, 7.35, 8.65, GRIP+1.85, 8.65, 'leather', 'staff')
    for y in [GRIP-1.65+i*.55 for i in range(7)]:
        w.box(7.29, y, 7.28, 8.71, y+.16, 8.72, 'leather_light', 'staff')
    # Small carved knots are solid inlays, with no leaf/green elemental cues.
    for y in (3.5, 6.2, 15.6):
        w.box(7.80, y, 7.26, 8.22, y+.72, 7.51, 'wood_dark', 'staff')
        w.box(7.94, y+.14, 7.21, 8.08, y+.54, 7.52, 'wood_light', 'staff')
    return w


def atlas(frame=0):
    image = shared.atlas(0)
    draw = ImageDraw.Draw(image)
    rng = random.Random(60341)
    for name, code in MATERIALS.items():
        index = shared.MATS.index(name)
        ox, oy = index % 8*16, index//8*16
        rgb = tuple(bytes.fromhex(code[1:]))
        for y in range(16):
            for x in range(16):
                shade = (1.13 if x < 3 else 1.02 if x < 11 else .84)
                shade += rng.choice((-.03, 0, 0, .03))
                if name.startswith('wood'):
                    # Long irregular grain and occasional knots remain readable
                    # on all six faces, including the otherwise untextured sides.
                    grain = (x + int(math.sin(y*.45)*1.3)) % 7
                    shade *= .76 if grain == 0 else 1.10 if grain == 1 else 1
                if name.startswith('leather') and y in (2, 10):
                    shade *= .79
                if name in shared.EMISSIVE:
                    shade *= .94+.06*math.sin(frame*math.tau/8)
                draw.point((ox+x, oy+y), tuple(min(255, round(c*shade)) for c in rgb)+(255,))
    return image


def camera_fit(w):
    """Fit complete solids to the same calibrated camera used by elemental8."""
    q = []
    for cube in w.cubes:
        for p in itertools.product(*[(cube['from'][i], cube['to'][i]) for i in range(3)]):
            q.append(-3*(p[0]-8)-18.42*(p[1]-GRIP)+1.5*(p[2]-8))
    ratio = 400/(max(q)-min(q))
    lift = (197+23.75*18.42+ratio*min(q)-50)/(18.42/.48)
    return round(.48*ratio, 6), round(lift, 6)


def configure_helpers(w):
    # A new key extends the in-memory exporter profiles; all eight original
    # profiles, sources and generated asset files retain their authored values.
    scale, lift = camera_fit(w)
    shared.GRIPS[NAME] = GRIP
    shared.FIRST_SCALES[NAME] = FIRST_SCALE
    shared.BEDROCK_SCALES[NAME] = scale
    shared.BEDROCK_LIFTS[NAME] = lift
    return scale, lift


def java_model(w):
    model = shared.java_model(w)
    model['elements'] = model['elements'][:-3]  # Body only; no VFX picture sheets.
    model['credit'] = 'Evergarden / wooden_staff_assets.py'
    model['textures'] = {'body': 'advance_magic:item/wooden_staff/materials',
                         'particle': 'advance_magic:item/wooden_staff/materials'}
    return model


def preview_model(w=None):
    model = copy.deepcopy(w or make_staff())
    texture = atlas()
    for cube in model.cubes:
        cube['preview_uv'] = [v*8 for v in shared.tile(cube['mat'])]
        cube['preview_texture'] = texture
        cube['mat'] = 'white' if cube['mat'] in shared.EMISSIVE else 'gold'
    return model


def _font(size, bold=False):
    return ImageFont.truetype('C:/Windows/Fonts/'+('segoeuib.ttf' if bold else 'segoeui.ttf'), size)


def make_previews(w, icon, scale, lift):
    PREVIEWS.mkdir(parents=True, exist_ok=True)
    board = Image.new('RGBA', (1920, 1120), '#10121b')
    draw = ImageDraw.Draw(board)
    draw.text((50, 28), 'EVERGARDEN / APPRENTICE WORKSHOP', font=_font(19), fill='#a899b8')
    draw.text((46, 61), 'CARVED WOOD + AMETHYST', font=_font(43, True), fill='#efd4a6')
    draw.text((50, 125), 'Neutral starter staff / native solid 3D / Java + Bedrock', font=_font(22), fill='#a99abf')
    for index, (yaw, title) in enumerate(((0, 'FRONT'), (32, 'THREE QUARTER'), (90, 'SIDE'), (170, 'BACK'))):
        x = 30+index*470
        draw.rounded_rectangle((x, 184, x+450, 892), radius=12, fill='#171c29', outline='#4d3f56', width=2)
        image = renderer.render_item(preview_model(w), atlas(), size=660, yaw=yaw, elevation=5, tilt=-4)
        board.alpha_composite(image, (x-105, 200))
        draw.text((x+26, 842), title, font=_font(23, True), fill='#bf9ce2')
    draw.text((50, 943), 'WOOD GRAIN / LEATHER HAND CONTACT / BRASS WRAPS / FIXED CRYSTAL CLUSTER',
              font=_font(23), fill='#cfbea4')
    draw.text((50, 993), 'Asset geometry render. Native client lighting and arm movement still require an in-game check.',
              font=_font(19), fill='#8793a8')
    board.convert('RGB').save(PREVIEWS/'wooden-staff-multiangle.png')
    # Keep one transparent full-size image available to review in a viewer.
    renderer.render_item(preview_model(w), atlas(), size=960, yaw=30, elevation=8, tilt=-7).save(PREVIEWS/'wooden-staff.png')

    # Screenshot-calibrated affine frame, rendered from the actual cuboids. The
    # hand contact marker is explicitly a calibration marker, never a screenshot.
    frame = Image.new('RGB', (1160, 580), '#202d3c')
    d = ImageDraw.Draw(frame)
    for y in range(500):
        col = (42-int(y*.025), 63-int(y*.035), 79-int(y*.035))
        d.line((0, y, 1160, y), fill=col)
    d.line((580, 230, 580, 270), fill='#7c8794');d.line((560, 250, 600, 250), fill='#7c8794')
    ratio = scale/.48
    projection = ((13, -3), (-11.68, -18.42), (2.5, 1.5))

    def project(p):
        p = [ratio*(p[0]-8), -23.75+ratio*(p[1]-GRIP)+lift/.48, ratio*(p[2]-8)]
        return (813+sum(p[i]*projection[i][0] for i in range(3)),
                197+sum(p[i]*projection[i][1] for i in range(3)))

    faces=[]
    for cube in w.cubes:
        x0,y0,z0 = cube['from'];x1,y1,z1 = cube['to']
        rgb = tuple(bytes.fromhex(MATERIALS[cube['mat']][1:]))
        for points, shade in (([(x0,y0,z0),(x1,y0,z0),(x1,y1,z0),(x0,y1,z0)],1),
                              ([(x1,y0,z0),(x1,y0,z1),(x1,y1,z1),(x1,y1,z0)],.72),
                              ([(x0,y1,z0),(x1,y1,z0),(x1,y1,z1),(x0,y1,z1)],1.14)):
            # Surface sort agrees with the approximate affine camera direction.
            depth = sum(p[0]+p[2]*2-p[1]*.2 for p in points)/4
            faces.append((depth, [project(p) for p in points], tuple(min(255, round(c*shade)) for c in rgb)))
    for _,points,color in sorted(faces, key=lambda row:row[0]):d.polygon(points, fill=color)
    contact=project([8,GRIP,8]);cx,cy=contact
    d.ellipse((cx-7,cy-7,cx+7,cy+7), outline='#fff0c1',width=2)
    d.text((45, 30), 'APPROXIMATE BEDROCK FIRST PERSON FRAME',font=_font(21,True),fill='#ecdfc8')
    d.text((45, 65), 'Complete crown and lower shaft fit the existing camera calibration.',font=_font(17),fill='#bfccd8')
    d.text((45, 512), 'Asset projection, not a game screenshot. Ring marks physical leather hand contact.',font=_font(20),fill='#c8b7d7')
    frame.save(PREVIEWS/'wooden-staff-held-frame.png')
    slot=Image.new('RGBA',(420,210),'#10121b');sd=ImageDraw.Draw(slot)
    sd.rectangle((28,35,160,167),fill='#2c2c3c',outline='#8f91a1',width=2)
    slot.alpha_composite(icon,(30,37))
    small=icon.resize((16,16),Image.Resampling.LANCZOS).resize((80,80),Image.Resampling.NEAREST)
    slot.alpha_composite(small,(255,60))
    sd.text((26,180),'128 px source',font=_font(16),fill='#d0bddc')
    sd.text((225,180),'16 px HUD x5',font=_font(16),fill='#d0bddc')
    slot.convert('RGB').save(PREVIEWS/'wooden-staff-hotbar.png')


def generate(previews=True):
    ART.mkdir(parents=True, exist_ok=True)
    w = make_staff()
    scale, lift = configure_helpers(w)
    java = ART/'java'
    bedrock = ART/'bedrock'
    jp = java/'assets/advance_magic/textures/item/wooden_staff'
    bp = bedrock/'textures/wooden_staff'
    jp.mkdir(parents=True, exist_ok=True);bp.mkdir(parents=True, exist_ok=True)
    texture = atlas()
    stream = io.BytesIO();texture.save(stream, format='PNG');data = stream.getvalue()
    texture.save(ART/'materials.png');texture.save(bp/'materials.png')
    strip=Image.new('RGBA',(128,1024))
    for frame in range(8):strip.paste(atlas(frame),(0,frame*128))
    strip.save(jp/'materials.png')
    shared.write_json(jp/'materials.png.mcmeta',{'animation':{'frametime':4,'interpolate':False,'width':128,'height':128}})
    shared.write_json(ART/'wood.geometry.json',{'id':NAME,'grip':[8,GRIP,8],'cubes':w.cubes})
    source=shared.bbmodel(w,data)
    source['name']='wooden_staff'
    source['textures'][0]['name']='wooden_staff_materials.png'
    # All pieces are physically seated. Native material pulse supplies gentle
    # amethyst shimmer without moving a crystal out of its wooden cradle.
    source['animations'][0]['animators']={}
    shared.write_json(ART/'wooden_staff.bbmodel',source)
    shared.write_json(ART/'blockbench/wooden_staff.bbmodel',source)
    shared.write_json(java/'assets/advance_magic/models/item/wooden_staff/wood.json',java_model(w))
    icon=renderer.render_icon_frames(preview_model(w),texture,[None])[0]
    icon.save(ART/'wood_icon.png');icon.save(jp/'wood_icon.png');icon.save(bp/'wood_icon.png')
    shared.write_json(java/'assets/advance_magic/models/item/wooden_staff/wood_icon.json',{
        'parent':'minecraft:item/generated','textures':{'layer0':'advance_magic:item/wooden_staff/wood_icon'}})
    shared.write_json(java/'assets/advance_magic/items/staff_wood.json',{'model':{
        'type':'minecraft:select','property':'minecraft:display_context',
        'cases':[{'when':'gui','model':{'type':'minecraft:model','model':'advance_magic:item/wooden_staff/wood_icon'}}],
        'fallback':{'type':'minecraft:model','model':'advance_magic:item/wooden_staff/wood'}}})
    shared.write_json(bedrock/'models/entity/wooden_staff/wood.geo.json',shared.bedrock_geometry(w))
    prefix='animation.advance_magic.staff_wood'
    shared.write_json(bedrock/'animations/wooden_staff.animation.json',{'format_version':'1.8.0','animations':{
        prefix+'.first':shared.bedrock_pose(True,NAME),prefix+'.third':shared.bedrock_pose(False,NAME)}})
    controller='controller.render.advance_magic.wooden_staff'
    shared.write_json(bedrock/'render_controllers/wooden_staff.render.json',{
        'format_version':'1.8.0','render_controllers':{controller:{
            'geometry':'Geometry.default','materials':[{'*':'Material.default'},{'crystal_glow':'Material.glow'}],
            'textures':['Texture.default']}}})
    shared.write_json(bedrock/'attachables/wooden_staff/wood.json',{'format_version':'1.10.0','minecraft:attachable':{'description':{
        'identifier':KEY,'materials':{'default':'entity_alphatest','glow':'entity_emissive_alpha'},
        'textures':{'default':'textures/wooden_staff/materials'},'geometry':{'default':'geometry.advance_magic.staff_wood'},
        'animations':{'first':prefix+'.first','third':prefix+'.third'},
        'scripts':{'animate':[{'first':'context.is_first_person == 1.0'},{'third':'context.is_first_person == 0.0'}]},
        'render_controllers':[controller]}}})
    icon_key='advance_magic.staff_wood'
    definition={'type':'definition','model':KEY,'bedrock_identifier':KEY,'display_name':TITLE,
        'bedrock_options':{'icon':icon_key,'allow_offhand':True,'display_handheld':True,'creative_category':'equipment'}}
    metadata={'format':1,'generator':'tools/wooden_staff_assets.py','base_item':'minecraft:carrot_on_a_stick',
        'modelengine_version':'4.1.1',
        'items':[{'id':NAME,'title':TITLE,'model_key':KEY,'modelengine':'wooden_staff.bbmodel','grip':[8,GRIP,8],
                  'cuboids':len(w.cubes),'element':None,'slots':{'initial':1,'upgraded':2,'maximum_rarity':'rare'},
                  'description':'Chestnut wood grain, carved branch cradle, three fixed violet amethyst shards, leather and restrained blaze brass.'}],
        'item_atlas':{icon_key:{'textures':'textures/wooden_staff/wood_icon'}},
        'definitions':{'minecraft:carrot_on_a_stick':[definition]},
        'grip_frame':{'bedrock_root':shared.BEDROCK_PIVOT,'bedrock_contact':shared.BEDROCK_GRIP,
                      'first_scale':scale,'first_lift':lift,'third_scale':.9},
        'camera_fit':{'method':'existing screenshot-calibrated affine projection, approximate; not a client render',
                      'frame':[1160,500],'solid_y_bounds':[50,450]},
        'vfx':{'type':'solid crystal facets and slow Java material shimmer','picture_planes':0,'server_entities':0}}
    shared.write_json(ART/'metadata.json',metadata)
    if previews:make_previews(w,icon,scale,lift)
    return metadata


def register(java,bedrock,item_atlas,definitions):
    """Append only the neutral staff; retain every existing Geyser entry."""
    if not (ART/'metadata.json').is_file():generate(previews=False)
    metadata=json.loads((ART/'metadata.json').read_text(encoding='utf8'))
    for source,destination in ((ART/'java',java),(ART/'bedrock',bedrock)):
        for path in source.rglob('*'):
            if path.is_file():
                target=destination/path.relative_to(source)
                target.parent.mkdir(parents=True,exist_ok=True)
                shutil.copyfile(path,target)
    item_atlas.update(metadata['item_atlas'])
    for base,items in metadata['definitions'].items():
        entries=definitions.setdefault(base,[])
        for entry in items:
            entries[:]=[old for old in entries if old.get('model')!=entry['model']]
            entries.append(entry)


if __name__=='__main__':
    metadata=generate()
    print('Generated neutral wooden staff;',metadata['items'][0]['cuboids'],'solid cuboids')
    print(ART)
    print(PREVIEWS/'wooden-staff-multiangle.png')
