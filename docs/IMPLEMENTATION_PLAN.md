# Create: Lost Cities Subways: Implementation Plan

Goal: turn The Lost Cities (LC) subway network into Create train infrastructure you can ride.
"Replace" means two things:

1. Every generated subway track joins Create's `TrackGraph`, so trains can pathfind across the whole network.
2. Every real LC station chunk gets a named `create:track_station` bound to a straight platform track.

Swapping blocks so the tunnels only look like Create track does not meet that bar. LC highways and monorails are left alone.

Research snapshot date: 2026-09-26. Sources: the Modrinth v2 API, CurseForge listings (via cfwidget), `McJtyMods/LostCities` branch `1.20` (7.5.5), and `Creators-of-Create/Create` branch `mc1.20.1/dev` (6.0.8).

---

## 1. Version matrix

### 1.1 Minecraft versions where both mods have builds

Create Fabric is a separate project (`create-fabric`). A Fabric/Quilt row counts only if LC also has a Fabric build for that exact Minecraft version.

| MC | Loader | Create (latest file) | The Lost Cities (latest file) | Verdict |
|---|---|---|---|---|
| **1.20.1** | **Forge** | `create-1.20.1-6.0.8.jar` (6.0.8, 2025-11-02) | `lostcities-1.20-7.5.5.jar` (2026-09-12) | **recommended (primary)** |
| 1.20.1 | NeoForge (1.20.1 fork) | same 6.0.8 jar, also tagged NeoForge | only tagged Forge | partial: depends on the NeoForge 47.1 fork loading LC's Forge jar; untested |
| 1.20.1 | Fabric / Quilt | Create Fabric `6.0.8.1+build.1744-mc1.20.1` | none | not viable |
| **1.21.1** | **NeoForge** | `create-1.21.1-6.0.10.jar` (6.0.10, 2026-04-21) | `lostcities-1.21-8.4.4.jar` (2026-09-12) | **recommended (secondary port)** |
| 1.19.2 | Forge | `create-1.19.2-0.5.1.i.jar` (2024-10-09) | `lostcities-1.19-6.2.2.jar` (2024-08-14) | possible: Create 0.5.1 trains, older track API, both lines frozen |
| 1.19.2 | Fabric / Quilt | Create Fabric `0.5.1-i-build.1630` | none | not viable |
| 1.18.2 | Forge | `create-1.18.2-0.5.1.i.jar` (2024-10-09) | `lostcities-1.18-5.3.29.jar` (2024-05-24) | possible: same caveats as 1.19.2, older LC asset format |
| 1.18.2 | Fabric / Quilt | Create Fabric `0.5.1-i-build.1629` | none | not viable |
| 1.18.1 | Forge | `create-mc1.18.1_v0.4f.jar` (CurseForge only) | `lostcities-1.18-5.0.4.jar` | not viable: Create 0.4 has no trains |
| 1.16.5 / 1.16.3-4 / 1.15.2 / 1.14.4 | Forge | Create 0.2–0.3.2g (CurseForge only) | LC 2.0.28–4.0.12 | not viable: no trains |

### 1.2 Nearby versions where only one mod exists (do not target by accident)

| MC | Create | Lost Cities |
|---|---|---|
| 1.19, 1.19.1, 1.19.3, 1.19.4 | — | Forge 6.0.3 / 6.1.6 / 6.2.3 |
| 1.20.2 – 1.20.6 | — | — (LC has a `1.20.4_neo` git branch, no published file) |
| 1.21 | — | NeoForge 8.2.2 |
| 1.21.11 | — | NeoForge 9.5.4 |
| 26.1.2 | — | NeoForge 10.0.4 |
| 26.2 | — | NeoForge 11.0.1 **and Fabric** 11.0.1 (LC's first Fabric build; Create has none) |

### 1.3 Targets

**Primary: 1.20.1 Forge, Create 6.0.8, Lost Cities 7.5.x.**

- **Pack popularity.** Modrinth downloads per game version: Create has 16.8 M on 1.20.1 and 8.0 M on 1.21.1. Lost Cities has 9.0 M on 1.20.1 and 89 k on 1.21.1. Nearly every existing "Create + Lost Cities" pack is on 1.20.1.
- **Create 6 trains.** 6.0.8 is the latest release on the 1.20 line, with the same train, track, and station code as 1.21.1. The classes this mod uses (`TrackBlock`, `TrackBlockEntity`, `BezierConnection`, `TrackPropagator`, `StationBlockEntity`) did not change between the two lines.
- **API stability.** Both mods are feature-frozen on 1.20.1 apart from fixes. LC 7.5.5 shipped this month. Create's 1.20.1 branch now only gets backports.
- **LC datapack assets.** 1.20.1 LC loads parts through a datapack registry (`data/<ns>/lostcities/parts/*.json`, `CustomRegistries.PART_REGISTRY_KEY`), so the parts can be overridden by id from a datapack. Railway part names are selected through `PartSelector.railwayParts()`.

**Secondary: 1.21.1 NeoForge, Create 6.0.10, LC 8.4.x.** Same Create 6 API and the same LC railway code (`Railway`, `Railways`, `RailwayParts`). This port only changes the loader glue (NeoForge events and `ModConfigSpec`), the `AddPackFindersEvent` signature, and `Pack.readMetaAndCreate`, which changed in 1.20.5. It is where newer Create packs are heading.

Fabric is not a target: LC has no Fabric build on any version that Create Fabric supports.

---

## 2. Lost Cities research

### 2.1 Grid and generation

- The station grid repeats every `2 × spacing` chunks. Spacing is `profile.RAILWAY_SPACING_EAST_WEST` / `RAILWAY_SPACING_NORTH_SOUTH`: default 10, even values from 4 to 128.
  With `mx = floorMod(chunkX + 1, 2·sx)` and `mz = floorMod(chunkZ + 1, 2·sz)`:
  - `(0, sz)`: 3-track station
  - `(sx, 0)`: 2-track station
  - `(sx, sz)`: 1-track station
  - `(0, 0)`: nothing
- **Stations are city-only** (`BuildingInfo.isCityRaw`). A grid point outside a city becomes a `HORIZONTAL` through-track with the same rail count, or, with `RAILWAYS_CAN_END`, a `RAILS_END_HERE` / nothing when there is no city on either side.
- East-west lines run along rows `mz = 0` (2 tracks) and `mz = sz` (1 or 3 tracks). North-south connectors are single-track `VERTICAL` columns at `mx = sx/2` and `mx = sx + sx/2`. They join the rows through `DOUBLE_BEND` (at `mz = 0`) and `THREE_SPLIT` (at `mz = sz`). Parts face `WEST` and are mirrored along X for `EAST`.
- **Depth:** `Railway.RAILWAY_LEVEL_OFFSET = -3` plus `profile.RAILWAY_LEVEL_OFFSET` (range −8 to 2) gives the railway city level. Part origin Y is `groundLevel + level·6` (`FLOORHEIGHT = 6`), and the rail row is slice 1.
- **Surface vs underground stations** (`Railway.getStationType`):
  - Underground if city level > 2, the descent is too steep (`cityLevel − railLevel > spacingEW − 2`), `RAILWAY_SURFACE_STATIONS_ENABLED` is off, or a highway is on or next to the chunk.
  - Otherwise a 50/50 roll between `STATION_SURFACE` (at city level; `stationopen` or `stationopenroof` 50/50) and `STATION_UNDERGROUND` (`stationundergroundstairs` plus a `stationstaircase` per level plus `stationstaircasesurface`).
- **Approach ramps** (`testAdjacentRailChunk`):
  - Next to a surface station: `STATION_EXTENSION_SURFACE` (40%), then `GOING_DOWN_ONE/TWO_FROM_SURFACE`, then `GOING_DOWN_FURTHER` in 2-level steps back to railway level.
  - Next to an underground station: `STATION_EXTENSION_UNDERGROUND` (40%) or horizontal.
- **Rail counts 1–3** are carried along each line. `Railways.generateRailways` reduces a 3-rail part by overwriting unused lanes with the style's rail main block: z=5 and z=9 for 1 rail, z=7 for 2 rails.
- **`RAILWAYS_CAN_END`** (default false) removes line segments that have no city at either end, which leaves gaps and `RAILS_END_HERE` buffers.
- **Other ways the network gets cut:**
  - City-sphere borders set the chunk to `NOTHING` (`CitySphere.onCitySphereBorder`).
  - `railwayavoidance = BLOCK_RAILWAY` world styles delete a rail chunk under tall cellars (`Railway.removeRailChunkType`).

### 2.2 Java entry points

- `LostCityTerrainFeature` → `Railways.generateRailways(feature, info, railInfo, heightmap)` for each chunk whose `info.getRailInfo()` is not `NONE`.
- `Railway.getRailChunkType(ChunkCoord, IDimensionInfo, LostCityProfile)` returns `RailChunkInfo(type, direction, level, rails, part)`. It is cached in `RAIL_INFO` and forced to `NOTHING` by the profile flags below.
- `api.RailChunkType`: `NONE, STATION_SURFACE, STATION_UNDERGROUND, STATION_EXTENSION_SURFACE, STATION_EXTENSION_UNDERGROUND, GOING_DOWN_TWO_FROM_SURFACE, GOING_DOWN_ONE_FROM_SURFACE, GOING_DOWN_FURTHER, HORIZONTAL, THREE_SPLIT, VERTICAL, DOUBLE_BEND, RAILS_END_HERE`.
- `Railway.RAILWAY_LEVEL_OFFSET = -3`; `Railway.getRailwayLevel(profile)`.
- Public API: `ILostChunkInfo.getRailType()/getRailLevel()`. It does **not** expose direction or rail count, so the mod reads `BuildingInfo`/`Railway` through a single bridge class.

### 2.3 Datapack part ids (`parts.railways` in the world style's `partselector`)

| selector key | default part | used for |
|---|---|---|
| `stationunderground` | `station_underground` | underground extensions, and surface stations below city level |
| `stationopen` / `stationopenroof` | `station_open` / `station_openroof` | surface stations |
| `stationundergroundstairs` | `station_underground_stairs` | underground stations |
| `stationstaircase` / `stationstaircasesurface` | `station_staircase` / `station_staircase_surface` | shaft up to the street |
| `railshorizontal` / `railshorizontalwater` / `railshorizontalend` | `rails_horizontal` / `_water` / `_end` | through-track, submerged variant, `RAILS_END_HERE` |
| `railsvertical` / `railsverticalwater` | `rails_vertical` / `_water` | north-south connectors |
| `railsbend` | `rails_bend` | `DOUBLE_BEND` |
| `rails3split` | `rails_3split` | `THREE_SPLIT` |
| `railsdown1` / `railsdown2` | `rails_down1` (12 slices) / `rails_down2` (18 slices) | ramps |
| `railsflat` | `rails_flat` | fallback only |

The `_water` variants are chosen when six sample points around the tunnel are all liquid.

### 2.4 Profile flags

`RAILWAYS_ENABLED`, `RAILWAY_STATIONS_ENABLED`, `RAILWAY_SURFACE_STATIONS_ENABLED`, `RAILWAYS_CAN_END`, `RAILWAY_LEVEL_OFFSET`, `RAILWAY_SPACING_*`, `RAILWAY_DUNGEON_CHANCE`.

Turning off stations also cuts the lines next to them, because `testAdjacentRailChunk(NONE)` returns `NOTHING`.

---

## 3. Create research

### 3.1 Why `create:track` is not a normal rail

- A track block is only the local shape (`TrackShape`: `XO`, `ZO`, diagonals, `AE`/`AW`/`AN`/`AS` 1:1 slopes, crossings). Trains do not follow blocks. They follow a `TrackGraph` of nodes and edges held by `GlobalRailwayManager` (`Create.RAILWAYS`) and saved in `create_tracks.dat`.
- A block joins a graph only when `TrackBlock.onPlace` schedules a tick and `TrackBlock.tick` calls `TrackPropagator.onRailAdded`, which walks the connected track and creates or merges nodes. On straight track, nodes are forced at every x or z ≡ 0 (mod 16), which is every chunk border.
- Curves are not blocks. They are `BezierConnection`s stored in a `TrackBlockEntity` at **both** ends (block state `turn=true`, i.e. `HAS_BE`). Each end stores the other end's **absolute** `BlockPos`, plus start points, axes, normals and material. `TrackBlockEntity.validateConnections` deletes any connection whose other end has no matching block entity.

**Why putting `create:track` in a Lost Cities part yields dead or corrupted rails:**

1. Parts are written into a proto-chunk during world generation. `onPlace` never runs, so no tick is scheduled and the tracks never enter the graph. They look fine but trains cannot path over them ("dead rails").
2. A part cannot author a curve. The block entity NBT needs absolute world positions, and LC palettes only carry block states.
3. Even with NBT, the other end is usually in a chunk that has not been generated. `validateConnections` then strips the curve, or a half-connected block entity is left behind ("corrupted rails").
4. LC post-processes parts: rail-count reduction, explosion and ruin damage, and the `railMainBlock` overwrite. All of these can clobber track blocks.
5. Similar breakage has been reported with Create addons and schematic or structure pipelines that copy track block-entity NBT to another position: curves keep their old absolute keys.

### 3.2 The intended placement path

- `TrackPlacement.tryConnect` validates a curve: `too_sharp` if a 90° turn is under 7 blocks, plus slope rules. `TrackPlacement.placeTracks` then places both end blocks with `HAS_BE`, creates `new BezierConnection(Couple(pos1,pos2), Couple(start1,start2), Couple(axis1,axis2), Couple(normal1,normal2), primary, girder, material)` (with `start = TrackBlock.getCurveStart(pos, axis)`, axes pointing into the curve), and calls `addConnection` on both block entities (`curve.secondary()` on the second).
- This mod does the same thing on a loaded `ServerLevel`: `TrackPlacer.placeStraight` / `placeCurve`.

### 3.3 Stations

- A station is a `TrackTargetingBehaviour<GlobalStation>` on `StationBlockEntity`. The item writes `TargetTrack` (relative position), `TargetDirection` and `Id`.
- Placement rules from `TrackTargetingBlockItem`:
  - The target must be within **16 blocks**.
  - The target must be track (`NO_TRACK`) and not a graph node or junction (`JUNCTION`).
  - It must not overlap another edge point, such as a signal, observer or another station, within 0.75 blocks (`OCCUPIED`).
- The `GlobalStation` edge point is created lazily in the behaviour's tick once the track is in a graph. Only then does `StationBlockEntity.updateName(String)` work.
- Assembly uses the straight run in front of the station. A 16-block straight platform is enough for a two-bogey carriage, and station plus extension chunks give 32–48 blocks.

### 3.4 Chunk borders

- Straight track across a border joins by adjacency, but `onRailAdded` then walks into the neighbouring chunk.
- A curve needs both of its chunks loaded when it is created.

Rule: **a railway chunk is stitched only when it and all 8 neighbours are loaded.** Every curve in this design reaches at most 5 blocks into one neighbour. Chunks that are waiting are persisted, so nothing is lost when the server restarts or the player walks away.

---

## 4. Architecture

| Approach | Rideable? | Named stations? | Chunk borders | Fragility | Verdict |
|---|---|---|---|---|---|
| A. Datapack-only visual swap (parts with `create:track`) | No: never registered, no curves | No | broken | low | rejected |
| B. **Hybrid: datapack Create-scale tunnels + Java stitcher that places track through Create's own API** | Yes | Yes | handled by the neighbourhood rule | uses LC internals in one bridge class | **chosen** |
| C. Mixin into `Railways.generateRailways` | Only if it still defers the track to post-gen (same problems as A during generation) | Possible | same as B | mixin against LC internals that change every minor | rejected |
| D. Disable LC railways and regenerate from the same grid math | Yes | Yes | same as B | loses LC's building/cellar avoidance and station-to-city sync; duplicates ~300 lines of grid logic | rejected |

**Chosen: B.**

- The datapack overrides LC's railway parts **by id** with tunnels 14 blocks wide, platforms and stairs, and **no track**.
- The Java stitcher asks LC for each chunk's `RailChunkInfo`, computes a pure layout (`SubwayLayout`), and places track, curves and stations on the live level after generation, through the same calls Create itself uses.

**Identity**

- mod id `create_lostcities`, display name **"Create: Lost Cities Subways"**
- package `com.minerguy341.createlostcities`
- license MIT

**Dependencies**

- Required: Minecraft 1.20.1, Forge ≥ 47, `create` [6.0.8, 6.1), `lostcities` ≥ 1.20-7.5.
- Optional: none. Works with any LC profile and any world style that uses the default railway part names.

**Config** (`config/create_lostcities-common.toml`)

| key | default | effect |
|---|---|---|
| `enabled` | true | master switch for stitching |
| `installCreateScaleParts` | true | register the built-in datapack (TOP priority, required) |
| `dimensions` | [] | whitelist; empty = every LC dimension |
| `placeStations` | true | one station per platform track on station chunks |
| `carveClearance` | true | clear a 3×3 gauge above track, never next to fluids |
| `removeVanillaRails` | true | delete leftover vanilla rails in the rail band |
| `retrofitExistingChunks` | false | also stitch chunks generated before install |
| `maxChunksPerTick` | 4 | stitching budget per level |
| `stationNameSingle` / `stationNameMulti` | `{name}` / `{name} P{platform}` | name templates (`{name} {platform} {x} {z}`) |

**Non-goals**

- Highways and monorails.
- Signals and interlocking. Junctions are unsignalled, and Create's pathfinder handles routing.
- Pre-built trains and schedules.
- Rebuilding custom LC world styles that rename railway parts. Track is still placed there, but through the old tunnels.
- Fabric.
- Retrofitting already-generated tunnels to Create scale. The opt-in retrofit only lays track.

**Prior art**

- Datapack and resource-pack tunnel replacements for LC widen the subway and swap vanilla rails for decorative blocks, sometimes including `create:track` blocks. Those tracks are the dead rails described in §3.1: never registered, no curves, no stations.
- Some Create addons and structure-copy tools have been reported to corrupt track NBT when they move curved track.

This plan differs in two ways. No track comes from structures, and every curve is created live between two loaded block entities. The result is a single connected graph with named stations, which is the point of the mod, not wider tunnels.

---

## 5. Implementation phases

### Phase 0: spike (done: `/createlostcities spike`)
Place 32 straight tracks, a 7.5-radius 90° curve built exactly like a junction curve, 16 more straight blocks, and a station bound 8 blocks into the straight section, then name it "Spike Station" once Create creates its edge point.

**Exit criteria:** a train assembled at the station can be driven around the curve and scheduled back to "Spike Station". This works in any world.

### Phase 1: Create-scale parts (done: `tools/generate_parts.py` → `datapacks/subways`)
- Lanes move from LC's z=5/7/9 to **z=3/7/11** (three-wide trains, one-block platforms between them). The connector stays on column x=8.
- Tunnels fill the whole chunk (walls at z=0/15). The rail row stays at slice 1 so every LC height (ramps, surface stations, staircases, `railpartheight6`) still lines up. Headroom is 3 blocks above the track, enough for a bogey plus two rows.
- Stations: platforms at z=1, 5, 9, 13–14. The underground stairs land on the south platform at x=4..8.
- Bends and splits are full-chunk halls so curves have room.
- Ramps follow LC's exact rise profile (down2: flat, 12 × 1:1, flat; down1: 6 × 1:1, flat).
- Water variants use glass walls and roof.
- LC's rail-count reduction now only writes harmless dividers between lanes.
- Registered through `AddPackFindersEvent` as a required pack at `Pack.Position.TOP`, so it always wins over LC's own copies regardless of mod order.

### Phase 2: stitcher (done: `SubwayStitcher`, `SubwayLayout`, `TrackPlacer`)
1. `ChunkEvent.Load` with `isNewChunk()` (or retrofit): if LC reports a rail chunk, add it to `pending` (saved in `SubwaySavedData`). Queue this chunk and any pending neighbour for a check.
2. Level tick: for queued chunks whose 3×3 neighbourhood is loaded, up to `maxChunksPerTick`, compute `SubwayLayout.plan(cell, north, south)` and place:
   - straight or 1:1 slope track with `setBlock(..., UPDATE_ALL)`, so Create's own `onPlace` then `TrackPropagator.onRailAdded` registers it;
   - bezier curves at bends and splits, owned by the junction chunk and ending five blocks into the connector chunk, which leaves those ends to the owner;
   - clearance carving and vanilla-rail cleanup.
3. Existing track is never overwritten, so curve ends, player edits and reruns are safe. Chunks go to `done` so they are never stitched twice.

### Phase 3: stations (done: `StationPlacer`, `StationNames`)
- On `STATION_SURFACE` / `STATION_UNDERGROUND` chunks only (extensions just lengthen the platform), place one `create:track_station` per lane: z=1 for lane 3, z=5 for lane 7, and x=12, z=13 for lane 11.
- Each station targets the straight lane track at the same x. That track is 8+ blocks from the chunk-border nodes, within 16 blocks, and never a junction.
- Names are deterministic from the world seed and chunk (e.g. "Ravenford Junction P2"). They are applied after Create creates the `GlobalStation`: a persisted queue is retried every 20 ticks.

### Phase 4: config, profiles, edge cases (done)
- **Profile integration:** everything comes from `Railway.getRailChunkType`, so spacing, level offset, `RAILWAYS_CAN_END`, disabled stations or surface stations, and city spheres are respected automatically. Chunks LC turns into `NOTHING` get nothing.
- **Water:** track is waterlogged where needed (`ProperWaterloggedBlock.withWater`). Carving never touches blocks next to fluid, and trains ignore terrain collision.
- **Line ends:** `RAILS_END_HERE` track stops at x=10 against a buffer wall.
- **Ramps:** 1:1 `AE`/`AW` slopes that meet the next part's rail row exactly (unit-tested). EAST parts are mirrored like LC's `Transform.MIRROR_X`.
- **Junctions:** a curve is only laid if the neighbour really is a `VERTICAL` connector, since `RAILWAYS_CAN_END` can orphan a bend.
- **City spheres, `BLOCK_RAILWAY` avoidance:** these leave dead ends where LC removed the chunk. Trains treat them as buffers.
- **Commands:** `/createlostcities info` (cell, plan, status) and `/createlostcities stitch [radius]` (forced stitch and retrofit).
- **Failure isolation:** if LC internals change, the bridge logs once and disables stitching instead of crashing world generation.

### Phase 5: in-game test plan and residual risks

**Automated (`./gradlew test`):** layout invariants. Lane spacing ≥ 4; full-width straights; ramps climb exactly to the next part; EAST mirroring; connector trimming matches curve ends; curve legs ≥ 6.5 blocks; stations target straight, non-border, in-range track; one station per lane; `NONE` gives an empty plan.

**Manual test world** (Forge 1.20.1, Create 6.0.8, LC 7.5.5, profile `default`, then `rarecities`, `spheres`, and a custom profile with spacing 4, `railwaysCanEnd=true`, `railwayLevelOffset=-2`):
1. `/createlostcities spike` in a flat world: assemble a one-bogey train, ride the curve, schedule to "Spike Station".
2. Fly 30 chunks through a city with `/createlostcities info` at each station type: stitched status, named stations in the Create train map / station screen.
3. Take a train from a 3-track station through a `THREE_SPLIT`, up a connector, through a `DOUBLE_BEND` into a 2-track station. Check the graph with Create's debug view (F3 + track graph overlay).
4. Surface station: ride down `GOING_DOWN_ONE/TWO` and `GOING_DOWN_FURTHER` ramps to rail level.
5. Under an ocean: water tunnel variant; track waterlogged, not flooded by carving.
6. Save, quit and reload mid-generation. Pending chunks are stitched on the next visit; there are no duplicate stations, and names survive.
7. A dedicated server with two players loading chunks at the edge of view distance: stitching waits for neighbours, and TPS impact stays under 1 ms at the default budget.
8. `retrofitExistingChunks=true` on a pre-existing world: track appears, but in the old narrow tunnels (documented).

**Headless smoke test, already run** (`./gradlew runServer` with Forge 47.3.0, Create 6.0.8, LC 7.5.5, profile `default` on the overworld, seed 4242, a 25×25-chunk area force-loaded, driven over RCON):
- `datapack list` shows `builtin/create_lostcities_subways` above every mod pack.
- 125 railway chunks were stitched automatically. The chunk types read back through `/createlostcities info` match LC's grid exactly: bends at cx=−6/4, cz=−1; splits at cz=−11/9; surface stations at (9,−11) and (−11,9) with down-one / down-two / further ramps.
- `/createlostcities graph`:
  - one graph runs from row cz=−1 lane 3 (z≈−13) through a `DOUBLE_BEND` curve, a connector and a `THREE_SPLIT` curve into row cz=−11 lane 11 (z≈−165), so junction curves and chunk-border joins work;
  - the surface-station rows span the whole loaded width through the ramps.
- Curve block entities are consistent: primary/secondary pairs with mirrored relative `Positions`, straight track continuing past both ends, nothing inside the curve.
- Ramp track shapes and heights were checked block by block. West ramp `ae` from y=54 to 72; east ramp mirrored to `aw`.
- Stations: `TargetTrack` sits 2 blocks away on the lane, and Create has created the edge point (`PrevAxis` set). Names such as "Glenley P1", "Pemfield" and "Linton Docks" were applied without intervention, and the spike station was named too.
- No ERROR lines in the server log.

**Residual risks**
- **LC's topology is not one network.** Create reproduces it faithfully:
  - the centre lane of a 3-track row runs straight through every split, so it forms its own east-west line;
  - each outer lane zig-zags through bends and splits as its own loop.
  Stations on different lanes of the same station chunk can therefore sit on different graphs. A follow-up phase could add S-curve crossovers in station-extension chunks to merge them. This is not done yet.
- **LC internal API drift:** `BuildingInfo.getRailInfo`, `Railway.RailChunkInfo`, and `groundLevel` are not public API. Mitigated by the bridge plus a pinned version range.
- **Create node placement:** junction curve ends are one block away from straight track in the same lane. Create should give them their own nodes. This needs a check that the graph does not merge them oddly.
- **Unsignalled single-track connectors:** two scheduled trains can deadlock on one connector. Players need to add signals, and a future phase could auto-place them.
- **Very tall trains (> 3 blocks above the track) clip tunnel ceilings.** Trains pass through blocks, so this is cosmetic.
- **Custom world styles** with different railway part names get Create track through LC's narrow tunnels.
- **Chunk saves before a stitch:** a chunk generated and unloaded before its neighbours load stays pending (persisted) until it is revisited. Stations appear lazily.
- **1.21.1 port:** `Pack` and `AddPackFindersEvent` signatures and data components on 1.21 change the station NBT path. Budget for a rewrite of `BuiltinDatapack` and `StationPlacer`.
