#!/usr/bin/env python3
"""Stacks the WearSheetTest rows into /workspace/dingrow-wear/wear-sheet.jpg (labels on the left)."""
import glob, os, sys
from PIL import Image, ImageDraw, ImageFont
D = '/workspace/dingrow-wear'; sp = sys.argv[1] if len(sys.argv) > 1 else 'base'
rows = sorted(glob.glob(f'{D}/row-{sp}-*.png'), key=lambda p: (0 if p.endswith('none.png') else 1, p))
ims = [Image.open(p).convert('RGB') for p in rows]
lw = 150; W = lw + max(i.width for i in ims); H = sum(i.height for i in ims) + 40
sheet = Image.new('RGB', (W, H), 'white'); d = ImageDraw.Draw(sheet)
try: font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 18)
except Exception: font = None
for k, t in enumerate(['egg', 'baby idle', 'child happy', 'adult idle', 'adult sleep', 'adult walk']):
    d.text((lw + k * ims[0].width // 6 + 10, 10), t, fill='black', font=font)
y = 40
for p, im in zip(rows, ims):
    sheet.paste(im, (lw, y)); d.text((8, y + im.height // 2 - 10), os.path.basename(p)[len(f'row-{sp}-'):-4], fill='black', font=font)
    d.line([(0, y), (W, y)], fill='#ddd'); y += im.height
out = f'{D}/wear-sheet.jpg' if sp == 'base' else f'{D}/wear-sheet-{sp}.jpg'
sheet.save(out, quality=88); print(out, sheet.size)
