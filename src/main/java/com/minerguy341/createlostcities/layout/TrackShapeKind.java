package com.minerguy341.createlostcities.layout;

/** The subset of Create's {@code TrackShape} the subway uses. */
public enum TrackShapeKind {
    /** Straight along X ({@code x_ortho}). */
    XO,
    /** Straight along Z ({@code z_ortho}). */
    ZO,
    /** One-block-per-block slope rising towards +X. */
    AE,
    /** One-block-per-block slope rising towards -X. */
    AW;

    public TrackShapeKind mirrorX() {
        return switch (this) {
            case AE -> AW;
            case AW -> AE;
            default -> this;
        };
    }
}
