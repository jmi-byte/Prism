"""Writes Veloura's icon as Android vector drawables from one set of shapes (the ribbon V, traced from the owner's design)."""
import os, sys
res = sys.argv[1]
S, TX, TY = 0.125, 19.375, 22.625
HDR = '<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated: Veloura ribbon V (owner\'s design). Keep in step with the other ic_veloura_* drawables. -->\n'
def vec(w, h, vw, vh, body):
    return (HDR + f'<vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt"\n'
            f'    android:width="{w}dp" android:height="{h}dp" android:viewportWidth="{vw}" android:viewportHeight="{vh}">\n{body}</vector>\n')
def lin(x1, y1, x2, y2, stops):
    items = ''.join(f'                <item android:offset="{o}" android:color="{c}"/>\n' for o, c in stops)
    return (f'        <aapt:attr name="android:fillColor">\n            <gradient android:type="linear" android:startX="{x1}" android:startY="{y1}" '
            f'android:endX="{x2}" android:endY="{y2}">\n{items}            </gradient>\n        </aapt:attr>\n')
def rad(cx, cy, r, stops):
    items = ''.join(f'                <item android:offset="{o}" android:color="{c}"/>\n' for o, c in stops)
    return (f'        <aapt:attr name="android:fillColor">\n            <gradient android:type="radial" android:centerX="{cx}" android:centerY="{cy}" '
            f'android:gradientRadius="{r}">\n{items}            </gradient>\n        </aapt:attr>\n')
def path(d, fill=None, alpha=None, grad=None, indent='    '):
    a = f' android:fillAlpha="{alpha}"' if alpha is not None else ''
    if grad: return f'{indent}<path android:pathData="{d}"{a}>\n{grad}{indent}</path>\n'
    return f'{indent}<path android:pathData="{d}" android:fillColor="{fill}"{a}/>\n'

BLUE = "M60,118 C58,98 74,86 104,86 L178,88 C196,89 205,98 212,110 L300,300 L262,420 C250,430 232,420 222,404 L78,170 C66,152 60,135 60,118 Z"
CURL = "M60,118 C56,148 64,180 82,210 L112,262 C98,208 92,152 106,98 C86,92 66,100 60,118 Z"
SHADE = "M160,124 C178,112 196,104 210,108 L298,298 L270,330 Z"
ORANGE = "M386,110 L494,104 C486,124 470,156 455,190 L425,262 C405,295 350,312 292,302 L386,110 Z"
PINK = "M292,302 C350,312 405,295 425,262 L405,320 C388,368 368,410 338,432 C318,446 278,448 250,432 C236,424 226,412 218,398 C245,370 270,336 292,302 Z"

def ribbon(indent):
    return (path(BLUE, grad=lin(80, 90, 270, 430, [(0, '#FF22E6FF'), (0.5, '#FF2FA6FF'), (1, '#FF3E68FF')]), indent=indent)
          + path(CURL, alpha=0.85, grad=lin(50, 100, 110, 240, [(0, '#FF1B8FE8'), (1, '#001B8FE8')]), indent=indent)
          + path(SHADE, fill='#2458D0', alpha=0.3, indent=indent)
          + path(ORANGE, grad=lin(460, 105, 330, 300, [(0, '#FFFF8F3D'), (1, '#FFFF693E')]), indent=indent)
          + path(PINK, alpha=0.95, grad=lin(230, 430, 425, 270, [(0, '#FFC24BFF'), (0.55, '#FFEE5AD6'), (1, '#FFFF6C9E')]), indent=indent))

def group(body, tx, ty, s):
    return f'    <group android:translateX="{tx}" android:translateY="{ty}" android:scaleX="{s}" android:scaleY="{s}">\n{body}    </group>\n'

d = os.path.join(res, 'drawable'); os.makedirs(d, exist_ok=True)
open(os.path.join(d, 'ic_veloura_background.xml'), 'w').write(vec(108, 108, 108, 108,
    path("M0,0h108v108h-108z", grad=rad(52, 46, 70, [(0, '#FF1D40BA'), (0.55, '#FF0E2479'), (1, '#FF070F45')]))
  + path("M0,0h108v108h-108z", grad=rad(58, 76, 24, [(0, '#B39A3DFF'), (1, '#009A3DFF')]))))
open(os.path.join(d, 'ic_veloura_foreground.xml'), 'w').write(vec(108, 108, 108, 108, group(ribbon('        '), TX, TY, S)))
# Themed icons (Android 13+): one colour, Android tints it. The V's silhouette only.
mono = ''.join(path(p, fill='#FFFFFFFF', indent='        ') for p in (BLUE, ORANGE, PINK))
open(os.path.join(d, 'ic_veloura_monochrome.xml'), 'w').write(vec(108, 108, 108, 108, group(mono, TX, TY, S)))
# The V on its own (the in-app logo beside "Veloura"): its shapes' box, 50..500 x 80..455.
open(os.path.join(d, 'ic_veloura_logo.xml'), 'w').write(vec(48, 40, 450, 375, group(ribbon('        '), -50, -80, 1)))
m = os.path.join(res, 'mipmap-anydpi'); os.makedirs(m, exist_ok=True)
adaptive = ('<?xml version="1.0" encoding="utf-8"?>\n<!-- Veloura\'s launcher icon: the ribbon V (owner\'s design) as an adaptive icon. -->\n'
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <background android:drawable="@drawable/ic_veloura_background"/>\n'
            '    <foreground android:drawable="@drawable/ic_veloura_foreground"/>\n'
            '    <monochrome android:drawable="@drawable/ic_veloura_monochrome"/>\n</adaptive-icon>\n')
open(os.path.join(m, 'ic_veloura.xml'), 'w').write(adaptive)
print('ok')
