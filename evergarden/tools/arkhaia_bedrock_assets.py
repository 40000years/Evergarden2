"""Export the native Arkhaia rig to a Bedrock client entity, without helmet frame swaps.

Geometry follows Blockbench's Bedrock codec. Our bbmodel animation channels are
ME-specific: undo ME's X/Y conversion to recover geometry-space poses, then apply
Bedrock's X/Y export conversion. These two conversions cancel for rotations.
"""
import base64
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
SCALE=3.2

def export_model(model, model_id="arkhaia", scale=3.2, bright=False):
    """bright: render without world lighting, matching the full-bright Java ModelEngine rig."""
    SCALE=scale
    encode=lambda value:(json.dumps(value,separators=(',',':'))+'\n').encode()
    elements={e['uuid']:e for e in model['elements']}
    bones=[]
    def point(p):return [-p[0]*SCALE,p[1]*SCALE,p[2]*SCALE]
    def name(group):return 'gaze' if group['name']=='hi_gaze' else group['name']
    def visit(group,parent=None):
        if group['name']=='hitbox':return
        bone=dict(name=name(group),pivot=point(group['origin']),cubes=[])
        if parent:bone['parent']=parent
        for child in group['children']:
            if isinstance(child,dict):continue
            e=elements[child];size=[e['to'][i]-e['from'][i] for i in range(3)]
            cube=dict(origin=point([e['to'][0],e['from'][1],e['from'][2]]),size=[v*SCALE for v in size],uv={})
            if any(e.get('rotation',[])):
                r=e['rotation'];cube.update(pivot=point(e['origin']),rotation=[-r[0],-r[1],r[2]])
            for face,data in e['faces'].items():
                # Alpha-cut sheets texture only their two broad faces.
                if data.get('texture') is None:continue
                uv=data['uv'];start=uv[:2];span=[uv[i+2]-uv[i] for i in range(2)]
                if face in ('up','down'):start=[start[i]+span[i] for i in range(2)];span=[-v for v in span]
                cube['uv'][face]=dict(uv=start,uv_size=span)
            bone['cubes'].append(cube)
        bones.append(bone)
        for child in group['children']:
            if isinstance(child,dict):visit(child,bone['name'])
    for group in model['outliner']:visit(group)
    animations={};states={};aliases={}
    # 'aura' is not selectable by the clip property; it loops under every state.
    selectable=[c for c in model['animations'] if c['name']!='aura']
    for clip in model['animations']:
        alias=clip['name'];full='animation.voidscape.arkhaia.'+alias;aliases[alias]=full
        channels={}
        for animator in clip['animators'].values():
            bone_channels={}
            for frame in animator['keyframes']:
                channel=frame['channel'];p=frame['data_points'][0];v=[float(p[a]) for a in 'xyz']
                # Rotation already has the signs required by Bedrock. Flipping
                # X/Y again reverses the sword sweep, casting arm and head bob.
                if channel=='position':
                    x=-v[0] if model.get('meta',{}).get('evergarden_animation_position_x')=='inverted' else v[0]
                    v=[-x*SCALE,v[1]*SCALE,v[2]*SCALE]
                entry=v if frame['interpolation']!='catmullrom' else dict(post=v,lerp_mode='catmullrom')
                bone_channels.setdefault(channel,{})[str(frame['time'])]=entry
            # Gaze is owned by its additive look layer, not the full body clips.
            if animator['name'] not in ('gaze','hi_gaze'):channels[animator['name']]=bone_channels
        if alias=='aura':
            animations[full]=dict(loop=True,animation_length=clip['length'],bones=channels);continue
        animations[full]=dict(loop=True if clip['loop']=='loop' else 'hold_on_last_frame',animation_length=clip['length'],
            anim_time_update="q.property('voidscape:clip_time') + v.extrapolate * q.property('voidscape:rate')",bones=channels)
        index=selectable.index(clip)
        states[alias]=dict(animations=[alias],blend_transition=.05,transitions=[{other['name']:f"q.property('voidscape:clip') == {j}"}
            for j,other in enumerate(selectable) if j!=index])
    aliases['look']='animation.voidscape.arkhaia.look';aliases['combat']='controller.animation.voidscape.arkhaia'
    animations[aliases['look']]=dict(loop=True,bones={'gaze':{'rotation':['v.look_pitch','v.look_yaw',0]}})
    # UVs are authored in the atlas's own pixel space (rivers 1024, Malefeasance phase 2 2048).
    resolution=model.get('resolution',{'width':1024,'height':1024})
    entity={'format_version':'1.10.0','minecraft:client_entity':{'description':{
        'identifier':'voidscape:arkhaia','materials':{'default':'entity_alphatest'},
        'textures':{'default':'textures/entity/arkhaia_native'},'geometry':{'default':'geometry.voidscape.arkhaia_native'},
        'enable_attachables':False,'animations':aliases,
        'scripts':{'initialize':['v.previous_time = -1;','v.previous_clip = -1;','v.extrapolate = 0;','v.look_yaw = 0;','v.look_pitch = 0;'],
            'pre_animation':[
                "v.extrapolate = (v.previous_time == q.property('voidscape:clip_time') && v.previous_clip == q.property('voidscape:clip')) ? math.min(v.extrapolate + q.delta_time, 0.1) : 0;",
                "v.previous_time = q.property('voidscape:clip_time');","v.previous_clip = q.property('voidscape:clip');",
                "v.look_yaw = v.look_yaw + (q.property('voidscape:look_yaw') - v.look_yaw) * math.min(q.delta_time * 18, 1);",
                "v.look_pitch = v.look_pitch + (q.property('voidscape:look_pitch') - v.look_pitch) * math.min(q.delta_time * 18, 1);"],
            'animate':['combat','look']+(['aura'] if 'aura' in aliases else [])},'render_controllers':['controller.render.voidscape.arkhaia']}}}
    result={
        'entity/arkhaia_native.entity.json':encode(entity),
        'models/entity/arkhaia_native.geo.json':encode({'format_version':'1.16.0','minecraft:geometry':[{
            'description':{'identifier':'geometry.voidscape.arkhaia_native','texture_width':resolution['width'],'texture_height':resolution['height'],
                'visible_bounds_width':18,'visible_bounds_height':18,'visible_bounds_offset':[0,5,0]},'bones':bones}]}),
        'animations/arkhaia_native.animation.json':encode({'format_version':'1.8.0','animations':animations}),
        'animation_controllers/arkhaia_native.controller.json':encode({'format_version':'1.10.0','animation_controllers':{
            aliases['combat']:{'initial_state':'idle','states':states}}}),
        'render_controllers/arkhaia_native.render.json':encode({'format_version':'1.8.0','render_controllers':{
            'controller.render.voidscape.arkhaia':dict({'geometry':'Geometry.default','materials':[{'*':'Material.default'}],'textures':['Texture.default']},
                                                        **({'ignore_lighting':True} if bright else {}))}}),
        'textures/entity/arkhaia_native.png':base64.b64decode(model['textures'][0]['source'].split(',')[1])}

    if model_id!='arkhaia':
        description=result['models/entity/arkhaia_native.geo.json']
        geometry=json.loads(description)
        geometry['minecraft:geometry'][0]['description'].update(visible_bounds_width=7*scale,visible_bounds_height=7*scale,visible_bounds_offset=[0,2*scale,0])
        result['models/entity/arkhaia_native.geo.json']=encode(geometry)
        renamed={}
        for path,content in result.items():
            path=path.replace('arkhaia_native',model_id+'_native')
            if path.endswith('.json'):content=content.decode().replace('arkhaia',model_id).encode()
            renamed[path]=content
        result=renamed
    return result

def assets():
    """Native Bedrock entities for the Evergarden main bosses (Arkhaia, Seraphon)."""
    result={}
    for model_id in ('arkhaia','seraphon'):
        model=json.loads((ROOT/f'evergarden/src/main/resources/modelengine/{model_id}.bbmodel').read_text(encoding='utf8'))
        result.update(export_model(model,model_id,3.2,bright=True))
    return result
