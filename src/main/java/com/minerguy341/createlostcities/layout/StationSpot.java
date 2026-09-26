package com.minerguy341.createlostcities.layout;

/**
 * A station block and the straight track it targets, in chunk-local coordinates.
 *
 * @param platform 1-based platform number, counted north to south
 */
public record StationSpot(int stationX, int stationZ, int trackX, int trackZ, int y, int platform) {

    public StationSpot mirrorX() {
        return new StationSpot(15 - stationX, stationZ, 15 - trackX, trackZ, y, platform);
    }
}
