"""Build a local review gallery from verified generated-block previews."""
from pathlib import Path
import html
import json

DESTINATIONS = [
    ('สวน', 'orchard-estate', 'สวนผลไม้และโรงคั้น', 'เครื่องคั้นผลไม้ ครัวคนงาน ลานบรรจุของ แปลงเพาะ และชานพักใต้ต้นซากุระ', 6),
    ('สวน', 'rose-conservatory', 'เรือนเพาะกุหลาบ', 'โดมกระจก เรือนปีกสองข้าง โต๊ะเพาะกล้า อ่างล้าง กระถาง และชานจิบชา', 5),
    ('สวน', 'garden-watermill', 'โรงสีและรางส่งน้ำ', 'กังหันกับเพลาบดข้าว ชั้นเก็บผลผลิต รางน้ำบนซุ้มหิน และลานซ่อมเครื่องมือ', 5),
    ('สวน', 'travelers-inn', 'จุดพักคาราวาน', 'รถเกวียนมีล้อและที่นอน โรงเตี๊ยมเล็ก ครัว โต๊ะอาหาร จุดล้าง และเพิงซ่อม', 6),
    ('สวน', 'hedge-sanctuary', 'สวนทางคดเคี้ยว', 'ทางพุ่มไม้หลายแขนง นาฬิกาแดด มุมนั่งพัก สระเล็ก และซุ้มปลูกดอกไม้', 5),
    ('ธารน้ำแข็ง', 'alpine-lodge', 'ที่พักทางผ่านภูเขา', 'สะพานข้ามโขดหิน ห้องพักอุ่นพร้อมครัว จุดซ่อมอุปกรณ์ และค่ายด้านล่าง', 5),
    ('ธารน้ำแข็ง', 'frozen-bathhouse', 'บ่อน้ำพุร้อนเป็นชั้น', 'สระหลายระดับ ห้องเปลี่ยนเสื้อทรงกลม ช่องไอน้ำ มุมล้าง และชานพัก', 5),
    ('ธารน้ำแข็ง', 'icebound-outpost', 'ป้อมเฝ้าทางน้ำแข็ง', 'หอเฝ้าสามระดับ ซุ้มประตู กองทหาร จุดฝึกเป้า ธง และลานช่าง', 6),
    ('ธารน้ำแข็ง', 'glacier-mine', 'เหมืองเปิดธารน้ำแข็ง', 'หลุมเหมืองลดระดับ อุโมงค์ค้ำไม้ รางขนแร่ เครนแขวน และโต๊ะคัดแร่', 6),
    ('ธารน้ำแข็ง', 'aurora-hall', 'วงหินแสงเหนือ', 'เสาหินจัดแนวรัศมี แท่นพิธีสูง แผนที่ ค่ายพัก และซากช่องเก็บเสบียง', 5),
    ('ป่าโบราณ', 'woodland-manor', 'ซากคฤหาสน์ในป่า', 'ครัวกับโต๊ะงานเลี้ยงที่หลงเหลือ คานถล่ม เถาไม้ เรือนเพาะ และห้องใต้ดิน', 6),
    ('ป่าโบราณ', 'canopy-refuge', 'ชานพักบนเรือนยอด', 'ต้นไม้แตกกิ่ง ชานหลายจุด สะพานเชือก บันได ห้องชาเปิด และโต๊ะช่าง', 5),
    ('ป่าโบราณ', 'mushroom-hamlet', 'ค่ายเก็บเห็ด', 'โรงอบในเห็ดหนึ่งหลัง ท่อนไม้กลวงเดินผ่านได้ เห็ดป่า แผงผลผลิต และกองไฟ', 5),
    ('ป่าโบราณ', 'overgrown-aqueduct', 'ทางส่งน้ำโบราณ', 'ซุ้มหินเจ็ดช่วง ทางตรวจรางน้ำชั้นบน อ่างรับน้ำ และค่ายขุดสำรวจ', 5),
    ('ป่าโบราณ', 'rootbound-shrine', 'ศาลเจ้าในรากไม้', 'โพรงเดินผ่านลำต้น รากค้ำทาง สวนพิธี อ่างปรุงยา และซุ้มหินผุ', 5),
    ('นครจันทรา', 'moon-market', 'ตลาดจันทรา', 'แผงยา ช่าง อาหาร และพืช มีผ้าใบต่างสี พร้อมชานบรรจุของชั้นบน', 6),
    ('นครจันทรา', 'astral-monastery', 'ลานอารามดวงดาว', 'แนวซุ้มเปิด ห้องคัดลอกหนังสือขนาดเล็ก แท่นพิธี ชานระฆัง และครัว', 5),
    ('นครจันทรา', 'tide-observatory', 'หอดูดาวจันทรา', 'กล้องดูดาวทองแดง ฐานเครื่องมือ โต๊ะแผนที่ ระเบียงวงแหวน และลานช่าง', 6),
    ('นครจันทรา', 'fallen-amphitheatre', 'โรงละครจันทราเก่า', 'อัฒจันทร์โค้ง เครื่องดนตรี ม่านเวที ห้องแต่งตัว และห้องเก็บอุปกรณ์ใต้ดิน', 5),
    ('นครจันทรา', 'lunar-bath-garden', 'สวนสระเสี้ยวจันทร์', 'สระรูปเสี้ยว ศาลาทองแดง แนวซุ้มโค้ง มุมซักผ้า และสวนกระถาง', 5),
]


def main():
    folder=Path('previews/exploration-redesign')
    for _,kind,_,_,_ in DESTINATIONS:
        for image in [folder/f'{kind}.png', folder/'interiors'/f'{kind}.png', Path('previews/medium-destinations')/f'{kind}.png']:
            if not image.is_file(): raise FileNotFoundError(image)
    cards=[]
    for biome,kind,title,description,chests in DESTINATIONS:
        cards.append(f'''<article data-biome="{biome}"><button class="picture" aria-label="ขยายภาพ {title}"><img loading="lazy" data-kind="{kind}" src="{kind}.png" alt="{title}"></button>
        <div class="caption"><small>{biome} · {chests} กล่อง</small><h2>{title}</h2><p>{description}</p><code>/evergarden tp {kind}</code></div></article>''')
    page='''<!doctype html><html lang="th"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Evergarden — สำรวจแบบใหม่ 20 แห่ง</title><style>
*{box-sizing:border-box}body{margin:0;background:#edf1f3;color:#263844;font:16px/1.65 system-ui,sans-serif}main{max-width:1450px;margin:auto;padding:35px 24px}h1{font-size:32px;line-height:1.35;margin:10px 0}h2{font-size:22px;margin:4px 0}p{margin:8px 0;color:#526573}.intro{max-width:940px}.eyebrow,small{color:#617b79}.controls{display:flex;gap:20px;flex-wrap:wrap;position:sticky;top:0;padding:16px 0;background:#edf1f3;z-index:1}.group{display:flex;gap:6px;flex-wrap:wrap}button{font:inherit;cursor:pointer;border:1px solid #b6c7c6;background:white;color:#2e4a49;border-radius:7px;padding:7px 14px}button[aria-pressed=true]{background:#335c55;color:white;border-color:#335c55}.grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:25px}article{background:white;border:1px solid #d4dfdf;border-radius:12px;overflow:hidden}.picture{display:block;padding:0;width:100%;border:0;border-radius:0}.picture img{display:block;width:100%;height:auto}.caption{padding:16px 22px 24px}code{font-size:13px;color:#335c55;overflow-wrap:anywhere}article[hidden]{display:none}dialog{max-width:94vw;max-height:96vh;padding:10px;border:0;border-radius:10px}dialog img{display:block;max-width:100%;max-height:84vh;object-fit:contain}dialog::backdrop{background:#122c35c9}.close{display:block;margin:8px 0 0 auto}.note{font-size:14px;color:#637681;margin-bottom:20px}@media(max-width:760px){.grid{grid-template-columns:1fr}main{padding:20px 14px}h1{font-size:27px}.controls{position:static;gap:12px}}
</style><main><div class="eyebrow">EVERGARDEN · e2.58</div><h1>สถานที่สำรวจ 20 แห่ง ออกแบบผังใหม่</h1><p class="intro">ลดอาคารหลังใหญ่ เพิ่มสถานที่กลางแจ้งและทางสำรวจหลายระดับ ห้องที่เก็บไว้มีครัว โต๊ะทำงาน เครื่องมือ ที่พัก และพื้นที่เก็บของตามการใช้งาน แต่ละแห่งมีกล่อง 5–6 จุด ใช้คุณภาพสมบัติเดิม</p>
<div class="controls"><div class="group" id="views"><button data-view="exterior" aria-pressed="true">ภายนอก</button><button data-view="interior" aria-pressed="false">ดูภายใน</button><button data-view="previous" aria-pressed="false">เทียบรุ่นก่อน</button></div><div class="group" id="biomes"><button data-biome="all" aria-pressed="true">ทุกไบโอม</button><button data-biome="สวน" aria-pressed="false">สวน</button><button data-biome="ธารน้ำแข็ง" aria-pressed="false">น้ำแข็ง</button><button data-biome="ป่าโบราณ" aria-pressed="false">ป่า</button><button data-biome="นครจันทรา" aria-pressed="false">จันทรา</button></div></div>
<p class="note">ภาพเรนเดอร์จากบล็อกที่สร้างจริง ใช้ texture Minecraft และแสงจำลอง ภาพภายในซ่อนส่วนบนเพื่อให้ตรวจผังได้ กดภาพเพื่อขยาย แบบใหม่เกิดในพื้นที่ใหม่หลังอัปเกรด</p><div class="grid">CARDS</div></main><dialog><img alt="ภาพโครงสร้างขยาย"><button class="close">ปิดภาพ</button></dialog>
<script>const dialog=document.querySelector('dialog');document.querySelector('#views').addEventListener('click',e=>{const b=e.target.closest('[data-view]');if(!b)return;document.querySelectorAll('#views button').forEach(x=>x.setAttribute('aria-pressed',x===b));document.querySelectorAll('.picture img').forEach(img=>{const kind=img.dataset.kind;img.src=b.dataset.view==='interior'?'interiors/'+kind+'.png':b.dataset.view==='previous'?'../medium-destinations/'+kind+'.png':kind+'.png';});});document.querySelector('#biomes').addEventListener('click',e=>{const b=e.target.closest('[data-biome]');if(!b)return;document.querySelectorAll('#biomes button').forEach(x=>x.setAttribute('aria-pressed',x===b));document.querySelectorAll('article').forEach(x=>x.hidden=b.dataset.biome!=='all'&&x.dataset.biome!==b.dataset.biome);});document.querySelectorAll('.picture').forEach(b=>b.addEventListener('click',()=>{const source=b.querySelector('img'),target=dialog.querySelector('img');target.src=source.src;target.alt=source.alt;dialog.showModal();}));document.querySelector('.close').addEventListener('click',()=>dialog.close());dialog.addEventListener('click',e=>{if(e.target===dialog)dialog.close();});</script></html>'''.replace('CARDS','\n'.join(cards))
    (folder/'index.html').write_text(page,encoding='utf-8')
    (folder/'destinations.json').write_text(json.dumps([dict(biome=b,kind=k,title=t,description=d,chests=c) for b,k,t,d,c in DESTINATIONS],ensure_ascii=False,indent=2),encoding='utf-8')
    print(folder/'index.html')


if __name__=='__main__':main()
