#!/usr/bin/env python3
"""Hachi launcher / bildirim ikonlarini kaynak gorsellerden uretir.

Kaynaklar (kullanicinin sagladigi dosyalar):
  /storage/emulated/0/hachilogofinal1.png  (2048x2048)  -> launcher
  /storage/emulated/0/hachinotif.png       (1508x1272)  -> bildirim / emblem

Neden bu betik var
------------------
Adaptive icon tuvali 108dp'dir; maskenin kirpmayacagi GARANTI edilen alan
merkezdeki 72dp'lik (yani %66.7) "safe zone"dur. Kaynak logo tam kanamali
(full-bleed): figur tuvalin %73'unu kapliyor -> dogrudan 432px'e kucultup
foreground yaptigimizda figur safe zone'u asiyor ve maske tarafindan
kirpildigi icin "zoomlanmis" gorunuyordu.

Cozum: kaynagi tuvalin %83.3'une (360/432) olcekleyip ortalayip, kalan
kenarlari kaynagin kendi kenar rengiyle dolduruyoruz. Boylece figur tam
olarak safe zone'un icine oturur (360*0.732 = 264px = 66dp <= 72dp) ve ikon
verilen gorsel gibi, sadece biraz daha genis koyu kenar bosluguyla gorunur.

Calistirma:  python3 tools/make_icons.py
(Zaten uretilmis PNG'ler repoda; bu betik yalnizca yeniden uretmek icin.)
"""

import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "res", "drawable-xxxhdpi")

LAUNCHER_SRC = "/storage/emulated/0/hachilogofinal1.png"
NOTIF_SRC = "/storage/emulated/0/hachinotif.png"

# Adaptive icon tuvali 432px (xxxhdpi: 108dp * 4).
CANVAS = 432
# Safe zone = 72dp -> 288px. Figur 360px tuvalin %73'u = 264px < 288px.
INNER = 360
# Bildirim kucuk ikonu 96px (24dp * 4); icerik ~%72'ye sigar.
NOTIF_CANVAS = 96
NOTIF_INNER = 69


def border_color(img: Image.Image, ring_frac: float = 0.02) -> tuple[int, int, int]:
    """Kenar halkasindaki opak piksellerin ortalama rengi (vinyet devami icin)."""
    w, h = img.size
    pad = max(1, int(min(w, h) * ring_frac))
    px = img.load()
    s = [0, 0, 0]
    n = 0
    step = max(1, min(w, h) // 256)
    for y in range(0, h, step):
        for x in range(0, w, step):
            if x < pad or x >= w - pad or y < pad or y >= h - pad:
                r, g, b, a = px[x, y]
                if a > 200:
                    s[0] += r
                    s[1] += g
                    s[2] += b
                    n += 1
    if n == 0:
        return (20, 20, 20)
    return (s[0] // n, s[1] // n, s[2] // n)


def make_launcher() -> None:
    src = Image.open(LAUNCHER_SRC).convert("RGBA")
    bg = border_color(src)
    print(f"launcher kenar rengi: #{bg[0]:02X}{bg[1]:02X}{bg[2]:02X}")

    inner = src.resize((INNER, INNER), Image.LANCZOS)

    canvas = Image.new("RGBA", (CANVAS, CANVAS), (*bg, 255))
    off = (CANVAS - INNER) // 2
    canvas.alpha_composite(inner, (off, off))

    canvas.save(os.path.join(OUT, "ic_launcher_foreground.png"), optimize=True)
    print("yazildi: ic_launcher_foreground.png")

    # Renkli modda da ayni kadraj: tek renkli katman sistem tarafindan boyanir,
    # bu yuzden arka plan saydam, yalnizca figur kaliyor.
    mono = Image.open(os.path.join(OUT, "ic_launcher_monochrome.png")).convert("RGBA")
    mono = crop_opaque(mono)
    mono = fit_square(mono, INNER)
    mono_canvas = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    m_off = (CANVAS - mono.width) // 2
    mono_canvas.alpha_composite(mono, (m_off, (CANVAS - mono.height) // 2))
    mono_canvas.save(os.path.join(OUT, "ic_launcher_monochrome.png"), optimize=True)
    print("yazildi: ic_launcher_monochrome.png")


def crop_opaque(img: Image.Image, thresh: int = 32) -> Image.Image:
    """Saydam olmayan icerigin sinir kutusuna kirpar (etrafta kirinti varsa atar)."""
    bbox = img.getchannel("A").point(lambda v: 255 if v > thresh else 0).getbbox()
    return img.crop(bbox) if bbox else img


def fit_square(img: Image.Image, side: int) -> Image.Image:
    """En-boy oranini koruyarak `side` kutusuna sigdirir."""
    w, h = img.size
    scale = side / max(w, h)
    return img.resize((max(1, round(w * scale)), max(1, round(h * scale))), Image.LANCZOS)


def make_notification() -> None:
    src = Image.open(NOTIF_SRC).convert("RGBA")
    src = crop_opaque(src)
    fig = fit_square(src, NOTIF_INNER)

    # Bildirim ikonu her zaman BEYAZ olmali (sistem renklendirir).
    px = fig.load()
    for y in range(fig.height):
        for x in range(fig.width):
            r, g, b, a = px[x, y]
            px[x, y] = (255, 255, 255, a)

    canvas = Image.new("RGBA", (NOTIF_CANVAS, NOTIF_CANVAS), (0, 0, 0, 0))
    canvas.alpha_composite(fig, ((NOTIF_CANVAS - fig.width) // 2, (NOTIF_CANVAS - fig.height) // 2))
    canvas.save(os.path.join(OUT, "ic_notification.png"), optimize=True)
    print("yazildi: ic_notification.png")


def main() -> int:
    if not os.path.isfile(LAUNCHER_SRC) or not os.path.isfile(NOTIF_SRC):
        print("Kaynak gorseller bulunamadi:", LAUNCHER_SRC, NOTIF_SRC, file=sys.stderr)
        return 1
    make_launcher()
    make_notification()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
