#!/usr/bin/env python3
"""Omalauncher Ormus: the seven planet themes, generated from the Ormus brand tokens.

  scripts/ormus-themes.py ~/projects/ormus-brand/tokens.json
  scripts/ormus-themes.py tokens.json --out app/src/main/assets/ormus/themes --lacquer midnight-lacquer.jpg

Reads the `planets` group of tokens.json (Sun, Moon, Mercury, Venus, Mars, Jupiter, Saturn) and writes one
Omarchy theme per planet, ormus-<planet>/colors.toml, plus a wallpaper tinted from the brand's midnight lacquer
(needs Pillow; with --no-backgrounds only the colors are written). Ormus-aurum, the older palette, is not touched.
Type: `font: serif` in a widget is Android's system serif, not Cormorant Garamond (the launcher ships no fonts).
The brand repo is the source of the colors; edit them there and run this again, never by hand in colors.toml.
Standard library only, apart from the optional Pillow for the wallpaper.

How the brand's roles map onto the names widgets and Linux read (Palette in ui/Theme.kt):
  background            deep       page ground
  lighter_background    midnight   surface: tiles and panels
  dark_background       codeBg     darker_background is codeBg halved toward black
  foreground            ink        light_foreground and bright_foreground too
  dark_foreground       inkMuted   what widgets call `muted` (secondary text)
  muted                 dim        what widgets call `border`; the quietest text
  accent                gold       the planet's one raised voice; cursor is goldBright
  selection             gold washed 28% into deep
  red                   needs you and errors (STATES: soft red, #e27a72 on most planets)
  blue                  working (STATES)
  green                 profit on the P&L pages (STATES); terminals use it for ANSI green
  cyan orange           the identity accents teal, ember: terminals need ANSI colors; widgets keep off them
  yellow                Sun goldBright: the same in every planet, so a warning never reads as the accent
  magenta               iris: Android derives the system's secondary color from it (OmarchyWallpaperService)
"""
import argparse
import json
import os
import sys

# State hues, per planet: needs-you (red), working (blue) and profit (green, the P&L pages only).
#
# The Sun row is the agent-state pair of the herdr Ormus themes (herdr-ormus/scripts/build.py BLOCKED and WORKING), so the
# desk and the phone agree, plus the brand's leaf green. Errors share the needs-you red on purpose: the brand's danger
# #d44f4f reads only 3.8 to 4.1:1 on the planet surfaces, below the 4.5:1 text needs, so #e27a72 carries both.
#
# On the other planets a hue moves only where it came too close to that planet's accent (CIE76 distance 22 for Mars red,
# 18 for Moon and Saturn blue, 21 for Venus green) and keeps its name's hue family. The rule, checked on every run and
# by OrmusThemesTest: each of the three is at least MIN_DELTA_E (CIE76) from the planet's accent and from the other two,
# and at least 4.5:1 on the planet's surface and ground. 30 is where two colors still read as two different things in a
# 12 sp word at a glance (about thirteen times the smallest difference an eye sees, 2.3); the closest pair left is 30.
MIN_DELTA_E = 30
MIN_CONTRAST = 4.5
STATES = {
    "sun": ("#e27a72", "#6f93cf", "#86c272"),
    "moon": ("#e27a72", "#568be2", "#86c272"),
    "mercury": ("#e27a72", "#6f93cf", "#86c272"),
    "venus": ("#e27a72", "#6f93cf", "#94cb69"),
    "mars": ("#e77b84", "#6f93cf", "#86c272"),
    "jupiter": ("#e27a72", "#6f93cf", "#86c272"),
    "saturn": ("#e27a72", "#5392be", "#86c272"),
}
PLANETS = ["sun", "moon", "mercury", "venus", "mars", "jupiter", "saturn"]


def rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i : i + 2], 16) for i in (0, 2, 4))


def hexa(c):
    return "#%02x%02x%02x" % tuple(max(0, min(255, round(v))) for v in c)


def _lin(c):
    c /= 255
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def _xyz(h):
    r, g, b = (_lin(v) for v in rgb(h))
    return 0.4124 * r + 0.3576 * g + 0.1805 * b, 0.2126 * r + 0.7152 * g + 0.0722 * b, 0.0193 * r + 0.1192 * g + 0.9505 * b


def lab(h):
    x, y, z = _xyz(h)
    f = lambda t: t ** (1 / 3) if t > 0.008856 else 7.787 * t + 16 / 116
    fx, fy, fz = f(x / 0.95047), f(y), f(z / 1.08883)
    return 116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)


def delta_e(a, b):
    """CIE76: the straight distance between two colors in Lab."""
    return sum((p - q) ** 2 for p, q in zip(lab(a), lab(b))) ** 0.5


def contrast(a, b):
    hi, lo = sorted((_xyz(a)[1], _xyz(b)[1]), reverse=True)
    return (hi + 0.05) / (lo + 0.05)


def check_states(planet_name, planet):
    red, blue, green = STATES[planet_name]
    named = {"red": red, "blue": blue, "green": green}
    for n, c in named.items():
        for ground in ("midnight", "deep"):
            if contrast(c, planet[ground]) < MIN_CONTRAST:
                sys.exit(f"{planet_name}: {n} {c} is under {MIN_CONTRAST}:1 on {ground}")
        if delta_e(c, planet["gold"]) < MIN_DELTA_E:
            sys.exit(f"{planet_name}: {n} {c} is within {MIN_DELTA_E} of the accent {planet['gold']}")
    for (n1, c1), (n2, c2) in ((("red", red), ("blue", blue)), (("red", red), ("green", green)), (("blue", blue), ("green", green))):
        if delta_e(c1, c2) < MIN_DELTA_E:
            sys.exit(f"{planet_name}: {n1} and {n2} are within {MIN_DELTA_E} of each other")


def mix(a, b, t):
    """t of a, the rest b."""
    x, y = rgb(a), rgb(b)
    return hexa(tuple(p * t + q * (1 - t) for p, q in zip(x, y)))


def colors_toml(name, planet, accents, sun):
    red, blue, green = STATES[name.split("-")[1]]
    c = [
        ("mode", "dark"),
        ("accent", planet["gold"]),
        ("cursor", planet["goldBright"]),
        ("selection", mix(planet["gold"], planet["deep"], 0.28)),
        ("selection_background", mix(planet["gold"], planet["deep"], 0.28)),
        ("selection_foreground", planet["ink"]),
        ("muted", planet["dim"]),
        ("background", planet["deep"]),
        ("lighter_background", planet["midnight"]),
        ("dark_background", planet["codeBg"]),
        ("darker_background", mix(planet["codeBg"], "#000000", 0.5)),
        ("foreground", planet["ink"]),
        ("light_foreground", planet["ink"]),
        ("bright_foreground", planet["ink"]),
        ("dark_foreground", planet["inkMuted"]),
        ("red", red),
        ("blue", blue),
        ("green", green),
        ("cyan", accents["teal"]),
        ("orange", accents["ember"]),
        ("yellow", sun["goldBright"]),
        ("magenta", accents["iris"]),
    ]
    head = [
        f"# {name}: generated by scripts/ormus-themes.py from the Ormus brand tokens (planets.{name.split('-')[1]}).",
        "# Do not edit by hand: change tokens.json in ormus-brand and run the script again.",
    ]
    return "\n".join(head + [f'{k} = "{v}"' for k, v in c]) + "\n"


def wallpaper(planet, lacquer_path, out_path):
    """The brand's lacquer texture painted in the planet's ground, with a soft bloom of its accent top right."""
    from PIL import Image, ImageChops

    src = Image.open(lacquer_path).convert("L")
    lo, hi = src.getextrema()
    t = src.point(lambda v: 255 * (v - lo) / max(1, hi - lo))
    deep, mid = rgb(planet["deep"]), rgb(planet["midnight"])
    # The lacquer's darkest is the planet's deep, its brightest a little past midnight: a surface, not a glare.
    ramp = lambda i: tuple(d + (m - d) * 1.6 * i / 255 for d, m in zip(deep, mid))
    chans = [t.point(lambda v, k=k: round(ramp(v)[k])) for k in range(3)]
    img = Image.merge("RGB", chans)
    w, h = img.size
    bloom = Image.radial_gradient("L").resize((w * 2, h * 2)).crop((0, h, w, h * 2))
    bloom = ImageChops.invert(bloom).point(lambda v: round(v * 0.16))
    img = Image.composite(Image.new("RGB", img.size, rgb(planet["gold"])), img, bloom)
    img.save(out_path, "JPEG", quality=80, optimize=True)


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("tokens", help="the brand's tokens.json")
    ap.add_argument("--out", default="app/src/main/assets/ormus/themes", help="themes directory to write into")
    ap.add_argument("--lacquer", help="midnight-lacquer.jpg (default: assets/ next to tokens.json)")
    ap.add_argument("--no-backgrounds", action="store_true", help="write the colors only")
    a = ap.parse_args()

    with open(a.tokens) as f:
        tokens = json.load(f)
    planets, accents = tokens["planets"], tokens["accents"]
    lacquer = a.lacquer or os.path.join(os.path.dirname(os.path.abspath(a.tokens)), "assets", "midnight-lacquer.jpg")
    backgrounds = not a.no_backgrounds
    if backgrounds:
        try:
            import PIL  # noqa: F401
        except ImportError:
            sys.exit("Pillow is needed for the wallpapers: pip install pillow, or pass --no-backgrounds")
        if not os.path.isfile(lacquer):
            sys.exit(f"no lacquer at {lacquer}: pass --lacquer or --no-backgrounds")
    for p in PLANETS:
        name = f"ormus-{p}"
        d = os.path.join(a.out, name)
        os.makedirs(os.path.join(d, "backgrounds"), exist_ok=True)
        with open(os.path.join(d, "colors.toml"), "w") as f:
            check_states(p, planets[p])
            f.write(colors_toml(name, planets[p], accents, planets["sun"]))
        with open(os.path.join(d, "icons.theme"), "w") as f:
            f.write("Yaru-dark\n")
        if backgrounds:
            wallpaper(planets[p], lacquer, os.path.join(d, "backgrounds", "0-lacquer.jpg"))
    print(f"{len(PLANETS)} planet themes written in {a.out}" + ("" if backgrounds else " (colors only)"))


if __name__ == "__main__":
    main()
