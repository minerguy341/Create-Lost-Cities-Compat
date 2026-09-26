package com.minerguy341.createlostcities.layout;

/**
 * A flat bezier connection between two track blocks, in chunk-local coordinates of the chunk that owns it.
 * The end positions may lie outside 0..15 when the curve reaches into a neighbouring chunk.
 * Each axis is a unit vector that points from its end into the curve, which is the convention
 * {@code TrackPlacement} uses when it builds a {@code BezierConnection}.
 */
public record CurvePiece(int ax, int az, int axisAx, int axisAz,
                         int bx, int bz, int axisBx, int axisBz,
                         int y) {

    public CurvePiece mirrorX() {
        return new CurvePiece(15 - ax, az, -axisAx, axisAz, 15 - bx, bz, -axisBx, axisBz, y);
    }

    /** Straight shape that each end block must have so its own axis lines up with the curve. */
    public static TrackShapeKind endShape(int axisX, int axisZ) {
        return axisX != 0 ? TrackShapeKind.XO : TrackShapeKind.ZO;
    }
}
