package com.minerguy341.createlostcities.world;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Places a {@code create:track_station} bound to a track, following what
 * {@code TrackTargetingBlockItem} does when a player places one: the block entity receives a
 * {@code TargetTrack} (relative to the station) and {@code TargetDirection}. Create then creates the
 * {@link GlobalStation} edge point on the station's first tick once the target track is part of a
 * graph, and only then can it be named, so naming is deferred (see {@link SubwaySavedData}).
 */
public final class StationPlacer {

    /** Create refuses to bind a station to track further away than this (TrackTargetingBlockItem). */
    public static final int MAX_TARGET_DISTANCE = 16;

    private StationPlacer() {
    }

    public static boolean place(ServerLevel level, BlockPos stationPos, BlockPos trackPos) {
        if (!stationPos.closerThan(trackPos, MAX_TARGET_DISTANCE)) {
            return false;
        }
        if (AllBlocks.TRACK_STATION.has(level.getBlockState(stationPos))) {
            return false;
        }
        BlockState state = ProperWaterloggedBlock.withWater(level, AllBlocks.TRACK_STATION.getDefaultState(), stationPos);
        level.setBlock(stationPos, state, Block.UPDATE_ALL);
        if (!(level.getBlockEntity(stationPos) instanceof StationBlockEntity station)) {
            return false;
        }
        CompoundTag tag = station.saveWithoutMetadata();
        tag.put("TargetTrack", NbtUtils.writeBlockPos(trackPos.subtract(stationPos)));
        tag.putBoolean("TargetDirection", true);
        station.load(tag);
        station.setChanged();
        return true;
    }

    /**
     * Applies a name once Create has created the station's edge point.
     *
     * @return true when the name was applied or can never be applied (the station is gone)
     */
    public static boolean tryName(ServerLevel level, BlockPos stationPos, String name) {
        if (!(level.getBlockEntity(stationPos) instanceof StationBlockEntity station)) {
            return true;
        }
        GlobalStation global = station.getStation();
        if (global == null) {
            return false;
        }
        if (name.equals(global.name)) {
            return true;
        }
        return station.updateName(name);
    }
}
