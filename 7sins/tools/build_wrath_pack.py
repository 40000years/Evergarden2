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
import boss_designs as designs

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / 'art/java'
PALETTE = designs.PALETTES['wrath']
PARTS = {}


def geometry():
    PARTS.clear()
    PARTS.update(designs.geometry('wrath', variants=False))



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


SIN_PALETTES = {sin: palette for sin, palette in designs.PALETTES.items() if sin != 'wrath'}


def sin_geometry(sin):
    return designs.geometry(sin)



def make_other_sins():
    for sin, palette in SIN_PALETTES.items():
        for material,color in palette.items():
            png(PACK/f'assets/sevensins/textures/item/{sin}/{material}.png',designs.texture(material,color))
        for name,cubes in sin_geometry(sin).items():
            elements=[]
            for cube in cubes:
                element={k:v for k,v in cube.items() if k!='material'}
                element['faces']={face:{'uv':[0,0,16,16],'texture':'#'+cube['material']} for face in ('north','south','east','west','up','down')}
                elements.append(element)
            write_json(PACK/f'assets/sevensins/models/{sin}/{name}.json',{
                'textures':{'particle':f'sevensins:item/{sin}/armor', **{m:f'sevensins:item/{sin}/{m}' for m in palette}},
                'elements':elements,'gui_light':'front',
                'display':{'fixed':{'rotation':[0,180,0],'translation':[0,0,0],'scale':[1,1,1]}}})
            write_json(PACK/f'assets/sevensins/items/{sin}/{name}.json',{
                'model':{'type':'minecraft:model','model':f'sevensins:{sin}/{name}'}})


def make_pack():
    write_json(PACK/'pack.mcmeta', {'pack': {'description': '7sins | The Seven Deadly Sins',
                                          'min_format': [88, 0], 'max_format': [88, 0]}})
    make_other_sins()
    for material, color in PALETTE.items():
        png(PACK/f'assets/sevensins/textures/item/wrath/{material}.png',designs.texture(material,color))
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
            'textures': {'particle':'sevensins:item/wrath/armor', **{m:f'sevensins:item/wrath/{m}' for m in PALETTE}},
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
    write_json(ROOT/'art/pack-hashes.json', {'java_sha1':digest, 'parts':len(PARTS), 'cubes':sum(map(len, PARTS.values())),
        'bosses':{sin:{'bones':len(parts),'cubes':sum(map(len,parts.values()))} for sin in designs.PALETTES
                  for parts in [designs.geometry(sin,variants=False)]}, 'resource_pack_format':[88,0]})
    print(f'{output}: {output.stat().st_size} bytes, SHA-1 {digest}')


PIVOTS = {'body':(0,1.65,0),'head':(0,2.85,0),'left_arm':(.8,2.3,0),'right_arm':(-.8,2.3,0),
          'left_leg':(.35,.875,0),'right_leg':(-.35,.875,0),'cleaver':(-.8863,1.2235,-.02),
          'core':(0,2,-.42),'crown':(0,3.15,0)}


def model_faces(yaw=-25):
    faces = []
    theta = math.radians(yaw)
    pose_path=ROOT/'art/model-rest-pose.json'
    poses=json.loads(pose_path.read_text()).get('wrath',{}).get('bones',{}) if pose_path.exists() else {}
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
        if name in poses:
            a,b,c,w=poses[name]['rotation']
            tx,ty,tz=2*(b*z-c*y),2*(c*x-a*z),2*(a*y-b*x)
            x,y,z=x+w*tx+b*tz-c*ty,y+w*ty+c*tx-a*tz,z+w*tz+a*ty-b*tx
            x,y,z=[v+o for v,o in zip((x,y,z),poses[name]['position'])]
        else:
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
