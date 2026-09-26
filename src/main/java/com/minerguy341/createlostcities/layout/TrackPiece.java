package com.minerguy341.createlostcities.layout;

/** One straight or sloped track block, in chunk-local coordinates. */
public record TrackPiece(int x, int y, int z, TrackShapeKind shape) {

    public TrackPiece mirrorX() {
        return new TrackPiece(15 - x, y, z, shape.mirrorX());
    }
}
