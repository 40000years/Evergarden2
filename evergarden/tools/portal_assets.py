"""Animated azure portal sprite and dedicated wearable planes for both clients."""
import math
import struct
import zlib

def register(java, bedrock, textures, mappings, selectors, write_json):
    rows=[]
    tau = math.tau
    for frame in range(16):
        phase = frame * tau / 16.0
        for y in range(32):
            row=bytearray([0])
            for x in range(32):
                dx = (x - 15.5) / 16.0
                dy = (y - 15.5) / 16.0
                r = math.hypot(dx, dy)
                theta = math.atan2(dy, dx)
                # Nether-portal style multi-arm swirling vortex + counter-filaments + vertical plasma drift
                s1 = math.sin(3.0 * theta - 4.2 * r + phase)
                s2 = math.cos(2.0 * theta + 5.0 * r - 2.0 * phase)
                s3 = math.sin(3.0 * dx + 4.0 * dy - phase)
                s4 = math.sin(6.0 * r - 2.0 * phase + math.cos(2.0 * theta))
                v = 0.42 * s1 + 0.28 * s2 + 0.18 * s3 + 0.12 * s4
                t = max(0.0, min(1.0, (v + 1.0) / 2.0))
                # High-contrast azure palette: deep abyss -> rich ocean -> electric cyan -> radiant white-cyan
                if t < 0.35:
                    p = t / 0.35
                    r_col = int(8 + (18 - 8) * p)
                    g_col = int(24 + (90 - 24) * p)
                    b_col = int(80 + (175 - 80) * p)
                    a_col = int(200 + (225 - 200) * p)
                elif t < 0.72:
                    p = (t - 0.35) / 0.37
                    r_col = int(18 + (35 - 18) * p)
                    g_col = int(90 + (210 - 90) * p)
                    b_col = int(175 + (255 - 175) * p)
                    a_col = int(225 + (248 - 225) * p)
                else:
                    p = (t - 0.72) / 0.28
                    r_col = int(35 + (220 - 35) * (p ** 1.4))
                    g_col = int(210 + (252 - 210) * p)
                    b_col = 255
                    a_col = int(248 + (255 - 248) * p)
                row.extend((r_col, g_col, b_col, a_col))
            rows.append(bytes(row))
    def chunk(kind,data):
        return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
    data=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',32,512,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(b''.join(rows),9))+chunk(b'IEND',b'')
    for path in (java/'assets/voidscape/textures/item/azure_portal.png',bedrock/'textures/items/azure_portal.png'):
        path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
    write_json(java/'assets/voidscape/textures/item/azure_portal.png.mcmeta',{'animation':{'frametime':2,'interpolate':True}})
    write_json(java/'assets/voidscape/models/item/azure_portal.json',{
        'textures':{'portal':'voidscape:item/azure_portal'},
        'elements':[{'from':[0,0,7.9],'to':[16,16,8.1], 'faces':{f:{'texture':'#portal','uv':[0,0,16,16]} for f in ('north','south')}}],
        'display':{
            'head':{'translation':[0,6.4,0],'scale':[1.7,1.7,1.7]},
            'gui':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[1,1,1]},
            'fixed':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[1,1,1]},
            'ground':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[0.5,0.5,0.5]}
        }})
    model={'type':'minecraft:model','model':'voidscape:item/azure_portal'}
    write_json(java/'assets/voidscape/items/azure_portal.json',{'model':model})
    selectors.setdefault('iron_helmet',[]).append({'when':'voidscape:azure_portal','model':model})
    textures['voidscape.azure_portal']={'textures':'textures/items/azure_portal'}
    mappings['items'].setdefault('minecraft:iron_helmet',[]).append({
        'type':'definition','model':'minecraft:iron_helmet',
        'predicate':{'type':'match','property':'custom_model_data','index':0,'value':'voidscape:azure_portal'},
        'bedrock_identifier':'voidscape:azure_portal','display_name':'Evergarden Azure Portal',
        'bedrock_options':{'icon':'voidscape.azure_portal','allow_offhand':False,'display_handheld':False}})
    write_json(bedrock/'models/entity/azure_portal.geo.json',{'format_version':'1.16.0','minecraft:geometry':[{
        'description':{'identifier':'geometry.voidscape.azure_portal','texture_width':32,'texture_height':512,'visible_bounds_width':3,'visible_bounds_height':3,'visible_bounds_offset':[0,1.75,0]},
        'bones':[{'name':'head','pivot':[0,24,0],'cubes':[{'origin':[-8.1,23.9,-0.1],'size':[16.2,16.2,0.2],
            'uv':{f:{'uv':[0,0],'uv_size':[32,32]} for f in ('north','south')}}]}]}]})
    write_json(bedrock/'attachables/azure_portal.json',{'format_version':'1.10.0','minecraft:attachable':{'description':{
        'identifier':'voidscape:azure_portal','materials':{'default':'evergarden_portal'},
        'textures':{'default':'textures/items/azure_portal'},'geometry':{'default':'geometry.voidscape.azure_portal'},
        'render_controllers':['controller.render.evergarden_portal']}}})
    write_json(bedrock/'render_controllers/azure_portal.json',{'format_version':'1.8.0','render_controllers':{
        'controller.render.evergarden_portal':{'geometry':'Geometry.default','materials':[{'*':'Material.default'}],
            'textures':['Texture.default'],'ignore_lighting':True,
            'uv_anim':{'offset':[0.0,'math.mod(math.floor(query.life_time * 10.0), 16.0) / 16.0'],'scale':[1.0,1.0]}}}})
    mat_def={'materials':{'version':'1.0.0',
        'evergarden_portal:entity_alphablend':{'+defines':['USE_UV_ANIM'],'+states':['Blending']}}}
    write_json(bedrock/'materials/evergarden_portal.material',mat_def)
    write_json(bedrock/'materials/entity.material',mat_def)
    write_json(bedrock/'materials/entity.material.json',mat_def)
    write_json(bedrock/'textures/flipbook_textures.json',[{
        'flipbook_texture':'textures/items/azure_portal',
        'atlas_tile':'voidscape.azure_portal',
        'ticks_per_frame':2}])
