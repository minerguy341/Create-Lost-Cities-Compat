package com.minerguy341.createlostcities.layout;

import mcjty.lostcities.api.RailChunkType;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure function from a Lost Cities rail chunk (plus its north/south neighbours) to the Create
 * tracks, curves and stations that chunk should contain.
 *
 * <p>Everything is authored in the canonical orientation Lost Cities uses for its parts: east-west
 * lines run along X, and directional parts (ramps, bends, splits, ends, north-south connectors) are
 * authored for {@link RailDir#WEST} and mirrored along X for {@link RailDir#EAST}, exactly like
 * {@code Transform.MIRROR_X} in {@code Railways.generateRailways}.
 *
 * <p>Lane layout (chunk-local Z): Lost Cities packs three minecart rails at z=5/7/9. Create trains
 * are three blocks wide, so the lanes are spread to z=3/7/11 (four apart, one block of platform
 * between trains). The north-south connector stays on Lost Cities' column x=8.
 */
public final class SubwayLayout {

    public static final int LANE_NORTH = 3;
    public static final int LANE_CENTER = 7;
    public static final int LANE_SOUTH = 11;
    /** Column of the north-south connector line (before mirroring). */
    public static final int CONNECTOR_X = 8;
    /**
     * How far a junction curve reaches into the neighbouring north/south chunk. The curve ends at
     * local z=-5 (north) or z=19 (south), giving a 7.5 block turn radius, which clears Create's
     * own 7 block minimum for a 90 degree turn.
     */
    public static final int CURVE_REACH = 5;
    /** Chunk-local Z of a junction curve's end block in the chunk to the north (neighbour local z=11). */
    public static final int NORTH_CURVE_END_Z = -CURVE_REACH;
    /** Chunk-local Z of a junction curve's end block in the chunk to the south (neighbour local z=3). */
    public static final int SOUTH_CURVE_END_Z = 15 + CURVE_REACH - 1;

    private SubwayLayout() {
    }

    /** Lane Z coordinates for a given Lost Cities rail count (mirrors the rail reduction in Railways). */
    public static int[] lanes(int rails) {
        return switch (rails) {
            case 1 -> new int[]{LANE_CENTER};
            case 2 -> new int[]{LANE_NORTH, LANE_SOUTH};
            case 3 -> new int[]{LANE_NORTH, LANE_CENTER, LANE_SOUTH};
            default -> new int[0];
        };
    }

    public static ChunkPlan plan(RailCell cell, RailCell north, RailCell south) {
        ChunkPlan canonical = switch (cell.type()) {
            case NONE -> ChunkPlan.EMPTY;
            case HORIZONTAL, STATION_EXTENSION_SURFACE, STATION_EXTENSION_UNDERGROUND -> straight(cell, 0, 15, false);
            case STATION_SURFACE, STATION_UNDERGROUND -> straight(cell, 0, 15, true);
            case RAILS_END_HERE -> straight(cell, 0, 10, false);
            case GOING_DOWN_TWO_FROM_SURFACE, GOING_DOWN_FURTHER -> ramp(cell, 2);
            case GOING_DOWN_ONE_FROM_SURFACE -> ramp(cell, 1);
            case VERTICAL -> vertical(cell, north, south);
            case DOUBLE_BEND -> doubleBend(cell, north, south);
            case THREE_SPLIT -> threeSplit(cell, north, south);
        };
        return isDirectional(cell.type()) && cell.direction() == RailDir.EAST ? canonical.mirrorX() : canonical;
    }

    /** Types whose Lost Cities part is mirrored for EAST. Straight and station parts are symmetric. */
    public static boolean isDirectional(RailChunkType type) {
        return switch (type) {
            case RAILS_END_HERE, VERTICAL, THREE_SPLIT, DOUBLE_BEND,
                 GOING_DOWN_ONE_FROM_SURFACE, GOING_DOWN_TWO_FROM_SURFACE, GOING_DOWN_FURTHER -> true;
            default -> false;
        };
    }

    private static ChunkPlan straight(RailCell cell, int fromX, int toX, boolean station) {
        int y = cell.trackY();
        int[] lanes = lanes(cell.rails());
        List<TrackPiece> tracks = new ArrayList<>();
        List<StationSpot> stations = new ArrayList<>();
        for (int lane : lanes) {
            for (int x = fromX; x <= toX; x++) {
                tracks.add(new TrackPiece(x, y, lane, TrackShapeKind.XO));
            }
        }
        if (station) {
            for (int lane : lanes) {
                stations.add(stationFor(lane, y));
            }
        }
        return new ChunkPlan(tracks, List.of(), stations);
    }

    /**
     * Platform positions match the Create-scale station parts: an outer platform at z=1, islands at
     * z=5 and z=9, and a wide outer platform at z=13..14 that also holds the staircase (x=4..8), which
     * is why the south station sits at x=12.
     */
    static StationSpot stationFor(int lane, int y) {
        return switch (lane) {
            case LANE_NORTH -> new StationSpot(8, 1, 8, LANE_NORTH, y, 1);
            case LANE_CENTER -> new StationSpot(8, 5, 8, LANE_CENTER, y, 2);
            case LANE_SOUTH -> new StationSpot(12, 13, 12, LANE_SOUTH, y, 3);
            default -> throw new IllegalArgumentException("Unknown lane " + lane);
        };
    }

    /**
     * Ramps copy the rail profile of Lost Cities' rails_down1/rails_down2 parts, which are placed one
     * or two levels below the chunk they climb towards and rise towards +X (canonical WEST).
     * rails_down2: flat at x=0, slope x=1..12, flat x=13..15 twelve blocks higher.
     * rails_down1: slope x=0..5, flat x=6..15 six blocks higher.
     */
    private static ChunkPlan ramp(RailCell cell, int levels) {
        int y0 = cell.trackY();
        List<TrackPiece> tracks = new ArrayList<>();
        for (int lane : lanes(cell.rails())) {
            if (levels == 2) {
                tracks.add(new TrackPiece(0, y0, lane, TrackShapeKind.XO));
                for (int x = 1; x <= 12; x++) {
                    tracks.add(new TrackPiece(x, y0 + x - 1, lane, TrackShapeKind.AE));
                }
                for (int x = 13; x <= 15; x++) {
                    tracks.add(new TrackPiece(x, y0 + 12, lane, TrackShapeKind.XO));
                }
            } else {
                for (int x = 0; x <= 5; x++) {
                    tracks.add(new TrackPiece(x, y0 + x, lane, TrackShapeKind.AE));
                }
                for (int x = 6; x <= 15; x++) {
                    tracks.add(new TrackPiece(x, y0 + 6, lane, TrackShapeKind.XO));
                }
            }
        }
        return new ChunkPlan(tracks, List.of(), List.of());
    }

    /**
     * North-south connector. Where the neighbour is a junction its curve ends inside this chunk
     * (z=11 when the junction is south, z=3 when it is north), so the straight run stops one block
     * short and leaves that end block to the curve owner.
     */
    private static ChunkPlan vertical(RailCell cell, RailCell north, RailCell south) {
        int y = cell.trackY();
        int fromZ = north.isJunctionOwner() ? SOUTH_CURVE_END_Z - 16 + 1 : 0;
        int toZ = south.isJunctionOwner() ? NORTH_CURVE_END_Z + 16 - 1 : 15;
        List<TrackPiece> tracks = new ArrayList<>();
        for (int z = fromZ; z <= toZ; z++) {
            tracks.add(new TrackPiece(CONNECTOR_X, y, z, TrackShapeKind.ZO));
        }
        return new ChunkPlan(tracks, List.of(), List.of());
    }

    /**
     * rails_bend: the two outer lanes arrive from the west edge and turn north/south onto the
     * connector. Curves are only laid when the neighbour really is a connector (RAILWAYS_CAN_END can
     * leave a bend without one).
     */
    private static ChunkPlan doubleBend(RailCell cell, RailCell north, RailCell south) {
        int y = cell.trackY();
        List<CurvePiece> curves = new ArrayList<>();
        if (north.type() == RailChunkType.VERTICAL) {
            curves.add(new CurvePiece(0, LANE_NORTH, 1, 0, CONNECTOR_X, NORTH_CURVE_END_Z, 0, 1, y));
        }
        if (south.type() == RailChunkType.VERTICAL) {
            curves.add(new CurvePiece(0, LANE_SOUTH, 1, 0, CONNECTOR_X, SOUTH_CURVE_END_Z, 0, -1, y));
        }
        return new ChunkPlan(List.of(), curves, List.of());
    }

    /**
     * rails_3split: the centre lane runs straight through; on the east side the two outer lanes
     * branch off onto the north and south connectors.
     */
    private static ChunkPlan threeSplit(RailCell cell, RailCell north, RailCell south) {
        int y = cell.trackY();
        List<TrackPiece> tracks = new ArrayList<>();
        for (int x = 0; x <= 15; x++) {
            tracks.add(new TrackPiece(x, y, LANE_CENTER, TrackShapeKind.XO));
        }
        List<CurvePiece> curves = new ArrayList<>();
        if (north.type() == RailChunkType.VERTICAL) {
            curves.add(new CurvePiece(CONNECTOR_X, NORTH_CURVE_END_Z, 0, 1, 15, LANE_NORTH, -1, 0, y));
        }
        if (south.type() == RailChunkType.VERTICAL) {
            curves.add(new CurvePiece(CONNECTOR_X, SOUTH_CURVE_END_Z, 0, -1, 15, LANE_SOUTH, -1, 0, y));
        }
        return new ChunkPlan(tracks, curves, List.of());
    }
}
