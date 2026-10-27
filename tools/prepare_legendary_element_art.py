"""Package native ImageGen textures and create a review gallery (no game edits)."""
from pathlib import Path
import base64
import hashlib
import html
import io
import json
import zipfile
from PIL import Image, ImageDraw, ImageFont, ImageOps

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'advance-magic/art/effects/legendary-elements-v1'
PREVIEW = ROOT / 'previews/legendary-elements-v1'
ELEMENTS = [
    ('fire', 'FIRE', 'ไฟ', 'Meteor Rain', 'meteor_rain', 'meteor', '#ff9c44'),
    ('water', 'WATER', 'น้ำ', "Leviathan's Wrath", 'leviathans_wrath', 'leviathan', '#59dbff'),
    ('ice', 'ICE', 'น้ำแข็ง', 'Absolute Zero', 'absolute_zero', 'crystal', '#b6e9ff'),
    ('wind', 'WIND', 'ลม', 'Tempest Dominion', 'tempest_dominion', 'vortex', '#a4f5d7'),
    ('earth', 'EARTH', 'ดิน', "Titan's Judgment", 'titans_judgment', 'impact', '#f3cc80'),
    ('dark', 'DARK', 'มืด', 'Abyssal Collapse', 'abyssal_collapse', 'core', '#cb93ff'),
    ('lightning', 'LIGHTNING', 'สายฟ้า', 'Wrath of Raijin', 'wrath_of_raijin', 'drums', '#95bfff'),
]


def thumb_data(image):
    preview = ImageOps.contain(image, (640, 640), Image.Resampling.LANCZOS)
    stream = io.BytesIO()
    preview.save(stream, 'PNG', compress_level=2)
    return 'data:image/png;base64,' + base64.b64encode(stream.getvalue()).decode('ascii')


def main():
    PREVIEW.mkdir(parents=True, exist_ok=True)
    title_font = ImageFont.truetype('C:/Windows/Fonts/seguisb.ttf', 28)
    small_font = ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf', 19)
    board = Image.new('RGB', (2340, 2180), '#080c17')
    draw = ImageDraw.Draw(board)
    draw.text((54, 30), 'LEGENDARY ELEMENTS / ORIGINAL TEXTURE COLLECTION', font=title_font, fill='#f0debb')
    draw.text((54, 76), '7 elements / 14 native RGBA assets / existing Ancient Judge hand reused for Earth', font=small_font, fill='#a4b3cc')
    cards, catalog = [], []
    for index, (folder, label, thai, spell, prefix, hero, accent) in enumerate(ELEMENTS):
        x, y = 30 + (index % 3) * 770, 130 + (index // 3) * 660
        draw.rounded_rectangle((x, y, x + 750, y + 630), 18, fill='#101827', outline='#29364c', width=2)
        draw.text((x + 24, y + 22), label + ' / ' + spell.upper(), font=title_font, fill=accent)
        parts, records = [], []
        for position, suffix in enumerate(('sigil', hero)):
            path = ART / folder / (prefix + '_' + suffix + '.png')
            with Image.open(path) as opened:
                image = opened.copy()
            # Inspection only; selected production files remain exactly as generated.
            if image.mode != 'RGBA':
                raise ValueError('Native RGBA image required: ' + str(path))
            alpha = image.getchannel('A')
            if alpha.getextrema()[0] != 0 or alpha.getextrema()[1] == 0:
                raise ValueError('Actual transparent artwork required: ' + str(path))
            stats = alpha.histogram()
            records.append({
                'file': path.relative_to(ART).as_posix(), 'role': suffix,
                'dimensions': list(image.size), 'mode': image.mode,
                'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
                'transparent_fraction': stats[0] / (image.width * image.height),
                'alpha_bbox': list(alpha.getbbox()),
                'suggested_plane': 'horizontal XZ' if suffix in ('sigil', 'impact') else 'world-facing effect plane',
                'bedrock_uv': {'texture_width': image.width, 'texture_height': image.height,
                               'uv': [0, 0], 'uv_size': list(image.size)},
            })
            preview_image = ImageOps.contain(image, (345, 460), Image.Resampling.LANCZOS)
            px = x + 20 + position * 365 + (345 - preview_image.width) // 2
            py = y + 90 + (460 - preview_image.height) // 2
            board.paste(preview_image, (px, py), preview_image)
            draw.text((x + 28 + position * 365, y + 565), suffix.upper() + ' / ' + str(image.width) + ' x ' + str(image.height), font=small_font, fill='#c2cee1')
            relative = '../../advance-magic/art/effects/legendary-elements-v1/' + records[-1]['file']
            parts.append('<a class="asset" href="' + relative + '" target="_blank"><img src="' + thumb_data(image)
                         + '" alt="' + html.escape(spell + ' ' + suffix) + '"><span>' + suffix.upper()
                         + ' · ' + str(image.width) + ' × ' + str(image.height) + ' · PNG</span></a>')
        if folder == 'earth':
            draw.text((x + 28, y + 598), 'Hand: existing Ancient Judge model', font=small_font, fill='#f3cc80')
        cards.append('<article style="--accent:' + accent + '"><div class="label">' + label + ' · ' + thai
                     + '</div><h2>' + html.escape(spell) + '</h2><div class="assets">' + ''.join(parts)
                     + '</div>' + ('<p>ใช้โมเดลมือ Ancient Judge เดิม</p>' if folder == 'earth' else '') + '</article>')
        catalog.append({'element': folder, 'spell': prefix, 'textures': records,
                        'prompt_manifest': folder + '/manifest.json',
                        'reuse_model_reference': 'earth/boss-hand-reuse.json' if folder == 'earth' else None})
    hand = PREVIEW / 'boss-hand-reuse.png'
    hand_html = ''
    if hand.is_file():
        with Image.open(hand) as image:
            hand_html = '<section class="hand"><div><div class="label">EARTH · EXISTING MODEL</div><h2>มือบอสที่นำกลับมาใช้</h2><p>โมเดลจริงของ Ancient Judge จาก boss-temple<br>ภาพนี้เรนเดอร์จาก geometry และ atlas เดิม ไม่ใช่ภาพจากเกม</p></div><a href="boss-hand-reuse.png" target="_blank"><img src="' + thumb_data(image) + '" alt="Existing Ancient Judge hand"></a></section>'
        small = ImageOps.contain(Image.open(hand), (710, 575), Image.Resampling.LANCZOS)
        board.paste(small, (815 + (710-small.width)//2, 1450), small if small.mode == 'RGBA' else None)
    draw.text((1585, 1500), 'SOURCE ART ONLY', font=title_font, fill='#f0debb')
    for n, line in enumerate(['Native PNG pixels preserved', 'True transparent RGBA', 'Full prompts + manifests included', 'Java / Bedrock UV sizes catalogued', 'Gameplay integration follows separately']):
        draw.text((1585, 1570 + n * 42), line, font=small_font, fill='#a4b3cc')
    board.save(PREVIEW / 'texture-board.jpg', quality=94, subsampling=0)
    page = '''<!doctype html><html lang="th"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Legendary · Seven Elements</title><style>
*{box-sizing:border-box}body{margin:0;background:#080c17;color:#e9edf5;font:16px/1.65 system-ui,sans-serif}main{max-width:1540px;margin:auto;padding:36px 24px}.label{color:var(--accent,#efd7a3);font-size:12px;letter-spacing:2px}h1{font:500 44px Georgia,serif;margin:12px 0}h2{font:500 25px Georgia,serif;margin:8px 0 20px}header p,.hand p{color:#a6b4cc}.toolbar{display:flex;flex-wrap:wrap;gap:12px;margin:24px 0}button,.download{color:#eadcb8;background:#18233a;border:1px solid #344561;border-radius:8px;padding:9px 16px;text-decoration:none;font:inherit;cursor:pointer}.grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:22px}article,.hand{border:1px solid #27364d;background:#101827;border-radius:16px;padding:24px}.assets{display:grid;grid-template-columns:1fr 1fr;gap:12px}.asset{display:block;color:#b5c3db;text-decoration:none;font-size:11px}.asset img{width:100%;aspect-ratio:1;object-fit:contain;background:#080e1b;border-radius:9px}.asset span{display:block;margin-top:8px}article p{font-size:13px;color:#e4c68b}.checker .asset img{background-color:#1b2538;background-image:linear-gradient(45deg,#27344b 25%,transparent 25%),linear-gradient(-45deg,#27344b 25%,transparent 25%),linear-gradient(45deg,transparent 75%,#27344b 75%),linear-gradient(-45deg,transparent 75%,#27344b 75%);background-size:20px 20px;background-position:0 0,0 10px,10px -10px,-10px 0}.hand{margin-top:24px;display:grid;grid-template-columns:1fr 1fr;align-items:center;gap:24px}.hand img{max-width:100%;max-height:520px;display:block;margin:auto}footer{color:#91a3bf;font-size:13px;margin-top:28px}@media(max-width:1100px){.grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:680px){.grid,.hand{grid-template-columns:1fr}h1{font-size:34px}main{padding:24px 16px}}
</style><main><header><div class="label">LEGENDARY · SEVEN ELEMENTS · TEXTURE COLLECTION</div><h1>เจ็ดธาตุแห่งคัมภีร์ตำนาน</h1><p>วงอักขระและเอฟเฟกต์หลัก 14 ภาพ · พื้นหลังโปร่งใส · คลิกภาพเพื่อเปิด PNG ต้นฉบับ</p><p>งานภาพรอบนี้ยังไม่ใช่ภาพเอฟเฟกต์ที่เล่นในเกม</p></header><div class="toolbar"><button id="alpha">แสดงพื้นตารางโปร่งใส</button><a class="download" href="../../dist/legendary-element-textures-v1.zip">ดาวน์โหลดชุด PNG</a><a class="download" href="texture-board.jpg" target="_blank">ดูบอร์ดรวม</a></div><div class="grid">__CARDS__</div>__HAND__<footer>สร้างด้วย built-in ImageGen · เก็บ PNG ต้นฉบับและ prompt เต็มในโฟลเดอร์งาน · ธาตุดินใช้มือบอสเดิม ส่วนภาพใหม่เป็นวงอักขระและคลื่นกระแทก</footer></main><script>document.getElementById('alpha').onclick=()=>document.body.classList.toggle('checker')</script></html>'''
    (PREVIEW / 'index.html').write_text(page.replace('__CARDS__', ''.join(cards)).replace('__HAND__', hand_html), encoding='utf8')
    record = {'collection': 'legendary-elements-v1', 'phase': 'Native texture production only',
              'tool': 'built-in image_gen.imagegen', 'textures': 14, 'elements': catalog,
              'native_pixels': 'Selected generated PNGs copied unchanged; only review thumbnails are resized',
              'runtime_changed': False, 'packs_changed': False,
              'earth_hand': 'Existing Ancient Judge model; see earth/boss-hand-reuse.json'}
    (ART / 'catalog.json').write_text(json.dumps(record, indent=2) + '\n', encoding='utf8')
    bundle = ROOT / 'dist/legendary-element-textures-v1.zip'
    with zipfile.ZipFile(bundle, 'w', zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(ART.rglob('*')):
            if path.is_file():
                archive.write(path, 'legendary-elements-v1/' + path.relative_to(ART).as_posix())
        for name in ('texture-board.jpg', 'boss-hand-reuse.png'):
            if (PREVIEW / name).is_file():
                archive.write(PREVIEW / name, 'preview/' + name)
    print(json.dumps({'textures': 14, 'gallery': str(PREVIEW / 'index.html'), 'board': str(PREVIEW / 'texture-board.jpg'),
                      'bundle': str(bundle), 'bundle_bytes': bundle.stat().st_size}, indent=2))


if __name__ == '__main__':
    main()
