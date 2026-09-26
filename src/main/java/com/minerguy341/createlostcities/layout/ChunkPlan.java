package com.minerguy341.createlostcities.layout;

import java.util.List;

/** Everything the stitcher places for one railway chunk. */
public record ChunkPlan(List<TrackPiece> tracks, List<CurvePiece> curves, List<StationSpot> stations) {

    public static final ChunkPlan EMPTY = new ChunkPlan(List.of(), List.of(), List.of());

    public boolean isEmpty() {
        return tracks.isEmpty() && curves.isEmpty() && stations.isEmpty();
    }

    ChunkPlan mirrorX() {
        return new ChunkPlan(
                tracks.stream().map(TrackPiece::mirrorX).toList(),
                curves.stream().map(CurvePiece::mirrorX).toList(),
                stations.stream().map(StationSpot::mirrorX).toList());
    }
}
