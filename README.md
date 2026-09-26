# Create: Lost Cities Subways

A Forge 1.20.1 compatibility mod that turns [The Lost Cities](https://modrinth.com/mod/the-lost-cities) subway network into working [Create](https://modrinth.com/mod/create) railway:

- Tunnels, ramps, junctions and stations become Create-scale: lanes are spaced for three-wide trains, with platforms and stairs.
- Every track is placed through Create's own API, so the whole network forms one `TrackGraph` that trains can pathfind across, including chunk borders and junction curves.
- Every Lost Cities station chunk gets a named `create:track_station` per platform track, for example `Ravenford Junction P2`.

Highways and monorails are not touched.

| | |
|---|---|
| Minecraft | 1.20.1 (Forge 47+) |
| Create | 6.0.8 – 6.0.x |
| The Lost Cities | 1.20-7.5+ |

The full design, version matrix and test plan are in [docs/IMPLEMENTATION_PLAN.md](docs/IMPLEMENTATION_PLAN.md).

## How it works

1. A built-in datapack, registered at the top of the pack stack, overrides Lost Cities' railway parts (`lostcities:rails_*`, `lostcities:station_*`) with wider tunnels and platforms. These parts contain **no track**. Create track written during world generation never registers with Create's rail graph, and curves cannot be authored in a structure.
2. When a newly generated railway chunk and all eight of its neighbours are loaded, the stitcher does the following:
   - reads the chunk's rail type from Lost Cities;
   - lays straight and sloped track with normal block placement, which Create registers itself;
   - builds the bezier curves at bends and splits;
   - places and binds the stations.
3. Stations are named as soon as Create has registered them.

## Commands (op level 2)

- `/createlostcities info`: the Lost Cities rail data, the planned track and the stitch status for your chunk.
- `/createlostcities stitch [radius]`: force-stitch loaded railway chunks around you. Use it for retrofitting or debugging.
- `/createlostcities graph`: lists Create's track graphs with their node bounds and station names.
- `/createlostcities spike`: builds a test track, curve and station at your feet in any world.

## Config

`config/create_lostcities-common.toml` has these options:

- `enabled`
- `installCreateScaleParts`
- `dimensions`
- `placeStations`
- `carveClearance`
- `removeVanillaRails`
- `retrofitExistingChunks`
- `maxChunksPerTick`
- `stationNameSingle` / `stationNameMulti`

## Building

```
./gradlew build          # jar in build/libs
./gradlew test           # layout unit tests
python3 tools/generate_parts.py   # regenerate the datapack parts
```

## Limitations

- Only newly generated chunks get Create-scale tunnels. Retrofit mode lays track through the old tunnels.
- Like Lost Cities' own rails, the lanes form several separate lines. The centre lane of a 3-track row is an express line, and each outer lane loops through the connectors. Platforms of one station can therefore sit on different Create graphs.
- Junctions have no signals. Add your own signals before running several trains on one connector.
- Tunnels leave 3 blocks of headroom above the track.
