#!/usr/bin/env python3
"""Generates the Create-scale Lost Cities railway parts shipped in the built-in datapack.

The parts override The Lost Cities' own railway parts by id (lostcities:rails_horizontal, ...), so every
world style that uses the default railway part selector picks them up. They contain tunnels, platforms
and stairs only. There is no track in them: Create track placed during world generation never joins
Create's TrackGraph, so the mod's stitcher places all track afterwards (see SubwayStitcher).

Conventions (same as Lost Cities):
  * slices[y][z][x]: y = slice above the part origin, z = row (north to south), x = column (west to east)
  * east-west lines run along X; directional parts are authored for WEST and mirrored for EAST by Lost Cities
  * slice 1 is the rail row; lanes are at z = 3, 7, 11 (Create trains are 3 wide)

Palette characters (resolved against the city style's compiled palette, refpalette "rails"):
  y wall/floor/ceiling, ~ forced air, Q platform, h glowstone, + glass, = slab (stairs), w wall,
  ) and ( quartz stairs (roof edges), space = leave whatever is there.

Run from the repository root:  python3 tools/generate_parts.py
"""
import json
import os

OUT = os.path.join("src", "main", "resources", "datapacks", "subways", "data", "lostcities", "lostcities", "parts")
SIZE = 16
LANES = (3, 7, 11)
PLATFORMS = (1, 5, 9, 13, 14)


def blank():
    return [[" "] * SIZE for _ in range(SIZE)]


def fill(slice_, char, xs=range(SIZE), zs=range(SIZE)):
    for z in zs:
        for x in xs:
            slice_[z][x] = char


def render(slices):
    return [["".join(row) for row in s] for s in slices]


def write(name, slices):
    part = {"xsize": SIZE, "zsize": SIZE, "refpalette": "rails", "slices": render(slices)}
    with open(os.path.join(OUT, name + ".json"), "w") as f:
        json.dump(part, f, indent=2)
        f.write("\n")


def ew_tunnel(wall="y", roof="y", lights=True):
    """Six-slice east-west tunnel filling the whole chunk: walls at z=0 and z=15, 14 wide inside."""
    slices = []
    s = blank(); fill(s, "y"); slices.append(s)                      # 0 floor
    for y in range(1, 5):                                            # 1 rail row, 2..4 headroom
        s = blank(); fill(s, "~")
        fill(s, wall, zs=(0, 15))
        if y == 1:
            fill(s, "y", zs=(0, 15))
        if y == 4 and lights:
            for x in (2, 13):
                s[0][x] = "h"; s[15][x] = "h"
        slices.append(s)
    s = blank(); fill(s, roof); fill(s, "y", zs=(0, 15)); slices.append(s)  # 5 ceiling
    return slices


def with_platforms(slices, rows=PLATFORMS):
    for z in rows:
        fill(slices[1], "Q", zs=(z,))
    return slices


def rails_horizontal():
    write("rails_horizontal", ew_tunnel())


def rails_horizontal_water():
    write("rails_horizontal_water", ew_tunnel(wall="+", roof="+", lights=False))


def rails_horizontal_end():
    slices = ew_tunnel()
    for y in range(1, 5):                                            # buffer wall after the track ends at x=10
        fill(slices[y], "y", xs=range(11, SIZE), zs=range(1, 15))
    write("rails_horizontal_end", slices)


def ns_tunnel(wall="y", roof="y", lights=True):
    """North-south connector, track at x=8: walls at x=6 and x=10 (Lost Cities' own footprint)."""
    xs = range(6, 11)
    slices = []
    s = blank(); fill(s, "y", xs=xs); slices.append(s)
    for y in range(1, 5):
        s = blank(); fill(s, "~", xs=range(7, 10))
        fill(s, "y" if y == 1 else wall, xs=(6, 10))
        if y == 4 and lights:
            s[4][6] = "h"; s[11][10] = "h"
        slices.append(s)
    s = blank(); fill(s, roof, xs=xs); slices.append(s)
    return slices


def rails_vertical():
    write("rails_vertical", ns_tunnel())


def rails_vertical_water():
    write("rails_vertical_water", ns_tunnel(wall="+", roof="+", lights=False))


def junction_hall(open_west, open_east):
    """Full-chunk hall for bends and splits. Curves need the whole quadrant between lanes and the connector."""
    slices = []
    s = blank(); fill(s, "y"); slices.append(s)
    for y in range(1, 5):
        s = blank(); fill(s, "~")
        wall = "y"
        fill(s, wall, zs=(0, 15), xs=[x for x in range(SIZE) if x not in range(6, 11)])
        if not open_west:
            fill(s, wall, xs=(0,), zs=range(SIZE))
        if not open_east:
            fill(s, wall, xs=(15,), zs=range(SIZE))
        if y == 4:
            s[0][2] = "h"; s[15][2] = "h"; s[0][13] = "h"; s[15][13] = "h"
        slices.append(s)
    s = blank(); fill(s, "y"); slices.append(s)
    return slices


def rails_bend():
    write("rails_bend", junction_hall(open_west=True, open_east=False))


def rails_3split():
    write("rails_3split", junction_hall(open_west=True, open_east=True))


def ramp(name, profile):
    """profile[x] = slice of the track at column x. Floor below the track, five rows of air above it."""
    height = max(profile) + 5
    slices = [blank() for _ in range(height)]
    for x, t in enumerate(profile):
        for z in range(SIZE):
            wall = z in (0, 15)
            slices[t - 1][z][x] = "y"
            for y in range(t, t + 4):
                slices[y][z][x] = "y" if wall else "~"
            slices[t + 4][z][x] = "y"
    write(name, slices)


def rails_down2():
    # Lost Cities rails_down2: flat at x=0, 1:1 slope x=1..12, flat x=13..15 twelve blocks higher.
    ramp("rails_down2", [1] + [x for x in range(1, 13)] + [13, 13, 13])


def rails_down1():
    # Lost Cities rails_down1: 1:1 slope x=0..5, flat x=6..15 six blocks higher.
    ramp("rails_down1", [x + 1 for x in range(6)] + [7] * 10)


def station_underground():
    write("station_underground", with_platforms(ew_tunnel()))


def station_underground_stairs():
    slices = with_platforms(ew_tunnel())
    # Lost Cities stacks station_staircase parts above this one; their stairs come down at z=13..14
    # (x=8 in the slice just above). Continue them down to the south platform, clear of lane 11 (z=10..12).
    for y, x in ((5, 8), (4, 7), (3, 6), (2, 5)):
        for z in (13, 14):
            slices[y][z][x] = "="
            for above in range(y + 1, 6):
                slices[above][z][x] = "~"
    for z in (13, 14):
        for x in range(4, 8):
            slices[5][z][x] = "~"
    write("station_underground_stairs", slices)


def open_station_base():
    floor = blank(); fill(floor, "y")
    rail_row = blank(); fill(rail_row, "~"); fill(rail_row, "y", zs=(0, 15))
    slices = [floor, rail_row]
    return with_platforms(slices)


def station_open():
    write("station_open", open_station_base())


def station_openroof():
    slices = open_station_base()
    for _ in range(3):                                               # pillars, as in Lost Cities
        s = blank()
        for z in (0, 15):
            s[z][3] = "w"; s[z][11] = "w"
        slices.append(s)
    s = blank(); fill(s, ")", zs=(0,)); fill(s, "(", zs=(15,)); slices.append(s)
    s = blank(); fill(s, ")", zs=(1,)); fill(s, "(", zs=(14,))
    for z in range(2, 14):
        fill(s, "+", zs=(z,))
        s[z][3] = "Q"; s[z][11] = "Q"
    slices.append(s)
    write("station_openroof", slices)


def main():
    os.makedirs(OUT, exist_ok=True)
    for gen in (rails_horizontal, rails_horizontal_water, rails_horizontal_end, rails_vertical,
                rails_vertical_water, rails_bend, rails_3split, rails_down1, rails_down2,
                station_underground, station_underground_stairs, station_open, station_openroof):
        gen()
    print("Wrote parts to", OUT)


if __name__ == "__main__":
    main()
