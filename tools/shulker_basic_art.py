"""Author an editable faceted dimensional prism using the repository's SVG effect workflow."""
from pathlib import Path
from legendary_effect_art import Engraving, shipping_image

ART = Path(__file__).resolve().parents[1] / 'advance-magic/art/effects'


def main():
    d = Engraving()
    violet, lavender, ivory = '#9f64ff', '#d8b8ff', '#f6eeff'
    top = [(512, 172), (810, 342), (512, 512), (214, 342)]
    left = [(214, 342), (512, 512), (512, 816), (214, 646)]
    right = [(512, 512), (810, 342), (810, 646), (512, 816)]
    d.polygon(top, '#bd8cff', ivory, 6, 220)
    d.polygon(left, '#482778', lavender, 6, 235)
    d.polygon(right, '#8345d1', ivory, 6, 225)
    # An open rim and a bright suspended core evoke a Shulker shell without a
    # solid screen-sized plate. All geometry remains editable vector paths.
    d.polygon([(214, 430), (512, 600), (512, 660), (214, 490)], '#201037', violet, 3, 245)
    d.polygon([(512, 600), (810, 430), (810, 490), (512, 660)], '#201037', violet, 3, 245)
    d.path([(214, 430), (512, 600), (810, 430)], 9, ivory)
    d.path([(214, 490), (512, 660), (810, 490)], 5, violet)
    d.jewel(512, 618, 62, 35, '#f0d8ff')
    for x, y, sign in ((365, 462, 1), (661, 462, -1), (365, 716, 1), (661, 716, -1)):
        d.path([(x-24*sign, y-42), (x+24*sign, y-14), (x-12*sign, y+8), (x+20*sign, y+34)], 5, lavender)
        d.spark(x, y, 18, ivory)
    d.polygon([(512, 250), (669, 342), (512, 434), (355, 342)], '#684098', lavender, 4, 155)
    d.path([(355, 342), (512, 250), (669, 342), (512, 434), (355, 342)], 6, ivory)
    for x, y in ((160, 475), (864, 475), (512, 108), (512, 882)):
        d.jewel(x, y, 35, 16, violet)
    image, svg = d.render()
    ART.mkdir(parents=True, exist_ok=True)
    shipping_image(image).save(ART / 'shulker_prism.png')
    (ART / 'shulker_prism.svg').write_text(svg, encoding='utf8')
    print('Authored SVG / PNG:', ART / 'shulker_prism.svg')


if __name__ == '__main__':
    main()
