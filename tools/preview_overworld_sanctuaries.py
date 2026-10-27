"""Render the exact shipped Overworld assets, including honest interior cutaways."""
import argparse, gzip, json, struct, uuid
from pathlib import Path
from PIL import Image, ImageDraw
from preview_sky_whale import Renderer, PALETTE, font
from preview_landmarks import LandmarkTextures
import preview_sky_whale as voxel_renderer
from preview_small_structures import shaped_cuboids
from build_overworld_sanctuaries import ROOT, ASSETS, PREVIEWS, IDS

class SanctuaryTextures(LandmarkTextures):
    def get(self,material,axis):
        texture=super().get('SNOW' if material=='SNOW_BLOCK' else material,axis)
        if material=='ACACIA_LEAVES':
            texture=texture.copy();texture[:,:,:3]*=(.65,.73,.3)
        return texture

def sanctuary_cuboids(original,states):
    shaped=shaped_cuboids(original,states)
    def boxes(material,position,blocks):
        if material=='SNOW':return [(0.,0.,0.,1.,.125,1.)]
        if material.endswith('_FENCE'):
            state=states.get(','.join(map(str,position)),'')
            result=[(.375,0.,.375,.625,1.,.625)]
            for face,axis,sign in (('north',2,-1),('south',2,1),('west',0,-1),('east',0,1)):
                if face+'=true' not in state:continue
                for y0,y1 in ((.375,.5625),(.75,.9375)):
                    lo=[.4375,y0,.4375];hi=[.5625,y1,.5625]
                    lo[axis]=0. if sign<0 else .5;hi[axis]=.5 if sign<0 else 1.
                    result.append(tuple(lo+hi))
            return result
        return shaped(material,position,blocks)
    return boxes

def read_asset(path,states=None):
    with gzip.open(path,'rb') as f:
        assert f.read(4)==b'OWS1'
        palette=[];raw=[]
        for _ in range(struct.unpack('>H',f.read(2))[0]):
            state=f.read(struct.unpack('>H',f.read(2))[0]).decode();raw.append(state)
            palette.append(state.removeprefix('minecraft:').split('[')[0].upper())
        n=struct.unpack('>I',f.read(4))[0]
        blocks={}
        for _ in range(n):
            x,y,z,m=struct.unpack('>hhhH',f.read(8));blocks[x,y,z]=palette[m]
            if states is not None:states[f'{x},{y},{z}']=raw[m]
        return blocks

def main():
    p=argparse.ArgumentParser();p.add_argument('--only',choices=IDS);p.add_argument('--width',type=int,default=1600);p.add_argument('--cutaway',action='store_true');p.add_argument('--upper',action='store_true');p.add_argument('--view',choices=('overview','front','back','axe'),default='overview');p.add_argument('--assemble',action='store_true');p.add_argument('--minecraft-jar',type=Path);args=p.parse_args()
    if args.minecraft_jar is None:
        local=Path.home()/'AppData/Roaming/.minecraft/versions/26.3/26.3.jar'
        if local.exists():args.minecraft_jar=local
    PALETTE.update({'DEEPSLATE_BRICKS':(63,64,73),'POLISHED_BLACKSTONE_BRICKS':(46,42,49),'CHISELED_POLISHED_BLACKSTONE':(55,51,58),'RED_NETHER_BRICKS':(75,23,31),'QUARTZ_BRICKS':(224,221,213),'POLISHED_DIORITE':(206,207,200),'BLUE_TERRACOTTA':(51,58,101),'WAXED_OXIDIZED_CUT_COPPER':(71,145,121),'WAXED_CUT_COPPER':(185,106,76),'DARK_PRISMARINE':(47,91,80),'MUD_BRICKS':(137,102,79),'DARK_OAK_PLANKS':(65,43,24),'GOLD_BLOCK':(236,184,49),'RED_CARPET':(160,36,42),'YELLOW_CARPET':(224,175,39),'PURPLE_CARPET':(126,55,163),'AMETHYST_BLOCK':(137,101,184),'SOUL_LANTERN':(82,204,212),'ORANGE_STAINED_GLASS':(224,128,41),'RED_STAINED_GLASS':(158,34,51),'PURPLE_STAINED_GLASS':(142,70,170)})
    if not args.assemble:
        original_cuboids=voxel_renderer.cuboids
        for name in ([args.only] if args.only else IDS):
            states={};b=read_asset(ASSETS/(name+'.ows'),states)
            voxel_renderer.cuboids=sanctuary_cuboids(original_cuboids,states)
            if args.cutaway:
                # Show the furnished main hall at readable scale, retaining upper
                # galleries, vault ribs and chandeliers that the v1 view hid.
                metadata=json.loads((PREVIEWS/(name+'-design.json')).read_text())
                left,back,right,front,floor,ceiling=metadata['design']['cutaway']
                if name=='styx':left,back,right,front,floor,ceiling=-20,-88,20,-47,5,29
                ceiling=floor+9
                if args.upper:floor+=9;ceiling=floor+10
                side_cut=right-2
                front_cut=front-2
                b={p:m for p,m in b.items() if left<=p[0]<=right and back<=p[2]<=front and floor-2<=p[1]<ceiling
                    and not(p[0]>=side_cut and p[1]>floor+1) and not(p[2]>=front_cut and p[1]>floor+1)}
            suffix=('-upper' if args.upper else '-interior') if args.cutaway else ''
            if not args.cutaway and args.view!='overview':suffix='-'+args.view
            if args.view=='axe':b={p:m for p,m in b.items() if -22<=p[0]<=22 and 38<=p[1]<=84 and -49<=p[2]<=-36}
            destination=PREVIEWS/(name+suffix+'.png')
            temporary=PREVIEWS/(name+suffix+'-'+uuid.uuid4().hex+'.png')
            Renderer(b,SanctuaryTextures(args.minecraft_jar)).render(temporary,name.upper()+' / OVERWORLD',
                'Revision 5 | '+(('Upper suites' if args.upper else 'Furnished ground floor')+' / image-only ceiling and wall cutaway' if args.cutaway else 'Connected architecture / proportion and detail corrections'),width=args.width,height=round(args.width*.8),azimuth=(0 if args.view in ('front','axe') else 205 if args.view=='back' else 25),elevation=48 if args.cutaway else 12 if args.view=='axe' else 20 if args.view=='front' else 24,shadows=False)
            temporary.replace(destination)
            print('Rendered',name+suffix,flush=True)
    if args.assemble:
        canvas=Image.new('RGB',(2000,2460),'#111b26');d=ImageDraw.Draw(canvas)
        d.text((45,24),'SIX RIVERS / OVERWORLD SANCTUARIES',font=font(35,True),fill='#f4dab0')
        d.text((45,75),'Actual shipped Minecraft blocks. Simulated lighting; not game screenshots.',font=font(20),fill='#c1cedb')
        for i,name in enumerate(IDS):
            with Image.open(PREVIEWS/(name+'.png')) as im:canvas.paste(im.resize((980,770)),(10+(i%2)*1000,120+(i//2)*780))
        canvas.save(PREVIEWS/'overview.png')
        for i,name in enumerate(IDS):
            with Image.open(PREVIEWS/(name+'-interior.png')) as im:canvas.paste(im.resize((980,770)),(10+(i%2)*1000,120+(i//2)*780))
        d=ImageDraw.Draw(canvas);d.rectangle((0,0,2000,116),fill='#111b26');d.text((45,24),'SIX RIVERS / FURNISHED MAIN HALLS',font=font(35,True),fill='#f4dab0')
        d.text((45,75),'Actual blocks. Selected walls and roofs removed in these images only.',font=font(20),fill='#c1cedb');canvas.save(PREVIEWS/'interior-overview.png')
        for i,name in enumerate(IDS):
            with Image.open(PREVIEWS/(name+'-upper.png')) as im:canvas.paste(im.resize((980,770)),(10+(i%2)*1000,120+(i//2)*780))
        d=ImageDraw.Draw(canvas);d.rectangle((0,0,2000,116),fill='#111b26');d.text((45,24),'SIX RIVERS / UPPER ROOMS AND GALLERIES',font=font(35,True),fill='#f4dab0')
        d.text((45,75),'Actual blocks. Ceilings removed in these images only.',font=font(20),fill='#c1cedb');canvas.save(PREVIEWS/'upper-overview.png')
        cards=''
        for name in IDS:
            info=json.loads((PREVIEWS/(name+'-design.json')).read_text())['design']
            cards+='<section><h2>'+name.title()+'</h2><p>'+str(len(info['occupied_rooms']))+' furnished spaces; rare accent blocks: '+str(info['rare_total'])+' / 128 maximum.</p>'
            for suffix,label in (('','Exterior'),('-back','Rear / side inspection'),('-interior','Ground floor'),('-upper','Upper rooms / galleries')):
                cards+='<figure><a href="'+name+suffix+'.png"><img src="'+name+suffix+'.png"></a><figcaption>'+label+'</figcaption></figure>'
            cards+='<details><summary>Before this correction pass</summary><img src="../overworld-sanctuaries-v4/'+name+'.png"><img src="../overworld-sanctuaries-v4/'+name+'-interior.png"></details></section>'
        (PREVIEWS/'index.html').write_text('<!doctype html><meta charset="utf-8"><title>Six Rivers / Structural polish / revision 5</title><style>body{background:#111b26;color:#e8dfd2;font:17px system-ui;max-width:1750px;margin:40px auto}section{margin:50px 0}figure{display:inline-block;width:48%;margin:0 .5% 22px}img{width:100%;border-radius:10px}details img{max-width:800px}a{color:#d2b982}figcaption{text-align:center}</style><h1>Six Rivers / Proportion and structural corrections / revision 5</h1><p>Connected crescent axe and socket; supported portals, rose windows, dome, gantry and lantern turret. Corrected furniture height, inward-facing artwork, ceiling hangers and explicit in-game fence/wall joins. No detached assemblies remain in the six authored assets. Rare material budgets, eight caches per site and boss courts retained. Actual blocks with simulated lighting, not game screenshots.</p><section><h2>Styx / revised axe silhouette</h2><a href="styx-axe.png"><img style="max-width:950px" src="styx-axe.png"></a><p><a href="styx-front.png">Full frontal inspection</a></p></section>'+cards,encoding='utf8')

if __name__=='__main__':main()
