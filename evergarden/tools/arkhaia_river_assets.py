"""Ship the approved V2 rigs and personal seals on both clients."""
import json
from pathlib import Path
from arkhaia_bedrock_assets import export_model

ROOT=Path(__file__).resolve().parents[2]
NAMES=['acheron','phlegethon','cocytus','banshee_wail','malefeasance','styx']

def bedrock_scale(model_id):
    """Same as RiverVisual.scale(model): both Malefeasance rigs render at half the river scale."""
    return 2 if 'malefeasance' in model_id else 4

def assets(encode,java_source,bedrock_source):
    java,bedrock={},{}
    for name in NAMES:
        model_id='arkhaia_'+name
        model=json.loads((ROOT/f'evergarden/src/main/resources/modelengine/rivers/{model_id}.bbmodel').read_text(encoding='utf8'))
        bedrock.update(export_model(model,model_id,bedrock_scale(model_id)))
        effect='river_'+name
        # The original alpha strokes are retained; they are never tinted violet.
        png=(ROOT/f'evergarden/art/arkhaia-rivers/{name}.png').read_bytes()
        java[f'assets/voidscape/textures/effect/{effect}.png']=png
        bedrock[f'textures/particle/{effect}.png']=png
        for folder in ['models/effect','items']:
            data=json.loads(java_source[f'assets/voidscape/{folder}/judge_orbit.json'].decode().replace('judge_orbit',effect))
            java[f'assets/voidscape/{folder}/{effect}.json']=encode(data)
        for suffix in ['','_flat']:
            template=bedrock_source[f'particles/judge_orbit{suffix}.particle.json'].decode().replace('judge_orbit',effect)
            # Original circles are 1024; the reviewed compact seals are 768 pixels.
            data=json.loads(template)
            billboard=data['particle_effect']['components']['minecraft:particle_appearance_billboard']['uv']
            billboard.update(texture_width=768,texture_height=768,uv_size=[768,768])
            bedrock[f'particles/{effect}{suffix}.particle.json']=encode(data)
    echo=json.loads((ROOT/'evergarden/src/main/resources/modelengine/rivers/arkhaia_acheron_echo.bbmodel').read_text(encoding='utf8'))
    bedrock.update(export_model(echo,'arkhaia_acheron_echo',bedrock_scale('arkhaia_acheron_echo')))
    # Malefeasance swaps to this rig at half HP; it shares phase 1's clips, seals and effects.
    phase2=json.loads((ROOT/'evergarden/src/main/resources/modelengine/rivers/arkhaia_malefeasance_phase2.bbmodel').read_text(encoding='utf8'))
    bedrock.update(export_model(phase2,'arkhaia_malefeasance_phase2',bedrock_scale('arkhaia_malefeasance_phase2')))
    from malefeasance_assets import assets as dimensional_assets
    extra_java,extra_bedrock=dimensional_assets(encode,java_source,bedrock_source)
    java.update(extra_java);bedrock.update(extra_bedrock)
    return java,bedrock
