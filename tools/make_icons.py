#!/usr/bin/env python3
"""Hachi launcher / bildirim ikonlarini kaynak gorsellerden uretir.

Kaynaklar (kullanicinin KENDI sagladigi dosyalar; repoda bulunmaz):
  <launcher-logo.png>   kare, tam kanamali logo  -> launcher
  <bildirim-logo.png>   beyaz/şeffaf siluet      -> bildirim / emblem

Yol verme (ikisinden biri):
  python3 tools/make_icons.py <launcher.png> <bildirim.png>
  HACHI_LAUNCHER_SRC=... HACHI_NOTIF_SRC=... python3 tools/make_icons.py

Neden bu betik var
------------------
Kaynak logo TAM KANAMALI (full-bleed): koyu (neredeyse #141414) bir kare,
figur ortada. Eski surum kaynagi tuvalin %83'une (INNER=360) olcekleyip
neredeyse tamamen dolduruyordu; adaptive icon maskesi altinda bu
"zoomlanmis" gorunuyordu.

Cozum
-----
Kaynagi tuvalin yalnizca ~%62'sine olcekliyor ve ortaliyoruz. Adaptive icon
maskesinin kirpmayacagi GARANTI edilen alan merkezdeki 66dp (%61) safe
zone'dur; %62'ye sigdirilan tam logo kendi koyu kenarlariyla birlikte bu
alanin icine oturur ve hicbir zaman kirpilmaz. Kenar boslugu arka plan rengi
(#141414 = @color/ic_launcher_background) ile doldurulur; boylece logonun koyu
kare govdesi zeminle kaynasir ve yalnizca figur net gorunur.

Uretilenler (her yogunluk: mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi icin):
  drawable-<d>/ic_launcher_foreground.png   (108dp: 108/162/216/324/432px)
  drawable-<d>/ic_launcher_monochrome.png   (108dp, beyaz siluet)
  drawable-<d>/ic_notification.png          (24dp: 24/36/48/72/96px, beyaz)
  mipmap-<d>/ic_launcher.png                (48dp: 48/72/96/144/192px)
  mipmap-<d>/ic_launcher_round.png          (48dp)

(Zaten uretilmis PNG'ler repoda; bu betik yalnizca yeniden uretmek icin.)
"""

import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")

# Kaynak gorseller kullanicinin kendi dosyalaridir ve repoda tutulmaz.
# Sirasiyla: komut satiri argumanlari > ortam degiskenleri. Ikisi de yoksa hata.
launcher_arg = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("HACHI_LAUNCHER_SRC")
notif_arg = sys.argv[2] if len(sys.argv) > 2 else os.environ.get("HACHI_NOTIF_SRC")

if not launcher_arg or not notif_arg:
    print(
        "Kullanim: python3 tools/make_icons.py <launcher-logo.png> <bildirim-logo.png>\n"
        "     ya da: HACHI_LAUNCHER_SRC=... HACHI_NOTIF_SRC=... python3 tools/make_icons.py",
        file=sys.stderr,
    )
    sys.exit(2)

LAUNCHER_SRC = launcher_arg
NOTIF_SRC = notif_arg

# (klasor eki, olcek). mdpi=1x ... xxxhdpi=4x.
DENSITIES = [
    ("mdpi", 1.0),
    ("hdpi", 1.5),
    ("xhdpi", 2.0),
    ("xxhdpi", 3.0),
    ("xxxhdpi", 4.0),
]

FG_DP = 108          # adaptive icon tuvali
NOTIF_DP = 24        # kucuk bildirim ikonu
MIPMAP_DP = 48       # legacy launcher ikonu

# Logonun tuval icine orani. 66dp safe zone = %61; %62 ile tam sigar.
FG_FRAC = 0.62
# Bildirim silueti tuvalin ~%72'sine sigar.
NOTIF_FRAC = 0.72

# @color/ic_launcher_background ile ayni renk (#141414).
BG = (20, 20, 20)


def crop_opaque(img: Image.Image, thresh: int = 32) -> Image.Image:
    """Saydam olmayan icerigin sinir kutusuna kirpar (etrafta kirinti varsa atar)."""
    bbox = img.getchannel("A").point(lambda v: 255 if v > thresh else 0).getbbox()
    return img.crop(bbox) if bbox else img


def fit_square(img: Image.Image, side: int) -> Image.Image:
    """En-boy oranini koruyarak `side` kutusuna sigdirir."""
    w, h = img.size
    scale = side / max(w, h)
    return img.resize((max(1, round(w * scale)), max(1, round(h * scale))), Image.LANCZOS)


def make_silhouette(src: Image.Image, side: int = 432) -> Image.Image:
    """Kaynaktan beyaz siluet uretir: parlak VE opak pikseller beyaz olur.

    Figur kaynakta aydinlik (beyaz/aydinlatilmis), zemin koyudur; bu yuzden
    parlaklik esigi ile figur ayrilir. Saydam koseler (a=0) disarida kalir.
    """
    small = src.resize((side, side), Image.LANCZOS)
    out = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    sp = small.load()
    op = out.load()
    for y in range(side):
        for x in range(side):
            r, g, b, a = sp[x, y]
            if a > 40 and (r * 299 + g * 587 + b * 114) // 1000 > 128:
                op[x, y] = (255, 255, 255, 255)
    return crop_opaque(out)


def whiten(img: Image.Image) -> Image.Image:
    """RGB'yi beyaza cevirir, alfa korunur (sistem bildirim ikonunu boyar)."""
    r, g, b, a = img.split()
    white = r.point(lambda _: 255)
    return Image.merge("RGBA", (white, white, white, a))


def save(img: Image.Image, *parts: str) -> None:
    path = os.path.join(RES, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, optimize=True)
    print("yazildi:", os.path.relpath(path, ROOT))


def make_launcher(src: Image.Image, silhouette: Image.Image) -> None:
    for name, scale in DENSITIES:
        px = round(FG_DP * scale)
        inner = round(px * FG_FRAC)

        # Foreground: tam logo %62'ye sigar, #141414 uzerine ortalanir.
        fig = src.resize((inner, inner), Image.LANCZOS)
        canvas = Image.new("RGBA", (px, px), (*BG, 255))
        off = (px - inner) // 2
        canvas.alpha_composite(fig, (off, off))
        save(canvas, f"drawable-{name}", "ic_launcher_foreground.png")

        # Monochrome: ayni kadraj, yalnizca beyaz siluet (arka plan saydam).
        mono = fit_square(silhouette, inner)
        m_canvas = Image.new("RGBA", (px, px), (0, 0, 0, 0))
        m_canvas.alpha_composite(mono, ((px - mono.width) // 2, (px - mono.height) // 2))
        save(m_canvas, f"drawable-{name}", "ic_launcher_monochrome.png")

        # Legacy mipmap launcher ikonlari (48dp).
        mpx = round(MIPMAP_DP * scale)
        minner = round(mpx * FG_FRAC)
        mfig = src.resize((minner, minner), Image.LANCZOS)
        mimg = Image.new("RGBA", (mpx, mpx), (*BG, 255))
        moff = (mpx - minner) // 2
        mimg.alpha_composite(mfig, (moff, moff))
        save(mimg, f"mipmap-{name}", "ic_launcher.png")
        save(mimg, f"mipmap-{name}", "ic_launcher_round.png")


def make_notification(src: Image.Image) -> None:
    glyph = crop_opaque(src)
    for name, scale in DENSITIES:
        px = round(NOTIF_DP * scale)
        inner = round(px * NOTIF_FRAC)
        fig = whiten(fit_square(glyph, inner))
        canvas = Image.new("RGBA", (px, px), (0, 0, 0, 0))
        canvas.alpha_composite(fig, ((px - fig.width) // 2, (px - fig.height) // 2))
        save(canvas, f"drawable-{name}", "ic_notification.png")


def main() -> int:
    if not os.path.isfile(LAUNCHER_SRC) or not os.path.isfile(NOTIF_SRC):
        print("Kaynak gorseller bulunamadi:", LAUNCHER_SRC, NOTIF_SRC, file=sys.stderr)
        return 1

    launcher_src = Image.open(LAUNCHER_SRC).convert("RGBA")
    notif_src = Image.open(NOTIF_SRC).convert("RGBA")

    silhouette = make_silhouette(launcher_src)
    make_launcher(launcher_src, silhouette)
    make_notification(notif_src)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
