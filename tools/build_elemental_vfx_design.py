"""Assemble the 58-spell visual design into a source-checked spec and offline catalog."""
from pathlib import Path
import collections
import html
import json
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PARTS = ROOT / 'docs/vfx'
FIELDS = ['Spell Name', 'Element', 'Combat Role', 'Visual Goal', 'Cast Phase',
          'Travel / Active Phase', 'Impact Phase', 'Lingering Effect (if any)',
          'Suggested Particles', 'Suggested Sounds', 'Camera / readability notes',
          'Performance-safe version', 'Why this looks better than a basic effect']
ELEMENTS = ['Fire', 'Water', 'Ice', 'Wind', 'Earth', 'Light', 'Dark', 'Lightning']
PALETTES = {
    'Fire': ['#FF6A16', '#C42A16', '#FFC44A'], 'Water': ['#168EC4', '#53D8E8', '#9BE9CF'],
    'Ice': ['#A8E8FF', '#4B88C7', '#EDF9FF'], 'Wind': ['#DFFFF3', '#82DCC5', '#A9BDC4'],
    'Earth': ['#776A54', '#A7C65A', '#C4AC77'], 'Light': ['#FFD77A', '#FFF7DE', '#CFFFEA'],
    'Dark': ['#7954B3', '#21152F', '#C56BCE'], 'Lightning': ['#8EBEFF', '#EEF7FF', '#4B58A3'],
}


def sections(content):
    return re.findall(r'^### ([^\n]+)\n(.*?)(?=^## |^### |\Z)', content, re.M | re.S)


def properties(content):
    return dict(re.findall(r'^- \*\*(.+?):\*\*\s*(.+)$', content, re.M))


def inline(content):
    value = html.escape(content)
    value = re.sub(r'`([^`]+)`', r'<code>\1</code>', value)
    return re.sub(r'\*\*([^*]+)\*\*', r'<strong>\1</strong>', value)


def markdown(content):
    """Small safe formatter for the fixed standalone design sections."""
    result, table = [], []
    def flush():
        if table:
            rows = [r.strip().strip('|').split('|') for r in table]
            result.append('<div class="table-wrap"><table><thead><tr>' + ''.join('<th>'+inline(x.strip())+'</th>' for x in rows[0])+'</tr></thead><tbody>')
            for row in rows[2:]: result.append('<tr>'+''.join('<td>'+inline(x.strip())+'</td>' for x in row)+'</tr>')
            result.append('</tbody></table></div>'); table.clear()
    for line in content.splitlines():
        if line.startswith('|'): table.append(line); continue
        flush()
        if not line.strip(): continue
        heading = re.match(r'^(#{1,6}) (.+)', line)
        if heading:
            level = min(4, len(heading[1]) + 1)
            result.append(f'<h{level}>'+inline(heading[2])+f'</h{level}>')
        elif line.startswith('- '): result.append('<p class="list-item">• '+inline(line[2:])+'</p>')
        else: result.append('<p>'+inline(line)+'</p>')
    flush(); return '\n'.join(result)


catalog_source = (ROOT/'advance-magic/src/main/java/com/example/advancemagic/elemental/ElementalSpell.java').read_text(encoding='utf8')
rows = re.findall(r'^\s*(\w+)\("([^"]+)",Element\.(\w+),(\d+),([\d.]+),([\d.]+),Rarity\.(\w+),(true|false),', catalog_source, re.M)
if len(rows) != 58: raise ValueError('Source catalog changed: expected 58 spells')
drafts, guides = {}, {}
for name in ('fire-water.md', 'ice-wind-earth.md', 'light-dark-lightning.md'):
    for title, body in sections((PARTS/name).read_text(encoding='utf8')):
        props = properties(body)
        if 'Spell Name' in props:
            if title in drafts: raise ValueError('Duplicate spell: '+title)
            if props['Spell Name'] != title or set(props) != set(FIELDS):
                raise ValueError('Missing or extra fields: '+title+' '+str(set(FIELDS)-set(props)))
            drafts[title] = props
        elif title in ELEMENTS:
            guides[title] = {'title': title, 'body': body.strip(), 'properties': props, 'palette': PALETTES[title]}

spells = []
for spell_id, title, element, mana, cooldown, multiplier, rarity, ultimate in rows:
    if title not in drafts: raise ValueError('Missing spell: '+title)
    data = drafts[title]
    if data['Element'] != element.title(): raise ValueError('Wrong element: '+title)
    spells.append({'id':spell_id.lower(), 'name':title, 'element':element.title(), 'mana':int(mana),
                   'cooldown':float(cooldown), 'rarity':rarity,
                   'tier':'Ultimate' if ultimate=='true' else 'Basic' if rarity=='BASIC' else 'Advanced',
                   'fields':data})
if set(drafts) != {s['name'] for s in spells} or set(guides) != set(ELEMENTS):
    raise ValueError('Catalog or style guide coverage mismatch')

global_text = (PARTS/'global-direction.md').read_text(encoding='utf8')
closing = (PARTS/'performance-priority.md').read_text(encoding='utf8')
master = [global_text.rstrip()]
master += ['\n### '+name+'\n\n'+guides[name]['body'] for name in ELEMENTS]
master.append('\n## 3. Redesigned spell effects — ทั้ง 58 เวท\n\nทุกเวทระบุครบ13หัวข้อ พร้อมแบบลดรายละเอียด คำว่าแผ่นภาพในบทนี้หมายถึงassetที่ต้องสร้าง/ส่งจริงตามrendererในหัวข้อ1 ไม่ใช่บล็อกหรือhitboxใหม่\n')
master.append('| ธาตุ | จำนวนเวท |\n| --- | ---: |\n'+'\n'.join(f'| {name} | {sum(s["element"]==name for s in spells)} |' for name in ELEMENTS))
for index, spell in enumerate(spells, 1):
    master.append(f'\n### {index:02d}. {spell["name"]}\n\n'+ '\n'.join('- **'+field+':** '+spell['fields'][field] for field in FIELDS))
master.append('\n'+closing)
spec = ROOT/'docs/elemental-vfx-redesign.md'
spec.write_text('\n\n'.join(master)+'\n',encoding='utf8')
catalog = {'version':'1.0', 'scope':'Visual and sound design; primary renderer hooks implemented in plugin 1.15.0', 'fields':FIELDS,
           'elementOrder':ELEMENTS, 'elements':[guides[name] for name in ELEMENTS], 'spells':spells}
catalog_path=PARTS/'elemental-vfx-catalog.json'
catalog_path.write_text(json.dumps(catalog,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
notes, plan = closing.split('## 5. Implementation priority plan',1)
global_overview = global_text.split('## 2. Element style guide')[0]
styles_html = ''.join('<article class="style-card" style="--accent:'+guides[name]['palette'][0]+'"><div class="style-top"><h3>'+name+'</h3><div class="palette">'+''.join('<span style="background:'+c+'" title="'+c+'"></span>' for c in guides[name]['palette'])+'</div></div>'+markdown(guides[name]['body'])+'</article>' for name in ELEMENTS)
TEMPLATE = r'''<!doctype html>
<html lang="th"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Elemental Magic · 58 Spell VFX Design</title><style>
:root{color-scheme:dark;--bg:#11151c;--panel:#1a202b;--border:#313c4d;--text:#e9edf4;--muted:#aab6c7;--gold:#e5c086;--accent:#e5c086}*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font:16px/1.65 "Segoe UI",Tahoma,sans-serif}header,main{max-width:1380px;margin:auto;padding:28px}header{padding-bottom:14px}.eyebrow{color:var(--gold);font:12px/1.5 ui-monospace,monospace;letter-spacing:1.5px}h1{font-size:clamp(28px,4vw,48px);line-height:1.25;margin:14px 0}h2{font-size:24px}h3{font-size:20px}h4{font-size:18px}p{max-width:90ch}.muted,small{color:var(--muted)}nav{max-width:1380px;margin:auto;padding:0 28px;display:flex;flex-wrap:wrap;gap:8px}button,select,input{font:inherit;color:var(--text);background:var(--panel);border:1px solid var(--border);border-radius:8px;padding:9px 14px}button{cursor:pointer}button:hover,button[aria-selected="true"]{border-color:var(--gold);background:#292a2a}input:focus,select:focus,button:focus-visible{outline:2px solid var(--gold);outline-offset:2px}button.tab{min-height:44px}.page[hidden]{display:none}.page{padding-top:12px}.table-wrap{overflow-x:auto;margin:20px 0}table{border-collapse:collapse;width:100%;font-size:14px}th,td{text-align:left;vertical-align:top;padding:12px;border:1px solid var(--border)}th{background:#252b36}code{color:#c5e5ff;background:#252d3b;padding:1px 4px;border-radius:4px;overflow-wrap:anywhere;font-size:13px}.list-item{padding-left:12px}a{color:#a8d9ff}.style-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(340px,1fr));gap:20px}.style-card{background:var(--panel);border:1px solid var(--border);border-top:3px solid var(--accent);padding:22px;border-radius:12px}.style-top{display:flex;align-items:center;justify-content:space-between}.style-top h3{margin:0;color:var(--accent)}.palette{display:flex;gap:5px}.palette span{width:25px;height:25px;border-radius:5px;border:1px solid #ffffff22}.style-card p{font-size:14px}.filters{display:grid;grid-template-columns:2fr 1fr 1fr;gap:12px;margin:12px 0}.filters label{font-size:13px;color:var(--muted)}.filters input,.filters select{display:block;width:100%;margin-top:4px}.catalog{display:grid;grid-template-columns:310px 1fr;gap:22px;align-items:start}#spell-list{display:flex;flex-direction:column;gap:7px;max-height:76vh;overflow-y:auto;padding:2px 6px 2px 2px;position:sticky;top:10px}.spell-option{text-align:left;border-left:3px solid var(--accent);padding:10px 12px;display:block;width:100%}.spell-option b{display:block;font-size:15px}.spell-option small{font-size:12px}.spell-option.active{background:#2b3340;border-color:var(--accent)}#spell-detail{background:var(--panel);border:1px solid var(--border);border-top:4px solid var(--accent);padding:26px;border-radius:12px;min-height:70vh}#spell-detail h2{margin:0 0 8px;font-size:28px}.tag{display:inline-block;border:1px solid var(--border);border-radius:6px;padding:2px 9px;color:var(--muted);font-size:12px;margin-right:6px}.field{border-top:1px solid var(--border);padding:14px 0 0;margin-top:16px}.field h3{font-size:13px;color:var(--accent);margin:0 0 5px;letter-spacing:.3px}.field p{margin:0}.field.phase{border-left:2px solid var(--accent);padding-left:14px}.scope{padding:12px 16px;background:#1b2525;border-left:3px solid #9ac6af;border-radius:6px}.counter{margin:8px 0 18px;font-size:13px;color:var(--muted)}footer{padding:20px 28px 40px;text-align:center;color:var(--muted);font-size:12px}@media(max-width:750px){header,main{padding:20px}nav{padding:0 20px}.filters{grid-template-columns:1fr 1fr}.filters label:first-child{grid-column:1/-1}.catalog{grid-template-columns:1fr}#spell-list{position:static;max-height:230px;border-bottom:1px solid var(--border);padding-bottom:15px}.style-grid{grid-template-columns:1fr}#spell-detail{padding:18px}}@media print{nav,.filters,#spell-list{display:none}.page[hidden]{display:block}.catalog{display:block}body{background:white;color:black}}
</style></head><body><header><div class="eyebrow">ADVANCE MAGIC · VISUAL DESIGN SPECIFICATION V1.0</div><h1>58 เวท · 8 ภาษาภาพ</h1><p class="muted">รูปทรงที่จำได้ • จังหวะตรงการต่อสู้ • Java และ Bedrock ผ่าน Geyser</p><p class="scope">สเปกภาพต้นทาง · เชื่อม renderer รุ่น 1.15.0 แล้ว · ขอบเขตรุ่นใช้งานดู elemental-vfx-release.md</p></header>
<nav aria-label="ส่วนเอกสาร"><button class="tab" data-page="overview">1 ภาพรวม</button><button class="tab" data-page="elements">2 ภาษาธาตุ</button><button class="tab" data-page="spells">3 เวททั้ง 58</button><button class="tab" data-page="performance">4 ประสิทธิภาพ</button><button class="tab" data-page="plan">5 ลำดับลงโค้ด</button></nav>
<main><section id="overview" class="page">__OVERVIEW__</section><section id="elements" class="page" hidden><h2>Element style guide</h2><div class="style-grid">__STYLES__</div></section>
<section id="spells" class="page" hidden><h2>Redesigned spell effects</h2><div class="filters"><label>ค้นหาชื่อ บทบาท หรือคำในแบบ<input id="search" type="search" placeholder="เช่น Frost, shield, รอยแตก" autocomplete="off"></label><label>ธาตุ<select id="element"><option value="">ทั้งหมด</option></select></label><label>Visual tier<select id="tier"><option value="">ทั้งหมด</option><option>Basic</option><option>Advanced</option><option>Ultimate</option></select></label></div><div class="counter" id="count" role="status"></div><div class="catalog"><div id="spell-list" aria-label="รายชื่อเวท"></div><article id="spell-detail" aria-live="polite"></article></div></section>
<section id="performance" class="page" hidden>__PERFORMANCE__</section><section id="plan" class="page" hidden>__PLAN__</section></main><footer>Source of truth: ElementalSpell.java · ElementalSpellService.java · Shared Mythic plane/particle renderer · Offline catalog, no network requests</footer>
<script id="catalog-data" type="application/json">__DATA__</script><script>
const data=JSON.parse(document.getElementById('catalog-data').textContent);const $=id=>document.getElementById(id);let selected=data.spells[0].id;
const palettes=Object.fromEntries(data.elements.map(e=>[e.title,e.palette]));
function activate(page){document.querySelectorAll('.page').forEach(x=>x.hidden=x.id!==page);document.querySelectorAll('.tab').forEach(b=>b.setAttribute('aria-selected',String(b.dataset.page===page)));}
document.querySelectorAll('.tab').forEach(b=>b.addEventListener('click',()=>activate(b.dataset.page)));
data.elementOrder.forEach(name=>{const o=document.createElement('option');o.value=o.textContent=name;$('element').append(o)});
function esc(s){return s.replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;')}
function fmt(s){return esc(s).replace(/`([^`]+)`/g,'<code>$1</code>').replace(/\*\*([^*]+)\*\*/g,'<strong>$1</strong>')}
function detail(s){const article=$('spell-detail');article.style.setProperty('--accent',palettes[s.element][0]);article.innerHTML='<h2>'+esc(s.name)+'</h2><p><span class="tag">'+s.element+'</span><span class="tag">'+s.tier+'</span><span class="tag">'+s.mana+' MP · '+s.cooldown+' s CD</span></p><p class="muted"><code>'+s.id+'</code> · ค่าจาก catalog เดิม; ไม่มีการเปลี่ยนพลังในแบบภาพนี้</p>'+data.fields.map(f=>'<section class="field '+(['Cast Phase','Travel / Active Phase','Impact Phase'].includes(f)?'phase':'')+'"><h3>'+esc(f)+'</h3><p>'+fmt(s.fields[f])+'</p></section>').join('');}
function render(){const q=$('search').value.toLocaleLowerCase(),el=$('element').value,tier=$('tier').value;const found=data.spells.filter(s=>(!el||s.element===el)&&(!tier||s.tier===tier)&&(!q||Object.values(s.fields).join(' ').toLocaleLowerCase().includes(q)||s.id.includes(q)));$('count').textContent=found.length+' / 58 เวท · รายละเอียดครบ 13 หัวข้อ พร้อมแบบ Performance-safe';if(!found.some(s=>s.id===selected))selected=found[0]?.id;const list=$('spell-list');list.replaceChildren();for(const s of found){const b=document.createElement('button');b.className='spell-option'+(s.id===selected?' active':'');b.style.setProperty('--accent',palettes[s.element][0]);b.setAttribute('aria-pressed',String(s.id===selected));b.innerHTML='<b>'+esc(s.name)+'</b><small>'+s.element+' · '+s.tier+'</small>';b.addEventListener('click',()=>{selected=s.id;render()});list.append(b)}const s=found.find(x=>x.id===selected);if(s)detail(s);else $('spell-detail').textContent='ไม่พบเวทตามตัวกรอง ลองเปลี่ยนคำค้นหรือธาตุ';}
['search','element','tier'].forEach(id=>$(id).addEventListener('input',render));activate('overview');render();
</script></body></html>'''
page=TEMPLATE.replace('__OVERVIEW__',markdown(global_overview)).replace('__STYLES__',styles_html)
page=page.replace('__PERFORMANCE__',markdown(notes)).replace('__PLAN__',markdown('## 5. Implementation priority plan'+plan))
page=page.replace('__DATA__',json.dumps(catalog,ensure_ascii=False).replace('<','\\u003c').replace('>','\\u003e'))
preview=ROOT/'previews/elemental-vfx-design.html'; preview.parent.mkdir(exist_ok=True)
preview.write_text(page,encoding='utf8')
bundle=ROOT/'dist/elemental-vfx-design-spec.zip'
with zipfile.ZipFile(bundle,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for path in (spec,preview,catalog_path): z.write(path,path.name)
print(json.dumps({'spells':len(spells),'fields_per_spell':len(FIELDS),
                  'by_element':dict(collections.Counter(s['element'] for s in spells)),
                  'tiers':dict(collections.Counter(s['tier'] for s in spells)),
                  'markdown':str(spec),'interactive_catalog':str(preview),'bundle':str(bundle)},ensure_ascii=False,indent=2))
