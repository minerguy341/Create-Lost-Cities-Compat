package com.minerguy341.createlostcities.layout;

import mcjty.lostcities.api.RailChunkType;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubwayLayoutTest {

    private static final int GROUND = 71;
    private static final int RAIL_LEVEL = -3;

    private static RailCell cell(RailChunkType type, RailDir dir, int level, int rails) {
        return new RailCell(type, dir, level, rails, GROUND);
    }

    private static RailCell rail(RailChunkType type, RailDir dir, int rails) {
        return cell(type, dir, RAIL_LEVEL, rails);
    }

    @Test
    void trackYMatchesLostCitiesRailRow() {
        RailCell c = rail(RailChunkType.HORIZONTAL, RailDir.BI, 3);
        assertEquals(GROUND - 18 + 1, c.trackY());
    }

    @Test
    void laneCountFollowsRailCount() {
        for (int rails = 1; rails <= 3; rails++) {
            ChunkPlan plan = SubwayLayout.plan(rail(RailChunkType.HORIZONTAL, RailDir.BI, rails), RailCell.NONE, RailCell.NONE);
            assertEquals(16 * rails, plan.tracks().size());
            assertTrue(plan.tracks().stream().allMatch(t -> t.shape() == TrackShapeKind.XO));
        }
    }

    @Test
    void lanesAreFarEnoughApartForThreeWideTrains() {
        int[] lanes = SubwayLayout.lanes(3);
        for (int i = 1; i < lanes.length; i++) {
            assertTrue(lanes[i] - lanes[i - 1] >= 4);
        }
    }

    @Test
    void straightRunsCoverTheWholeChunkSoBordersJoin() {
        ChunkPlan plan = SubwayLayout.plan(rail(RailChunkType.STATION_EXTENSION_UNDERGROUND, RailDir.WEST, 2), RailCell.NONE, RailCell.NONE);
        for (int lane : SubwayLayout.lanes(2)) {
            assertTrue(plan.tracks().contains(new TrackPiece(0, plan.tracks().get(0).y(), lane, TrackShapeKind.XO)));
            assertTrue(plan.tracks().contains(new TrackPiece(15, plan.tracks().get(0).y(), lane, TrackShapeKind.XO)));
        }
    }

    @Test
    void stationChunksGetOneStationPerTrackWithinCreatesTargetingRange() {
        for (int rails = 1; rails <= 3; rails++) {
            ChunkPlan plan = SubwayLayout.plan(rail(RailChunkType.STATION_UNDERGROUND, RailDir.BI, rails), RailCell.NONE, RailCell.NONE);
            assertEquals(rails, plan.stations().size());
            Set<Integer> platforms = new HashSet<>();
            for (StationSpot s : plan.stations()) {
                assertTrue(platforms.add(s.platform()));
                // Bound track must exist and be straight, the station must not sit on a lane.
                assertTrue(plan.tracks().contains(new TrackPiece(s.trackX(), s.y(), s.trackZ(), TrackShapeKind.XO)));
                assertFalse(isLane(s.stationZ()));
                double dist = Math.hypot(s.stationX() - s.trackX(), s.stationZ() - s.trackZ());
                assertTrue(dist < 16);
                // Not on a graph node: Create puts nodes on chunk borders of straight track.
                assertTrue(s.trackX() > 0 && s.trackX() < 15);
            }
        }
        ChunkPlan extension = SubwayLayout.plan(rail(RailChunkType.STATION_EXTENSION_SURFACE, RailDir.EAST, 3), RailCell.NONE, RailCell.NONE);
        assertTrue(extension.stations().isEmpty());
    }

    private static boolean isLane(int z) {
        for (int lane : SubwayLayout.lanes(3)) {
            if (lane == z) {
                return true;
            }
        }
        return false;
    }

    @Test
    void surfaceStationsUseTheirOwnLevel() {
        ChunkPlan plan = SubwayLayout.plan(cell(RailChunkType.STATION_SURFACE, RailDir.BI, 1, 3), RailCell.NONE, RailCell.NONE);
        assertEquals(GROUND + 6 + 1, plan.tracks().get(0).y());
    }

    @Test
    void twoLevelRampClimbsExactlyToTheNextPart() {
        // A GOING_DOWN_TWO part sits two levels below the station it leads to.
        int stationLevel = 1;
        RailCell ramp = cell(RailChunkType.GOING_DOWN_TWO_FROM_SURFACE, RailDir.WEST, stationLevel - 2, 1);
        RailCell station = cell(RailChunkType.STATION_SURFACE, RailDir.BI, stationLevel, 1);
        List<TrackPiece> tracks = SubwayLayout.plan(ramp, RailCell.NONE, RailCell.NONE).tracks();
        TrackPiece low = at(tracks, 0);
        TrackPiece high = at(tracks, 15);
        assertEquals(ramp.trackY(), low.y());
        assertEquals(station.trackY(), high.y());
        assertContinuous(tracks);
    }

    @Test
    void oneLevelRampClimbsExactlyToTheNextPart() {
        RailCell ramp = cell(RailChunkType.GOING_DOWN_ONE_FROM_SURFACE, RailDir.WEST, 0, 2);
        RailCell upper = cell(RailChunkType.STATION_EXTENSION_SURFACE, RailDir.WEST, 1, 2);
        List<TrackPiece> tracks = SubwayLayout.plan(ramp, RailCell.NONE, RailCell.NONE).tracks();
        assertEquals(ramp.trackY(), at(tracks, 0).y());
        assertEquals(upper.trackY(), at(tracks, 15).y());
        assertContinuous(tracks);
    }

    @Test
    void eastRampsAreMirrored() {
        RailCell ramp = rail(RailChunkType.GOING_DOWN_FURTHER, RailDir.EAST, 1);
        List<TrackPiece> tracks = SubwayLayout.plan(ramp, RailCell.NONE, RailCell.NONE).tracks();
        assertTrue(at(tracks, 15).y() < at(tracks, 0).y(), "EAST ramps rise towards -X");
        assertTrue(tracks.stream().noneMatch(t -> t.shape() == TrackShapeKind.AE));
    }

    /** Walks one lane west to east: a slope rising east is AE, and the next block is one higher. */
    private static void assertContinuous(List<TrackPiece> tracks) {
        int lane = tracks.get(0).z();
        TrackPiece prev = null;
        for (int x = 0; x < 16; x++) {
            TrackPiece t = at(tracks, x, lane);
            if (prev != null) {
                int expected = prev.shape() == TrackShapeKind.AE ? prev.y() + 1 : prev.y();
                assertEquals(expected, t.y(), "discontinuity at x=" + x);
            }
            prev = t;
        }
    }

    private static TrackPiece at(List<TrackPiece> tracks, int x) {
        return at(tracks, x, tracks.get(0).z());
    }

    private static TrackPiece at(List<TrackPiece> tracks, int x, int z) {
        return tracks.stream().filter(t -> t.x() == x && t.z() == z).findFirst().orElseThrow();
    }

    @Test
    void connectorsLeaveTheirEndsToJunctionCurves() {
        RailCell vertical = rail(RailChunkType.VERTICAL, RailDir.WEST, 1);
        RailCell bend = rail(RailChunkType.DOUBLE_BEND, RailDir.WEST, 1);
        RailCell split = rail(RailChunkType.THREE_SPLIT, RailDir.WEST, 3);

        ChunkPlan full = SubwayLayout.plan(vertical, vertical, vertical);
        assertEquals(16, full.tracks().size());

        // Junction to the south: its north-going curve ends in this chunk at z = 16 + NORTH_CURVE_END_Z.
        ChunkPlan southJunction = SubwayLayout.plan(vertical, vertical, bend);
        int endZ = 16 + SubwayLayout.NORTH_CURVE_END_Z;
        assertEquals(endZ, southJunction.tracks().size());
        assertTrue(southJunction.tracks().stream().allMatch(t -> t.z() < endZ && t.x() == SubwayLayout.CONNECTOR_X));

        // Junction to the north: its south-going curve ends in this chunk at z = SOUTH_CURVE_END_Z - 16.
        ChunkPlan northJunction = SubwayLayout.plan(vertical, split, vertical);
        int startZ = SubwayLayout.SOUTH_CURVE_END_Z - 16;
        assertTrue(northJunction.tracks().stream().allMatch(t -> t.z() > startZ));
        assertEquals(15 - startZ, northJunction.tracks().size());
    }

    @Test
    void bendCurvesOnlyTowardsExistingConnectors() {
        RailCell bend = rail(RailChunkType.DOUBLE_BEND, RailDir.WEST, 1);
        RailCell vertical = rail(RailChunkType.VERTICAL, RailDir.WEST, 1);
        assertEquals(2, SubwayLayout.plan(bend, vertical, vertical).curves().size());
        assertEquals(1, SubwayLayout.plan(bend, RailCell.NONE, vertical).curves().size());
        assertEquals(0, SubwayLayout.plan(bend, RailCell.NONE, RailCell.NONE).curves().size());
    }

    @Test
    void curvesReachOnlyTheAdjacentChunkAndMeetTheConnectorColumn() {
        RailCell vertical = rail(RailChunkType.VERTICAL, RailDir.WEST, 1);
        for (RailDir dir : new RailDir[]{RailDir.WEST, RailDir.EAST}) {
            RailCell verticalDir = rail(RailChunkType.VERTICAL, dir, 1);
            int connectorX = SubwayLayout.plan(verticalDir, verticalDir, verticalDir).tracks().get(0).x();
            for (RailChunkType type : new RailChunkType[]{RailChunkType.DOUBLE_BEND, RailChunkType.THREE_SPLIT}) {
                ChunkPlan plan = SubwayLayout.plan(rail(type, dir, 3), vertical, vertical);
                assertEquals(2, plan.curves().size());
                for (CurvePiece c : plan.curves()) {
                    assertTrue(c.ax() >= 0 && c.ax() <= 15 && c.bx() >= 0 && c.bx() <= 15, "curves never leave the column");
                    assertTrue(c.az() >= -16 && c.az() < 32 && c.bz() >= -16 && c.bz() < 32);
                    // One end is on the connector column (outside this chunk), the other on a lane.
                    boolean aOnConnector = c.az() < 0 || c.az() > 15;
                    int connectorEndX = aOnConnector ? c.ax() : c.bx();
                    assertEquals(connectorX, connectorEndX, type + " " + dir);
                    // Axes point into the curve: perpendicular, unit length.
                    assertEquals(0, c.axisAx() * c.axisBx() + c.axisAz() * c.axisBz());
                    assertEquals(1, Math.abs(c.axisAx()) + Math.abs(c.axisAz()));
                }
            }
        }
    }

    @Test
    void curveTurnRadiusIsAtLeastCreatesPlayerMinimum() {
        RailCell vertical = rail(RailChunkType.VERTICAL, RailDir.WEST, 1);
        for (RailChunkType type : new RailChunkType[]{RailChunkType.DOUBLE_BEND, RailChunkType.THREE_SPLIT}) {
            for (CurvePiece c : SubwayLayout.plan(rail(type, RailDir.WEST, 3), vertical, vertical).curves()) {
                // Curve starts sit on the block face the axis points through (TrackBlock.getCurveStart).
                double sax = c.ax() + 0.5 + c.axisAx() * 0.5, saz = c.az() + 0.5 + c.axisAz() * 0.5;
                double sbx = c.bx() + 0.5 + c.axisBx() * 0.5, sbz = c.bz() + 0.5 + c.axisBz() * 0.5;
                double legX = Math.abs(sax - sbx), legZ = Math.abs(saz - sbz);
                assertTrue(Math.min(legX, legZ) >= 6.5, type + " legs " + legX + "/" + legZ);
            }
        }
    }

    @Test
    void splitKeepsTheCentreLaneStraightThrough() {
        ChunkPlan plan = SubwayLayout.plan(rail(RailChunkType.THREE_SPLIT, RailDir.EAST, 3), RailCell.NONE, RailCell.NONE);
        assertEquals(16, plan.tracks().size());
        assertTrue(plan.tracks().stream().allMatch(t -> t.z() == SubwayLayout.LANE_CENTER));
    }

    @Test
    void railsEndHereStopsBeforeTheBufferWall() {
        ChunkPlan west = SubwayLayout.plan(rail(RailChunkType.RAILS_END_HERE, RailDir.WEST, 3), RailCell.NONE, RailCell.NONE);
        assertTrue(west.tracks().stream().allMatch(t -> t.x() <= 10));
        ChunkPlan east = SubwayLayout.plan(rail(RailChunkType.RAILS_END_HERE, RailDir.EAST, 3), RailCell.NONE, RailCell.NONE);
        assertTrue(east.tracks().stream().allMatch(t -> t.x() >= 5));
    }

    @Test
    void noneProducesNothing() {
        assertTrue(SubwayLayout.plan(RailCell.NONE, RailCell.NONE, RailCell.NONE).isEmpty());
    }
}
