"""Build the original Wrath cube models, pixel textures and deterministic Java pack.

No downloaded art or dependencies. Geometry is also used by the SVG preview.
"""
import hashlib
import json
import math
from pathlib import Path
import struct
import zipfile
import zlib

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / 'art/java'
PALETTE = {
    'armor': (30, 33, 44), 'edge': (78, 83, 98), 'bone': (192, 176, 151),
    'coal': (12, 11, 19), 'ember': (237, 47, 31), 'gold': (165, 96, 51),
    'hot': (255, 179, 63), 'cloth': (73, 16, 29),
}
PARTS = {}


def box(part, a, b, material, rotation=None):
    cube = {'from': list(a), 'to': list(b), 'material': material}
    if rotation:
        cube['rotation'] = rotation
    PARTS.setdefault(part, []).append(cube)


def geometry():
    # Coordinates use the item display center at (8,8,8); 16 pixels = one block.
    box('body', (-2, 1, 3), (18, 17, 13), 'armor')
    box('body', (-4, 13, 1), (20, 20, 15), 'edge')
    box('body', (1, -1, 4), (15, 3, 13), 'coal')
    box('body', (-3, -5, 4), (19, 1, 14), 'armor')
    box('body', (-2, -5, 2), (4, 5, 5), 'cloth')
    box('body', (12, -5, 2), (18, 5, 5), 'cloth')
    # Six ivory ribs frame the recessed, burning heart.
    for y, width in [(5, 7), (9, 8), (13, 9)]:
        box('body', (8-width, y, 0), (6, y+2, 3), 'bone')
        box('body', (10, y, 0), (8+width, y+2, 3), 'bone')
    box('body', (7, 1, 0), (9, 5, 3), 'bone')
    for x in (-1, 16):
        for y in (4, 8, 12):
            box('body', (x, y, 12), (x+2, y+2, 15), 'gold')
    # A scorched faceplate, red slit eyes and hanging teeth.
    box('head', (2, 2, 3), (14, 14, 13), 'coal')
    box('head', (1, 8, 1), (15, 14, 5), 'armor')
    box('head', (2, 6, 1), (6, 8, 2), 'ember')
    box('head', (10, 6, 1), (14, 8, 2), 'ember')
    box('head', (7, 4, 0), (9, 10, 3), 'edge')
    box('head', (2, 1, 1), (14, 4, 4), 'bone')
    for x in (3, 6, 9, 12):
        box('head', (x, -1, 1), (x+1, 2, 3), 'bone')
    for side in (-1, 1):
        x = 8 + side * 8
        box('head', (x-2, 10, 6), (x+2, 18, 10), 'bone',
            {'origin': [x, 10, 8], 'axis': 'z', 'angle': -side*22.5})
        x = 8 + side * 11
        box('head', (x-1.5, 16, 6.5), (x+1.5, 24, 9.5), 'coal',
            {'origin': [x, 16, 8], 'axis': 'z', 'angle': side*22.5})
    # Uneven spiked pauldrons and long plated forearms, each pivoted at shoulder.
    for part, side in [('left_arm', 1), ('right_arm', -1)]:
        box(part, (2, -8, 4), (14, 13, 13), 'armor')
        box(part, (0, 9, 2), (16, 16, 15), 'edge')
        box(part, (3, -12, 3), (13, -5, 14), 'coal')
        for x in (2, 10):
            box(part, (x, 15, 5), (x+3, 23 if side == -1 else 20, 9), 'bone',
                {'origin': [x+1.5, 15, 7], 'axis': 'z', 'angle': -side*22.5})
        box(part, (4, -3, 2), (12, 1, 4), 'ember')
        for y in (-7, 2, 6):
            box(part, (1, y, 3), (15, y+1, 5), 'gold')
    for part in ('left_leg', 'right_leg'):
        box(part, (3, -3, 4), (13, 16, 13), 'armor')
        box(part, (1, -6, -1), (15, 0, 14), 'coal')
        box(part, (2, 5, 1), (14, 10, 5), 'edge')
        box(part, (6, 0, 2), (10, 5, 4), 'gold')
        box(part, (5, 8, 0), (11, 10, 1), 'ember')
    # War hammer: grip at (8,8,8), long haft and a broad striking head above it.
    # Keep the legacy 'cleaver' item ID so existing model mappings remain valid.
    box('cleaver', (6.5, -12, 6.5), (9.5, 26, 9.5), 'coal')
    for y in (-8, -3, 2, 7, 12, 17):
        box('cleaver', (6, y, 6), (10, y+1.5, 10), 'gold')
    box('cleaver', (-5, 23, 1), (21, 32, 15), 'armor')
    box('cleaver', (-8, 22, 0), (-4, 32, 16), 'edge')
    box('cleaver', (20, 22, 0), (24, 32, 16), 'edge')
    box('cleaver', (-8, 23, -1), (-4, 31, 1), 'ember')
    box('cleaver', (20, 23, -1), (24, 31, 1), 'ember')
    box('cleaver', (5, 24, -1), (11, 30, 1), 'hot')
    box('cleaver', (5, -14, 5), (11, -11, 11), 'bone')
    # The exposed red heart is a full-bright item bone.
    box('core', (4, 4, 5), (12, 12, 11), 'coal')
    box('core', (5, 5, 3), (11, 11, 6), 'ember',
        {'origin': [8, 8, 4.5], 'axis': 'z', 'angle': 45})
    box('core', (6.5, 6.5, 2), (9.5, 9.5, 4), 'hot')
    # Floating, broken iron halo; independent rotation makes it feel possessed.
    for x, z in [(0, 4), (12, 4), (4, 0), (4, 12)]:
        box('crown', (x, 7, z), (x+4, 9, z+4), 'gold')
        box('crown', (x+1, 9, z+1), (x+3, 14, z+3), 'ember')



def png(path, pixels):
    h, w = len(pixels), len(pixels[0])
    def chunk(tag, data):
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag+data)&0xffffffff)
    raw = b''.join(b'\0'+bytes(c for p in row for c in p) for row in pixels)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR', struct.pack('>IIBBBBB', w,h,8,6,0,0,0))
                     +chunk(b'IDAT', zlib.compress(raw,9))+chunk(b'IEND',b''))


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2)+'\n', encoding='utf8')


SIN_PALETTES = {
    'pride': {'armor':(62,49,91),'edge':(171,133,57),'bone':(243,217,145),'coal':(24,18,40),'ember':(252,202,60),'gold':(249,233,146),'hot':(255,248,202),'cloth':(83,38,100)},
    'greed': {'armor':(45,74,62),'edge':(160,117,35),'bone':(218,198,113),'coal':(16,35,30),'ember':(227,174,48),'gold':(251,210,77),'hot':(255,246,147),'cloth':(41,103,73)},
    'lust': {'armor':(70,27,61),'edge':(151,57,100),'bone':(234,182,203),'coal':(30,13,38),'ember':(223,43,99),'gold':(211,130,163),'hot':(255,150,201),'cloth':(111,25,70)},
    'envy': {'armor':(26,59,68),'edge':(60,146,121),'bone':(169,225,203),'coal':(10,28,37),'ember':(58,222,156),'gold':(102,198,174),'hot':(182,255,219),'cloth':(27,86,79)},
    'gluttony': {'armor':(55,66,37),'edge':(112,140,59),'bone':(217,219,148),'coal':(22,32,25),'ember':(130,209,67),'gold':(180,151,65),'hot':(218,255,112),'cloth':(72,83,38)},
    'sloth': {'armor':(57,48,76),'edge':(121,99,150),'bone':(192,177,221),'coal':(24,22,38),'ember':(143,99,207),'gold':(160,131,189),'hot':(218,183,255),'cloth':(66,53,88)},
}


def sin_geometry(sin):
    """Nine bones with sin-specific silhouettes; dimensions match the animated pivots."""
    parts = {}
    def add(part, a, b, material, rotation=None):
        cube = {'from':list(a), 'to':list(b), 'material':material}
        if rotation: cube['rotation'] = rotation
        parts.setdefault(part, []).append(cube)
    wide = sin in ('greed','gluttony','sloth')
    shoulder = -6 if wide else -3
    add('body',(shoulder,0,3),(16-shoulder,17,14),'armor')
    add('body',(shoulder-2,13,2),(18-shoulder,19,15),'edge')
    add('body',(0,-4,4),(16,2,14),'cloth')
    add('body',(5,4,0),(11,12,4),'coal')
    for y in (4,8,12):
        add('body',(shoulder,y,1),(3,y+1.5,4),'bone')
        add('body',(13,y,1),(16-shoulder,y+1.5,4),'bone')
    add('head',(1,1,2),(15,15,14),'coal')
    add('head',(0,7,1),(16,15,6),'armor')
    add('head',(2,6,0),(6,9,2),'ember')
    add('head',(10,6,0),(14,9,2),'ember')
    add('head',(3,1,0),(13,5,4),'bone')
    for part in ('left_arm','right_arm'):
        add(part,(2,-10,3),(14,13,14),'armor')
        add(part,(0,10,1),(16,17,15),'edge')
        add(part,(4,-13,4),(12,-9,13),'coal')
        add(part,(2,-4,1),(14,-1,4),'ember')
    for part in ('left_leg','right_leg'):
        add(part,(3,-4,3),(13,16,13),'armor')
        add(part,(1,-7,-1),(15,0,15),'coal')
        add(part,(3,5,0),(13,9,4),'edge')
    add('core',(4,4,4),(12,12,11),'coal')
    add('core',(6,5,1),(10,11,5),'ember')
    add('core',(7,7,0),(9,9,2),'hot')
    add('cleaver',(6,-13,6),(10,25,10),'coal')
    add('cleaver',(5,-13,5),(11,-10,11),'bone')
    if sin == 'pride':
        # Tall seven-point crown and a royal scepter/halberd.
        for x, height in ((-4,19),(0,24),(4,20),(7,28),(10,20),(14,24),(18,19)):
            add('crown',(x,7,5),(x+2,height,10),'gold')
        add('crown',(-5,6,4),(20,10,12),'edge')
        add('body',(-6,-5,11),(22,5,15),'cloth')
        add('cleaver',(-2,20,4),(18,29,12),'gold')
        add('cleaver',(5,25,2),(11,31,14),'hot')
    elif sin == 'greed':
        # Coin vault chest, four clawed crown prongs and hooked chain mace.
        for x in (-5,15):
            add('body',(x,1,0),(x+6,18,6),'gold')
            add('head',(x,5,0),(x+5,11,5),'gold')
        for x in (-5,2,11,18): add('crown',(x,5,4),(x+4,18,12),'gold')
        for y in (0,5,10,15,20): add('cleaver',(4,y,4),(12,y+2,12),'gold')
        add('cleaver',(-4,22,0),(20,31,16),'edge')
    elif sin == 'lust':
        # Narrow, blade-like diadem, winged shoulders and a thorn spear.
        add('crown',(-1,5,5),(2,29,9),'ember')
        add('crown',(14,5,5),(17,29,9),'ember')
        add('crown',(3,9,3),(13,14,11),'gold')
        for part in ('left_arm','right_arm'):
            add(part,(-3,8,10),(19,16,14),'cloth')
        add('cleaver',(4,22,5),(12,32,11),'ember')
        add('cleaver',(1,18,6),(15,21,10),'gold')
    elif sin == 'envy':
        # Asymmetric mirror horns and double-ended jagged blade.
        add('crown',(-5,7,5),(1,27,11),'hot')
        add('crown',(14,7,5),(19,20,11),'edge')
        add('head',(-3,4,8),(3,15,14),'edge')
        add('head',(13,1,7),(19,13,14),'hot')
        add('cleaver',(3,19,4),(13,32,12),'hot')
        add('cleaver',(3,-16,4),(13,-9,12),'ember')
    elif sin == 'gluttony':
        # Hinged-looking layered mouth, tusks and a forked eating implement.
        add('head',(-5,-4,-2),(21,4,8),'bone')
        add('head',(-4,4,-3),(20,8,4),'coal')
        for x in (-3,2,7,12,17): add('head',(x,1,-4),(x+2,8,0),'bone')
        add('body',(-8,-4,1),(24,9,13),'cloth')
        for x in (-3,5,13,20): add('crown',(x,4,5),(x+3,18,10),'bone')
        add('cleaver',(2,19,4),(5,32,10),'bone')
        add('cleaver',(11,19,4),(14,32,10),'bone')
    else:
        # Sloth: fallen mantle, one shoulder hanging low, burial scythe.
        add('crown',(-5,4,5),(20,9,13),'bone')
        add('crown',(13,8,5),(18,20,11),'ember')
        add('left_arm',(-5,-12,9),(17,8,15),'cloth')
        add('body',(-8,-9,11),(21,3,15),'cloth')
        add('cleaver',(-8,21,4),(21,27,11),'bone')
        add('cleaver',(-9,16,5),(-5,28,10),'hot')
    sword = [
        {'from':[6,0,6],'to':[10,9,10],'material':'coal'},
        {'from':[2,8,5],'to':[14,11,11],'material':'gold'},
        {'from':[5,11,6],'to':[11,27,10],'material':'armor'},
        {'from':[7,27,7],'to':[9,32,9],'material':'hot'},
    ]
    parts['ground_sword'] = sword
    for bone in ('body','head','cleaver','core'):
        parts[bone+'_unbound'] = [{**cube, 'material': 'hot' if cube['material']=='ember' else cube['material']} for cube in parts[bone]]
    return parts


def make_other_sins():
    for sin, palette in SIN_PALETTES.items():
        for material,color in palette.items():
            pixels=[]
            for y in range(16):
                pixels.append([tuple(max(0,min(255,c+((x*17+y*31+x*y*3)%17)-8)) for c in color)+(255,) for x in range(16)])
            png(PACK/f'assets/sevensins/textures/item/{sin}/{material}.png',pixels)
        for name,cubes in sin_geometry(sin).items():
            elements=[]
            for cube in cubes:
                element={k:v for k,v in cube.items() if k!='material'}
                element['faces']={face:{'uv':[0,0,16,16],'texture':'#'+cube['material']} for face in ('north','south','east','west','up','down')}
                elements.append(element)
            write_json(PACK/f'assets/sevensins/models/{sin}/{name}.json',{
                'textures':{m:f'sevensins:item/{sin}/{m}' for m in palette},
                'elements':elements,'gui_light':'front',
                'display':{'fixed':{'rotation':[0,180,0],'translation':[0,0,0],'scale':[1,1,1]}}})
            write_json(PACK/f'assets/sevensins/items/{sin}/{name}.json',{
                'model':{'type':'minecraft:model','model':f'sevensins:{sin}/{name}'}})


def make_pack():
    write_json(PACK/'pack.mcmeta', {'pack': {'description': '7sins | The Seven Deadly Sins',
                                          'min_format': [88, 0], 'max_format': [88, 0]}})
    make_other_sins()
    for material, color in PALETTE.items():
        pixels = []
        for y in range(16):
            row = []
            for x in range(16):
                noise = ((x*17+y*31+x*y*3)%17)-8
                highlight = 18 if (x+y)%11 == 0 else 0
                row.append(tuple(max(0, min(255, c+noise+highlight)) for c in color)+(255,))
            pixels.append(row)
        png(PACK/f'assets/sevensins/textures/item/wrath/{material}.png', pixels)
    models = dict(PARTS)
    # A narrow executioner's sword with an upward point; separate from the boss bones.
    models['ground_sword'] = [
        {'from':[6,0,6], 'to':[10,8,10], 'material':'coal'},
        {'from':[1,7,5], 'to':[15,10,11], 'material':'gold'},
        {'from':[5,10,6], 'to':[11,25,10], 'material':'armor'},
        {'from':[4,10,6], 'to':[5,25,10], 'material':'ember'},
        {'from':[11,10,6], 'to':[12,25,10], 'material':'ember'},
        {'from':[6,25,6.5], 'to':[10,29,9.5], 'material':'edge'},
        {'from':[7,29,7], 'to':[9,32,9], 'material':'hot'},
        {'from':[7,11,5], 'to':[9,24,6], 'material':'hot'},
    ]
    for name in ('body', 'head', 'cleaver', 'core'):
        cubes = json.loads(json.dumps(PARTS[name]))
        for cube in cubes:
            if cube['material'] == 'ember': cube['material'] = 'hot'
            elif name == 'body' and cube['material'] == 'edge': cube['material'] = 'ember'
            elif name == 'head' and cube['material'] == 'bone': cube['material'] = 'coal'
        models[name+'_unbound'] = cubes
    for name, cubes in models.items():
        elements = []
        for cube in cubes:
            element = {k:v for k,v in cube.items() if k != 'material'}
            element['faces'] = {face: {'uv':[0,0,16,16], 'texture':'#'+cube['material']} for face in ('north','south','east','west','up','down')}
            elements.append(element)
        write_json(PACK/f'assets/sevensins/models/wrath/{name}.json', {
            'textures': {m:f'sevensins:item/wrath/{m}' for m in PALETTE},
            'elements': elements, 'gui_light':'front',
            # Cancel the item renderer's Y flip so cubes, offsets and pivots agree.
            'display': {'fixed': {'rotation':[0,180,0], 'translation':[0,0,0], 'scale':[1,1,1]}}})
        write_json(PACK/f'assets/sevensins/items/wrath/{name}.json', {
            'model': {'type':'minecraft:model', 'model':f'sevensins:wrath/{name}'}})
    # Original pack icon: burning eye slits inside a horned dark helmet.
    icon = [[(12,10,19,255) for _ in range(64)] for _ in range(64)]
    for y in range(64):
        for x in range(64):
            if 15 <= x <= 48 and 17 <= y <= 53: icon[y][x] = (43,44,58,255)
            if (13 <= x <= 20 or 43 <= x <= 50) and 5 <= y <= 25: icon[y][x] = (192,176,151,255)
            if (18 <= x <= 27 or 36 <= x <= 45) and 29 <= y <= 32: icon[y][x] = (255,65,29,255)
            if 29 <= x <= 34 and 38 <= y <= 50: icon[y][x] = (255,157,51,255)
    png(PACK/'pack.png', icon)
    output = ROOT/'src/main/resources/resource-packs/7sins-java.zip'
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in sorted(PACK.rglob('*')):
            if not path.is_file(): continue
            info = zipfile.ZipInfo(path.relative_to(PACK).as_posix(), date_time=(2026,1,1,0,0,0))
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, path.read_bytes())
    digest = hashlib.sha1(output.read_bytes()).hexdigest()
    write_json(ROOT/'art/pack-hashes.json', {'java_sha1':digest, 'parts':len(PARTS), 'cubes':sum(map(len, PARTS.values())), 'resource_pack_format':[88,0]})
    print(f'{output}: {output.stat().st_size} bytes, SHA-1 {digest}')


PIVOTS = {'body':(0,1.65,0),'head':(0,2.85,0),'left_arm':(.8,2.3,0),'right_arm':(-.8,2.3,0),
          'left_leg':(.35,.875,0),'right_leg':(-.35,.875,0),'cleaver':(-.8863,1.2235,-.02),
          'core':(0,2,-.42),'crown':(0,3.15,0)}


def model_faces(yaw=-25):
    faces = []
    theta = math.radians(yaw)
    # Front is local -Z, exactly as the Java item models.
    def transform(point, cube, pivot, name):
        p = list(point)
        if 'rotation' in cube:
            r = cube['rotation']; origin = r['origin']; angle = math.radians(r['angle'])
            axes = {'x':(1,2),'y':(2,0),'z':(0,1)}[r['axis']]
            a,b = axes; u,v = p[a]-origin[a], p[b]-origin[b]
            p[a]=origin[a]+u*math.cos(angle)-v*math.sin(angle)
            p[b]=origin[b]+u*math.sin(angle)+v*math.cos(angle)
        x,y,z = [(p[i]-8)/16 for i in range(3)]
        rx,rz = {'cleaver':(-2.3,.35),'right_arm':(0,-.08),'left_arm':(0,-.06)}.get(name,(0,0))
        x,y = x*math.cos(rz)-y*math.sin(rz), x*math.sin(rz)+y*math.cos(rz)
        y,z = y*math.cos(rx)-z*math.sin(rx), y*math.sin(rx)+z*math.cos(rx)
        x,y,z = x+pivot[0],y+pivot[1],z+pivot[2]
        return x*math.cos(theta)-z*math.sin(theta), y, x*math.sin(theta)+z*math.cos(theta)
    for name, cubes in PARTS.items():
        for cube in cubes:
            a,b=cube['from'],cube['to']
            points = [transform((x,y,z), cube, PIVOTS[name], name) for x,y,z in
                      [(a[0],a[1],a[2]),(b[0],a[1],a[2]),(b[0],b[1],a[2]),(a[0],b[1],a[2]),
                       (a[0],a[1],b[2]),(b[0],a[1],b[2]),(b[0],b[1],b[2]),(a[0],b[1],b[2])]]
            for ids, shade in [((0,1,2,3),1.05),((4,7,6,5),.6),((0,3,7,4),.7),((1,5,6,2),.85),((3,2,6,7),1.25),((0,4,5,1),.45)]:
                vertices = [points[i] for i in ids]
                depth = sum(z-y*.3 for x,y,z in vertices)/4
                color = tuple(min(255,int(c*shade)) for c in PALETTE[cube['material']])
                faces.append((depth,vertices,color,cube['material']))
    return sorted(faces, key=lambda f:f[0], reverse=True)


def preview():
    output=ROOT/'art/wrath-preview.svg'
    svg=['<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="900" viewBox="0 0 1200 900">',
         '<defs><radialGradient id="bg"><stop stop-color="#372024"/><stop offset="1" stop-color="#090b12"/></radialGradient>',
         '<radialGradient id="heat"><stop stop-color="#ef442b" stop-opacity=".3"/><stop offset="1" stop-color="#ef442b" stop-opacity="0"/></radialGradient></defs>',
         '<rect width="1200" height="900" fill="url(#bg)"/>',
         '<circle cx="600" cy="460" r="340" fill="url(#heat)"/>',
         '<ellipse cx="600" cy="787" rx="260" ry="38" fill="#05060b"/>']
    for depth, vertices, color, material in model_faces():
        coords=' '.join(f'{600+x*172:.1f},{780-y*172+z*48:.1f}' for x,y,z in vertices)
        fill='#'+''.join(f'{c:02x}' for c in color)
        svg.append(f'<polygon points="{coords}" fill="{fill}" stroke="#0d0a12" stroke-width="1.1"/>')
    svg += ['<text x="58" y="77" fill="#fb7355" font-family="sans-serif" font-size="52" font-weight="bold" letter-spacing="8">WRATH</text>',
            '<text x="61" y="111" fill="#c0afb0" font-family="sans-serif" font-size="18" letter-spacing="3">THE ASHEN EXECUTIONER</text>',
            '<text x="61" y="845" fill="#a69b9e" font-family="sans-serif" font-size="15">7SINS  /  ORIGINAL IN-GAME CUBE GEOMETRY</text>',
            '<text x="61" y="871" fill="#746d7d" font-family="sans-serif" font-size="13">Model preview with simulated lighting. Not a Minecraft screenshot.</text>', '</svg>']
    output.write_text('\n'.join(svg),encoding='utf8')
    print(output)


if __name__ == '__main__':
    geometry(); make_pack(); preview()
