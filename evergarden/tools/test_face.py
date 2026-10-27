"""Prototype script to paint a beautiful anime sorceress face."""
from PIL import Image, ImageDraw, ImageFilter
import numpy as np

def create_sorceress_face(w=128, h=128):
    # w x h texels for front face of head
    img = Image.new("RGBA", (w, h), (252, 240, 232, 255))
    draw = ImageDraw.Draw(img)
    
    # Soft skin shading: slightly darker at edges, jaw, and under bangs
    # Base skin gradient
    for y in range(h):
        for x in range(w):
            # Distance from center
            nx = (x - w/2) / (w/2)
            ny = (y - h/2) / (h/2)
            dist = np.sqrt(nx**2 + ny**2)
            # Shade chin and top
            shade = 0.0
            if y < h * 0.25: # under bangs shadow
                shade += (h * 0.25 - y) / (h * 0.25) * 0.12
            if y > h * 0.75: # chin shadow
                shade += (y - h * 0.75) / (h * 0.25) * 0.15
            if abs(nx) > 0.6: # cheek sides
                shade += (abs(nx) - 0.6) / 0.4 * 0.1
            
            r = int(np.clip(252 * (1.0 - shade * 0.8), 180, 255))
            g = int(np.clip(240 * (1.0 - shade * 0.9), 160, 255))
            b = int(np.clip(234 * (1.0 - shade * 0.85), 150, 255))
            img.putpixel((x, y), (r, g, b, 255))
    
    # Soft blush on cheeks
    # Left cheek: center around (w*0.28, h*0.62)
    # Right cheek: center around (w*0.72, h*0.62)
    blush_layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    b_draw = ImageDraw.Draw(blush_layer)
    b_rad_x = int(w * 0.14)
    b_rad_y = int(h * 0.08)
    b_draw.ellipse([int(w*0.28 - b_rad_x), int(h*0.62 - b_rad_y), int(w*0.28 + b_rad_x), int(h*0.62 + b_rad_y)], fill=(255, 140, 150, 90))
    b_draw.ellipse([int(w*0.72 - b_rad_x), int(h*0.62 - b_rad_y), int(w*0.72 + b_rad_x), int(h*0.62 + b_rad_y)], fill=(255, 140, 150, 90))
    # Soft blush lines / hatching
    for offset in [-4, 0, 4]:
        b_draw.line([(w*0.28 + offset - 3, h*0.62 - 2), (w*0.28 + offset + 3, h*0.62 + 2)], fill=(240, 110, 120, 140), width=1)
        b_draw.line([(w*0.72 + offset - 3, h*0.62 - 2), (w*0.72 + offset + 3, h*0.62 + 2)], fill=(240, 110, 120, 140), width=1)
        
    blush_layer = blush_layer.filter(ImageFilter.GaussianBlur(radius=w*0.04))
    img = Image.alpha_composite(img, blush_layer)
    draw = ImageDraw.Draw(img)

    # Eyes:
    # Left eye: center at w*0.30, y=h*0.48, width=w*0.22, height=h*0.26
    # Right eye: center at w*0.70, y=h*0.48, width=w*0.22, height=h*0.26
    def draw_eye(cx, cy, ew, eh, flip=False):
        # Sclera (white with soft top shadow)
        eye_box = [cx - ew/2, cy - eh/2, cx + ew/2, cy + eh/2]
        draw.ellipse(eye_box, fill=(245, 245, 252, 255))
        # Top shadow of sclera
        draw.chord(eye_box, start=180, end=360, fill=(215, 218, 235, 255))
        
        # Iris (almond/oval shape)
        iw = ew * 0.72
        ih = eh * 0.95
        iris_box = [cx - iw/2, cy - ih/2, cx + iw/2, cy + ih/2]
        
        # Multi-tone iris: deep violet top to bright cyan-violet bottom
        for step in range(int(ih)):
            t = step / ih
            # Gradient: top (40, 20, 70) -> mid (110, 45, 160) -> bot (60, 200, 255)
            if t < 0.5:
                sub_t = t / 0.5
                r = int(40 + sub_t * 70)
                g = int(20 + sub_t * 25)
                b = int(70 + sub_t * 90)
            else:
                sub_t = (t - 0.5) / 0.5
                r = int(110 - sub_t * 50)
                g = int(45 + sub_t * 155)
                b = int(160 + sub_t * 95)
            # calculate width of slice
            angle = np.arcsin(np.clip((step - ih/2) / (ih/2), -1.0, 1.0))
            slice_w = iw * np.cos(angle)
            sy = cy - ih/2 + step
            draw.line([(cx - slice_w/2, sy), (cx + slice_w/2, sy)], fill=(r, g, b, 255), width=1)
            
        # Pupil (dark violet-black diamond/circle)
        pw = iw * 0.35
        ph = ih * 0.45
        draw.ellipse([cx - pw/2, cy - ph/2 - ih*0.05, cx + pw/2, cy + ph/2 - ih*0.05], fill=(25, 10, 45, 255))
        # Starlight rune in iris (glowing cyan cross)
        draw.line([(cx - iw*0.2, cy + ih*0.15), (cx + iw*0.2, cy + ih*0.15)], fill=(180, 245, 255, 220), width=1)
        draw.line([(cx, cy + ih*0.15 - iw*0.2), (cx, cy + ih*0.15 + iw*0.2)], fill=(180, 245, 255, 220), width=1)

        # Upper eyelash & curved eyeliner (thick, glamorous anime style)
        lash_col = (35, 22, 50, 255)
        # main arch
        y_arch = cy - eh/2
        points = [
            (cx - ew*0.58, y_arch + eh*0.35),
            (cx - ew*0.3, y_arch - eh*0.05),
            (cx, y_arch - eh*0.12),
            (cx + ew*0.3, y_arch - eh*0.05),
            (cx + ew*0.62 if not flip else cx + ew*0.55, y_arch + (eh*0.15 if not flip else eh*0.35))
        ]
        if flip:
            # wing flip
            points[0] = (cx - ew*0.62, y_arch + eh*0.15)
        for i in range(len(points)-1):
            draw.line([points[i], points[i+1]], fill=lash_col, width=int(eh*0.18))
        # Wing tip
        if not flip:
            draw.polygon([(cx + ew*0.45, y_arch), (cx + ew*0.72, y_arch - eh*0.15), (cx + ew*0.55, y_arch + eh*0.15)], fill=lash_col)
        else:
            draw.polygon([(cx - ew*0.45, y_arch), (cx - ew*0.72, y_arch - eh*0.15), (cx - ew*0.55, y_arch + eh*0.15)], fill=lash_col)
            
        # Lower lash line (subtle)
        draw.arc([cx - ew*0.35, cy + eh*0.2, cx + ew*0.35, cy + eh*0.5], start=20, end=160, fill=(90, 60, 100, 200), width=int(eh*0.07))

        # Big Specular Highlights (Sparkling Anime Eyes)
        # Main primary highlight (top-left)
        hl1_x = cx - iw*0.25 if not flip else cx - iw*0.15
        hl1_y = cy - ih*0.2
        draw.ellipse([hl1_x - ew*0.12, hl1_y - eh*0.14, hl1_x + ew*0.12, hl1_y + eh*0.14], fill=(255, 255, 255, 255))
        # Secondary highlight (bottom-right)
        hl2_x = cx + iw*0.2 if not flip else cx + iw*0.25
        hl2_y = cy + ih*0.2
        draw.ellipse([hl2_x - ew*0.07, hl2_y - eh*0.08, hl2_x + ew*0.07, hl2_y + eh*0.08], fill=(230, 245, 255, 220))

    draw_eye(w * 0.31, h * 0.46, w * 0.24, h * 0.28, flip=False)
    draw_eye(w * 0.69, h * 0.46, w * 0.24, h * 0.28, flip=True)

    # Eyebrows (elegant arched deep violet)
    brow_col = (60, 38, 75, 240)
    draw.arc([w*0.18, h*0.24, w*0.44, h*0.38], start=200, end=350, fill=brow_col, width=int(h*0.04))
    draw.arc([w*0.56, h*0.24, w*0.82, h*0.38], start=190, end=340, fill=brow_col, width=int(h*0.04))

    # Dainty nose (small highlight and subtle shadow dot)
    draw.ellipse([w*0.48, h*0.65, w*0.52, h*0.68], fill=(225, 175, 165, 220))
    draw.point((int(w*0.49), int(h*0.64)), fill=(255, 255, 255, 230))

    # Alluring Rose Lips (delicate Cupid's bow and soft rosy shine)
    lip_box = [w*0.42, h*0.77, w*0.58, h*0.85]
    draw.ellipse(lip_box, fill=(225, 115, 130, 230))
    # upper lip line
    draw.line([(w*0.43, h*0.78), (w*0.48, h*0.79), (w*0.50, h*0.78), (w*0.52, h*0.79), (w*0.57, h*0.78)], fill=(170, 70, 90, 255), width=int(h*0.025))
    # lower lip gloss highlight
    draw.ellipse([w*0.47, h*0.81, w*0.53, h*0.84], fill=(255, 200, 210, 200))

    return img

test_face = create_sorceress_face(256, 256)
test_face.save("evergarden/tools/test_sorceress_face.png")
print("Saved evergarden/tools/test_sorceress_face.png")
