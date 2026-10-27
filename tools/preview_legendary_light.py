"""Build a self-contained art/motion preview using the exact packaged textures."""
from pathlib import Path
import base64
import json
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'advance-magic/art/effects/legendary-light'
OUT = ROOT / 'previews/legendary-light'


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    assets = {}
    board = Image.new('RGB', (1800, 1480), (8, 12, 25))
    draw = ImageDraw.Draw(board)
    font = ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf', 24)
    small = ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf', 17)
    titles = {'sigil': 'CELESTIAL SIGIL', 'halo': 'PRISMATIC RUNE HALO',
              'beacon': 'FALLING LIGHT RAIN', 'shot': 'ORBITAL VOLLEY RAY', 'mote': 'SPIRAL COLOUR MOTES'}
    for index, key in enumerate(titles):
        path = ART / f'elemental_legendary_light_{key}.png'
        assets[key] = 'data:image/png;base64,' + base64.b64encode(path.read_bytes()).decode('ascii')
        image = Image.open(path)
        dimensions = image.size
        if key == 'mote':
            image = image.resize((320, 320), Image.Resampling.NEAREST)
        image.thumbnail((590, 590), Image.Resampling.LANCZOS)
        x, y = index % 3 * 600, index // 3 * 740
        board.paste(image, (x + (600-image.width)//2, y + 80 + (590-image.height)//2), image)
        draw.text((x + 28, y + 24), titles[key], font=font, fill='#eadfbd')
        label = f'{dimensions[0]} x {dimensions[1]} RGBA / ' + ('ImageGen circle art' if key in ('sigil', 'halo') else 'authored technical VFX')
        draw.text((x + 28, y + 680), label, font=small, fill='#9ba8be')
    board.save(OUT / 'texture-board.png', optimize=True)
    html = TEMPLATE.replace('__ASSETS__', json.dumps(assets))
    (OUT / 'index.html').write_text(html, encoding='utf8')
    print('Created', OUT / 'index.html')
    print('Created', OUT / 'texture-board.png')


TEMPLATE = r'''<!doctype html><html lang="th"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Heaven's Descent · Legendary Light</title>
<style>
*{box-sizing:border-box}body{margin:0;background:#080c17;color:#e5e8f2;font:16px/1.6 system-ui,sans-serif}header,section{max-width:1360px;margin:auto;padding:24px}header{display:flex;justify-content:space-between;align-items:center;gap:20px}h1{font:500 30px/1.2 Georgia,serif;margin:8px 0}small,.note{color:#9aa8c3}.badge{font-size:11px;letter-spacing:3px;color:#e9d69d}.toolbar{display:flex;gap:12px;align-items:center;flex-wrap:wrap}button,select{background:#17213b;color:#e5e8f2;border:1px solid #35405b;border-radius:7px;padding:8px 14px;cursor:pointer}button:hover{border-color:#e9d69d}input[type=range]{accent-color:#e6d19b;flex:1;min-width:180px}canvas{width:100%;aspect-ratio:1280/820;display:block;border:1px solid #28314a;border-radius:12px;background:#080c17}#status{font-variant-numeric:tabular-nums;min-width:170px}#textures{display:grid;grid-template-columns:repeat(3,1fr);gap:18px}figure{margin:0;background:#0e1424;border:1px solid #26304a;border-radius:12px;overflow:hidden}figure img{width:100%;display:block}figcaption{padding:12px;color:#c9d0df}a{color:#e8d69e}.note{font-size:13px;margin-bottom:0}@media(max-width:700px){header{display:block}#textures{grid-template-columns:1fr}h1{font-size:25px}}
</style>
<header><div><div class="badge">LIGHT · LEGENDARY · RADIANT RAIN</div><h1>Heaven's Descent</h1><small>ฝนแสงตกเป็นระลอก · ละอองสี · วงเวทเล็กโคจรและยิงลำแสง</small></div><div class="badge">HORIZONTAL / 32 BLOCKS / 30 SECONDS</div></header>
<section style="padding-top:0"><canvas id="scene" width="1280" height="820"></canvas><div class="toolbar" style="padding-top:16px"><button id="play">พัก</button><input id="time" type="range" min="0" max="31.5" step="0.01" value="6"><span id="status"></span><select id="view"><option value="iso">มุมเฉียง</option><option value="front">มุมหน้า</option><option value="top">มุมสูง</option></select><select id="quality"><option value="balanced">Balanced · 12 layers</option><option value="low">Low / 7 layers</option></select></div><p class="note">พรีวิวนี้นำ PNG ที่อยู่ในแพ็กจริงมาจัดวางตามฉาก VFX บน Canvas · ใช้ดูรายละเอียดและการเคลื่อนไหว · การแสดงแสง ความโปร่งใส และ FPS ในเกมต้องตรวจบน Java และ Bedrock จริง</p></section>
<section style="padding-top:0"><div id="textures"></div><p class="note">วงเวทใช้ต้นฉบับ ImageGen จัดลง texture 2048 × 2048 px ส่วนเสาแสง ลำแสงยิง และละอองเป็นกราฟิกที่สร้างจากรูปทรงและ gradient เพื่อควบคุมขอบโปร่งใส · ฉากนี้ใช้ละอองแยกชิ้นแทนภาพหมอกยืด</p></section>
<script>
const sources=__ASSETS__, images={}, canvas=document.getElementById('scene'),ctx=canvas.getContext('2d');
const slider=document.getElementById('time'),status=document.getElementById('status'),params=new URLSearchParams(location.search);let running=params.get('pause')!=='1',time=Number(params.get('time')??6),last=0;
const names={sigil:'วงเวทหลัก — Celestial Sigil',halo:'วงอักขระชั้นนอก — Prismatic Halo',beacon:'ฝนแสง — Radiant Rain',shot:'ลำแสงจากวงเล็ก — Orbital Volley',mote:'ละอองสี — Spiral Motes'};
const loading=Object.entries(sources).map(([key,src])=>new Promise(resolve=>{let im=new Image();im.onload=()=>{images[key]=im;resolve()};im.src=src;let figure=document.createElement('figure');figure.innerHTML=`<a href="${src}" download="elemental_legendary_light_${key}.png"><img src="${src}" alt="${names[key]}"></a><figcaption>${names[key]}</figcaption>`;document.getElementById('textures').append(figure)}));
const add=(a,b)=>a.map((v,i)=>v+b[i]),mul=(a,n)=>a.map(v=>v*n),dot=(a,b)=>a.reduce((s,v,i)=>s+v*b[i],0),ease=t=>{t=Math.max(0,Math.min(1,t));return t*t*(3-2*t)};
let right,up,eye;function camera(){let mode=document.getElementById('view').value,yaw=mode==='iso'?.52:0,pitch=mode==='top'?1.15:mode==='front'?.12:.5;right=[Math.cos(yaw),0,-Math.sin(yaw)];up=[-Math.sin(yaw)*Math.sin(pitch),Math.cos(pitch),-Math.cos(yaw)*Math.sin(pitch)];eye=[Math.sin(yaw)*Math.cos(pitch),Math.sin(pitch),Math.cos(yaw)*Math.cos(pitch)];}
function project(p){let rel=add(p,[0,-16,0]);return[640+dot(rel,right)*17,420-dot(rel,up)*17]}
function line(a,b,color){let A=project(a),B=project(b);ctx.beginPath();ctx.moveTo(...A);ctx.lineTo(...B);ctx.strokeStyle=color;ctx.stroke()}
function plane(key,p,r,u,w,h,angle){let R=add(mul(r,Math.cos(angle)),mul(u,Math.sin(angle))),U=add(mul(u,Math.cos(angle)),mul(r,-Math.sin(angle)));return{key,p,r:R,u:U,w,h,depth:dot(p,eye)}}
function paint(p){let im=images[p.key],P=project(p.p),R=project(add(p.p,mul(p.r,p.w))),U=project(add(p.p,mul(p.u,p.h))),rx=R[0]-P[0],ry=R[1]-P[1],ux=U[0]-P[0],uy=U[1]-P[1];ctx.save();ctx.transform(rx/im.width,ry/im.width,-ux/im.height,-uy/im.height,P[0]-rx/2+ux/2,P[1]-ry/2+uy/2);ctx.drawImage(im,0,0);ctx.restore()}
const cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]],unit=a=>mul(a,1/Math.hypot(...a));
function addRay(list,key,from,to,width){let delta=add(to,mul(from,-1)),length=Math.hypot(...delta);if(length<.03)return;let u=unit(delta),r=cross(u,[0,1,0]);if(Math.hypot(...r)<.01)r=[1,0,0];else r=unit(r);let n=unit(cross(r,u)),mid=mul(add(from,to),.5);for(let sign of[-1,1]){list.push(plane(key,add(mid,mul(n,sign*width/2)),r,u,width,length,0));list.push(plane(key,add(mid,mul(r,sign*width/2)),n,u,width,length,0))}}
const motePalette=['#fff4bd','#f6c4ff','#bda5ff','#9deeff','#9cffd0','#ffc2d1'];let tinted=[];
function tintMotes(){tinted=motePalette.map(color=>{let c=document.createElement('canvas');c.width=c.height=64;let x=c.getContext('2d');x.drawImage(images.mote,0,0,64,64);x.globalCompositeOperation='source-in';x.fillStyle=color;x.fillRect(0,0,64,64);return c})}
function drawMote(p){let q=project(p.p);ctx.save();ctx.globalAlpha=p.alpha;ctx.translate(...q);ctx.rotate(p.angle);ctx.shadowColor=motePalette[p.color];ctx.shadowBlur=4;ctx.drawImage(tinted[p.color],-p.size/2,-p.size/2,p.size,p.size);ctx.restore()}
function draw(){camera();ctx.clearRect(0,0,1280,820);let g=ctx.createLinearGradient(0,0,0,820);g.addColorStop(0,'#06091b');g.addColorStop(1,'#142336');ctx.fillStyle=g;ctx.fillRect(0,0,1280,820);ctx.lineWidth=1;for(let n=-40;n<=40;n+=2){line([n,0,-40],[n,0,40],n===0?'#364456':'#1d2b3e');line([-40,0,n],[40,0,n],n===0?'#364456':'#1d2b3e')}
let charge=time<1.5,age=(charge?time:time-1.5)*20,duration=charge?30:600,opening=charge?.16+.84*ease(age/duration):1+.06*Math.exp(-age/14),closing=charge?1:1-ease((age-(duration-28))/28),size=opening*closing,motionAge=charge?age:age+30,low=document.getElementById('quality').value==='low';let list=[],emit=(...a)=>list.push(plane(...a));
emit('sigil',[0,.10,0],[1,0,0],[0,0,1],14*size,14*size,motionAge*.024);emit('halo',[0,.18,0],[1,0,0],[0,0,1],26*size,26*size,-motionAge*.018);let sky=[0,32.10,0];emit('sigil',sky,[1,0,0],[0,0,1],21*size,21*size,-motionAge*.020);
if(!low)emit('halo',add(sky,[0,-.08,0]),[1,0,0],[0,0,1],25*size,25*size,motionAge*.028);
if(!charge)for(let i=0;i<(low?1:3);i++){let launched=age-i*4;if(launched<0)continue;let drop=Math.floor(launched/16),fallAge=launched%16;if(fallAge<.01)continue;let seed=drop*3+i,a=seed*2.39996322973+.37,radial=(seed*.61803398875+.31)%1,r=.8+4.8*Math.sqrt(radial),x=Math.cos(a)*r,z=Math.sin(a)*r,head=32*(1-Math.min(1,fallAge/12)),tail=Math.min(32,head+32*.22),width=(.70+.25*(.5+.5*Math.sin(seed*1.13)))*closing;addRay(list,'beacon',[x,.1+tail,z],[x,.1+head,z],width)}
for(let i of(low?[0,2]:[0,1,2,3])){let a=i*Math.PI/2+motionAge*.006,r=10.2*opening,spin=i%2===0?.032:-.028,since=((age-24-i*16)%64+64)%64,flash=!charge&&age>=24+i*16&&since<6;let pos=[Math.cos(a)*r,.10+7+2.5*i,Math.sin(a)*r];emit('sigil',pos,[1,0,0],[0,0,1],3.8*size*(flash?1.14:1),3.8*size*(flash?1.14:1),motionAge*spin);if(flash){let fire=age-since,phase=fire+30,theta=i*Math.PI/2+phase*.006,rad=10.2*(1+.06*Math.exp(-fire/14)),volley=Math.floor(fire/16),bearing=theta+.20*Math.sin(volley*1.7),range=20+2*Math.sin(volley*1.13);addRay(list,'shot',[Math.cos(theta)*rad,.10+7+2.5*i,Math.sin(theta)*rad],[Math.cos(bearing)*range,.16,Math.sin(bearing)*range],(.34+.18*(1-since/6))*closing)}}
let frame=Math.floor(motionAge/4),particleCount=low?12:24;for(let birth=frame-5;birth<=frame;birth++)for(let i=0;i<particleCount;i++){if(birth<0)continue;let life=.65+.4*((i*.381966+birth*.17)%1),elapsed=(motionAge-birth*4)/20;if(elapsed<0||elapsed>life)continue;let phase=(i*.61803398875+birth*4*.007)%1,a=i*2.39996322973+birth*4*.045+elapsed*.4,r=14*size*(.11+.28*Math.sin(phase*Math.PI)),p=[Math.cos(a)*r,.35+19.2*phase+elapsed*.42,Math.sin(a)*r];list.push({type:'mote',p,depth:dot(p,eye),color:((i+birth)%6+6)%6,size:8+4*(i%11)/10,alpha:opening*closing*Math.sin(Math.PI*elapsed/life),angle:i+elapsed*2})}
list.sort((a,b)=>a.depth-b.depth);for(let p of list){if(p.type==='mote')drawMote(p);else paint(p)}ctx.fillStyle='#e9d9b6';ctx.font='15px system-ui';ctx.fillText('HEAVEN’S DESCENT',28,36);ctx.fillStyle='#9faec8';ctx.font='13px system-ui';ctx.fillText('Falling light rain / orbiting volley / coloured motes',28,60);status.textContent=`${time.toFixed(1)}s · ${charge?'ร่ายเวท':time>30.1?'สลายตัว':'ฝนแสงและวงเวทยิง'}`;
}
document.getElementById('play').onclick=()=>{running=!running;document.getElementById('play').textContent=running?'พัก':'เล่น'};slider.oninput=()=>{time=+slider.value;draw()};document.getElementById('view').onchange=draw;document.getElementById('quality').onchange=draw;
Promise.all(loading).then(()=>{tintMotes();document.getElementById('play').textContent=running?'พัก':'เล่น';requestAnimationFrame(function loop(now){let dt=last?(now-last)/1000:0;last=now;if(running){time=(time+Math.min(dt,.1))%31.5;slider.value=time}draw();requestAnimationFrame(loop)})});
</script></html>'''


if __name__ == '__main__':
    main()
