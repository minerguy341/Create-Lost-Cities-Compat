package com.minerguy341.createlostcities.compat;

import com.minerguy341.createlostcities.CreateLostCities;
import com.minerguy341.createlostcities.layout.RailCell;
import com.minerguy341.createlostcities.layout.RailDir;
import mcjty.lostcities.api.RailChunkType;
import mcjty.lostcities.setup.Registration;
import mcjty.lostcities.varia.ChunkCoord;
import mcjty.lostcities.worldgen.IDimensionInfo;
import mcjty.lostcities.worldgen.lost.BuildingInfo;
import mcjty.lostcities.worldgen.lost.Railway;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;

/**
 * The only class that touches Lost Cities internals. The public API ({@code ILostChunkInfo}) exposes
 * the rail type and level but not the direction or rail count, which decide mirroring and lane
 * count, so this reads {@code BuildingInfo}/{@code Railway} directly. Porting to another Lost
 * Cities version should only require changes here.
 */
public final class LostCitiesBridge {

    private static volatile boolean broken = false;

    private LostCitiesBridge() {
    }

    @Nullable
    public static IDimensionInfo dimensionInfo(ServerLevel level) {
        if (broken) {
            return null;
        }
        try {
            return Registration.LOSTCITY_FEATURE.get().getDimensionInfo(level);
        } catch (LinkageError | RuntimeException e) {
            fail(e);
            return null;
        }
    }

    public static boolean isLostCitiesLevel(ServerLevel level) {
        return dimensionInfo(level) != null;
    }

    /** Railway cell for a chunk, or {@link RailCell#NONE} if there is none or the level has no Lost Cities. */
    public static RailCell cell(ServerLevel level, int chunkX, int chunkZ) {
        IDimensionInfo provider = dimensionInfo(level);
        if (provider == null) {
            return RailCell.NONE;
        }
        try {
            ChunkCoord coord = new ChunkCoord(level.dimension(), chunkX, chunkZ);
            BuildingInfo info = BuildingInfo.getBuildingInfo(coord, provider);
            Railway.RailChunkInfo rail = info.getRailInfo();
            if (rail.getType() == RailChunkType.NONE) {
                return RailCell.NONE;
            }
            return new RailCell(rail.getType(), convert(rail.getDirection()), rail.getLevel(), rail.getRails(), info.groundLevel);
        } catch (LinkageError | RuntimeException e) {
            fail(e);
            return RailCell.NONE;
        }
    }

    public static long seed(ServerLevel level) {
        IDimensionInfo provider = dimensionInfo(level);
        return provider != null ? provider.getSeed() : level.getSeed();
    }

    private static RailDir convert(Railway.RailDirection direction) {
        return switch (direction) {
            case BI -> RailDir.BI;
            case WEST -> RailDir.WEST;
            case EAST -> RailDir.EAST;
        };
    }

    private static void fail(Throwable e) {
        if (!broken) {
            broken = true;
            CreateLostCities.LOGGER.error("Could not read The Lost Cities railway data; subway stitching is disabled. "
                    + "This Lost Cities version is probably not supported.", e);
        }
    }
}
