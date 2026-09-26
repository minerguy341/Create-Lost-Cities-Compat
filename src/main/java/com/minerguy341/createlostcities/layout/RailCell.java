package com.minerguy341.createlostcities.layout;

import mcjty.lostcities.api.RailChunkType;

/**
 * Everything the layout needs to know about one Lost Cities railway chunk.
 *
 * @param type        the Lost Cities rail chunk type
 * @param direction   which way directional parts face (EAST parts are mirrored along X)
 * @param level       Lost Cities city level of the part (railway level is -3 by default)
 * @param rails       number of tracks (1-3) Lost Cities assigned to this piece of line
 * @param groundLevel the ground level for this chunk's profile
 */
public record RailCell(RailChunkType type, RailDir direction, int level, int rails, int groundLevel) {

    /** Lost Cities' {@code LostCityTerrainFeature.FLOORHEIGHT}. */
    public static final int FLOOR_HEIGHT = 6;

    public static final RailCell NONE = new RailCell(RailChunkType.NONE, RailDir.BI, 0, 0, 0);

    /** Y of the part origin, as used by {@code Railways.generateRailways}. */
    public int partBaseY() {
        return groundLevel + level * FLOOR_HEIGHT;
    }

    /** Y of a flat track block: the rail row of every Lost Cities railway part is slice 1. */
    public int trackY() {
        return partBaseY() + 1;
    }

    public boolean isNone() {
        return type == RailChunkType.NONE;
    }

    public boolean isJunctionOwner() {
        return type == RailChunkType.DOUBLE_BEND || type == RailChunkType.THREE_SPLIT;
    }
}
