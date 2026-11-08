"""Build, validate and promote the mount animation/Fenrir/Aetherius update over e2.178."""
import argparse,json
import build_mythical_pet_release as release

release.OUT=release.ROOT/'output/mythical-mount-refinement'
release.VERSION='3.0.0-e2.179-mount-refinement'
release.BASE_SHA='aa094cf1a49d498ef412218787dd71809defd0f1aa6bf38fab1ec6908b8d0f2e'
release.FAMILIES=tuple('com/example/voidscape/pet/'+n for n in ('PetRiding','PetMountBlessing','PetMenu','PetCompanions'))
release.RESOURCES={n:release.RES/n for n in ('config.yml','native-pets.json','judge-design.json','modelengine/pets/manifest.json')}
release.RESOURCES.update({'modelengine/pets/everpet_'+s+'_'+age+'.bbmodel':release.RES/('modelengine/pets/everpet_'+s+'_'+age+'.bbmodel')
                         for s in ('aetherius','ignis_draconis','fenrir') for age in ('baby','medium','adult')})
release.RESOURCES.update({'resource-packs/'+n:release.ROOT/'evergarden/dist'/n for n in ('evergarden-bedrock.mcpack','pack-hashes.json')})
release.PROBES=[release.ROOT/'tests/model-engine'/n for n in ('PetSystemNativeProbe.java','MythicalPetProbe.java','MythicalRidingProbe.java')]
release.PROBE_MAIN='MythicalRidingProbe'

def assets(old):
    before=json.loads(old.read('native-pets.json'))['models'];after=json.loads((release.RES/'native-pets.json').read_bytes())['models']
    for name,model in before.items():
        if not name.startswith(('aetherius_','ignis_draconis_','fenrir_')):assert after[name]==model,name
        else:
            assert after[name]['bones']==model['bones'],name+' mesh changed'
    before_pack=release.zip_entries(old.read('resource-packs/evergarden-bedrock.mcpack'))
    after_pack=release.zip_entries(release.RESOURCES['resource-packs/evergarden-bedrock.mcpack'].read_bytes())
    changed={n for n,data in before_pack.items() if after_pack.get(n)!=data}
    assert len(changed)==10 and all(n=='manifest.json' or n.startswith('animations/') and any('everpet_'+s+'_' in n for s in ('aetherius','ignis_draconis','fenrir')) for n in changed),changed
release.check_assets=assets
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--validate',action='store_true');parser.add_argument('--promote',action='store_true');args=parser.parse_args()
    release.promote() if args.promote else release.validate() if args.validate else release.candidate_build()
