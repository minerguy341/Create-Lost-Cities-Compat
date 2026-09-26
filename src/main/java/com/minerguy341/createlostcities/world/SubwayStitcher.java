package com.minerguy341.createlostcities.world;

import com.minerguy341.createlostcities.CreateLostCities;
import com.minerguy341.createlostcities.SubwayConfig;
import com.minerguy341.createlostcities.compat.LostCitiesBridge;
import com.minerguy341.createlostcities.layout.ChunkPlan;
import com.minerguy341.createlostcities.layout.CurvePiece;
import com.minerguy341.createlostcities.layout.RailCell;
import com.minerguy341.createlostcities.layout.StationSpot;
import com.minerguy341.createlostcities.layout.SubwayLayout;
import com.minerguy341.createlostcities.layout.TrackPiece;
import com.minerguy341.createlostcities.layout.TrackShapeKind;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.ITrackBlock;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns Lost Cities railway chunks into connected Create track after world generation.
 *
 * <p>Why after generation: blocks written to a proto-chunk never get {@code onPlace}, so Create never
 * adds them to its {@code TrackGraph}; and a curve's block entity stores absolute positions of its other
 * end, which usually lies in a chunk that has not been generated yet. So new railway chunks are only
 * recorded when they load, and stitched on the server thread once the chunk <em>and all eight
 * neighbours</em> are loaded. That makes every chunk-border join (straight runs, junction curves that
 * reach five blocks into the next chunk, graph walks across the border) happen between loaded chunks.
 */
public final class SubwayStitcher {

    /** Chunks whose pending status should be re-checked this tick, per level. */
    private static final Map<ResourceKey<Level>, LongLinkedOpenHashSet> READY = new HashMap<>();

    private SubwayStitcher() {
    }

    public static boolean isActive(ServerLevel level) {
        if (!SubwayConfig.ENABLED.get()) {
            return false;
        }
        List<? extends String> dims = SubwayConfig.DIMENSIONS.get();
        if (!dims.isEmpty() && !dims.contains(level.dimension().location().toString())) {
            return false;
        }
        return LostCitiesBridge.isLostCitiesLevel(level);
    }

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        if (!isActive(level)) {
            return;
        }
        ChunkPos pos = chunk.getPos();
        long key = pos.toLong();
        SubwaySavedData data = SubwaySavedData.get(level);
        if (!data.pending.contains(key) && !data.done.contains(key)
                && (event.isNewChunk() || SubwayConfig.RETROFIT_EXISTING.get())) {
            if (!LostCitiesBridge.cell(level, pos.x, pos.z).isNone()) {
                data.pending.add(key);
                data.setDirty();
            }
        }
        // A chunk load can complete the neighbourhood of any pending chunk around it.
        LongLinkedOpenHashSet ready = READY.computeIfAbsent(level.dimension(), k -> new LongLinkedOpenHashSet());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long n = ChunkPos.asLong(pos.x + dx, pos.z + dz);
                if (data.pending.contains(n)) {
                    ready.add(n);
                }
            }
        }
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            READY.remove(level.dimension());
        }
    }

    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        LongLinkedOpenHashSet ready = READY.get(level.dimension());
        if (ready != null && !ready.isEmpty()) {
            if (isActive(level)) {
                drainReady(level, ready, SubwaySavedData.get(level));
            } else {
                ready.clear();
            }
        }
        if (level.getGameTime() % 20 == 0) {
            // Not gated on isActive so /createlostcities spike stations get named in any world.
            SubwaySavedData data = SubwaySavedData.get(level);
            if (!data.names.isEmpty()) {
                applyNames(level, data);
            }
        }
    }

    private static void drainReady(ServerLevel level, LongLinkedOpenHashSet ready, SubwaySavedData data) {
        int budget = SubwayConfig.MAX_CHUNKS_PER_TICK.get();
        while (budget > 0 && !ready.isEmpty()) {
            long key = ready.removeFirstLong();
            if (!data.pending.contains(key)) {
                continue;
            }
            int cx = ChunkPos.getX(key);
            int cz = ChunkPos.getZ(key);
            if (!neighbourhoodLoaded(level, cx, cz)) {
                continue; // re-queued by the next neighbouring chunk load
            }
            stitch(level, cx, cz, data);
            budget--;
        }
    }

    public static boolean neighbourhoodLoaded(ServerLevel level, int cx, int cz) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!level.getChunkSource().hasChunk(cx + dx, cz + dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Stitches one chunk. Callers must make sure the 3x3 neighbourhood is loaded. */
    public static StitchResult stitch(ServerLevel level, int cx, int cz, SubwaySavedData data) {
        long key = ChunkPos.asLong(cx, cz);
        data.pending.remove(key);
        data.done.add(key);
        data.setDirty();

        RailCell cell = LostCitiesBridge.cell(level, cx, cz);
        if (cell.isNone()) {
            return StitchResult.NOTHING;
        }
        RailCell north = LostCitiesBridge.cell(level, cx, cz - 1);
        RailCell south = LostCitiesBridge.cell(level, cx, cz + 1);
        ChunkPlan plan = SubwayLayout.plan(cell, north, south);
        if (plan.isEmpty()) {
            return StitchResult.NOTHING;
        }
        int ox = cx << 4;
        int oz = cz << 4;

        if (SubwayConfig.REMOVE_VANILLA_RAILS.get()) {
            removeVanillaRails(level, ox, oz, plan);
        }

        int tracks = 0;
        for (TrackPiece piece : plan.tracks()) {
            BlockPos pos = new BlockPos(ox + piece.x(), piece.y(), oz + piece.z());
            if (SubwayConfig.CARVE_CLEARANCE.get()) {
                boolean slope = piece.shape() == TrackShapeKind.AE || piece.shape() == TrackShapeKind.AW;
                carveAbove(level, pos, slope ? 4 : 3, piece.shape() == TrackShapeKind.ZO);
            }
            if (TrackPlacer.placeStraight(level, pos, piece.shape())) {
                tracks++;
            }
        }

        int curves = 0;
        for (CurvePiece curve : plan.curves()) {
            BlockPos a = new BlockPos(ox + curve.ax(), curve.y(), oz + curve.az());
            BlockPos b = new BlockPos(ox + curve.bx(), curve.y(), oz + curve.bz());
            BezierConnection connection = TrackPlacer.placeCurve(level,
                    a, new Vec3(curve.axisAx(), 0, curve.axisAz()),
                    b, new Vec3(curve.axisBx(), 0, curve.axisBz()));
            if (connection != null) {
                curves++;
                if (SubwayConfig.CARVE_CLEARANCE.get()) {
                    carveAlong(level, connection);
                }
            }
        }

        int stations = 0;
        if (SubwayConfig.PLACE_STATIONS.get() && !plan.stations().isEmpty()) {
            String base = StationNames.baseName(LostCitiesBridge.seed(level), cx, cz);
            int count = plan.stations().size();
            for (StationSpot spot : plan.stations()) {
                BlockPos stationPos = new BlockPos(ox + spot.stationX(), spot.y(), oz + spot.stationZ());
                BlockPos trackPos = new BlockPos(ox + spot.trackX(), spot.y(), oz + spot.trackZ());
                if (StationPlacer.place(level, stationPos, trackPos)) {
                    stations++;
                    queueName(level, stationPos, StationNames.format(base, spot.platform(), count, cx, cz));
                }
            }
        }
        CreateLostCities.LOGGER.debug("Stitched {} {} at chunk {},{}: {} tracks, {} curves, {} stations",
                cell.type(), cell.direction(), cx, cz, tracks, curves, stations);
        return new StitchResult(tracks, curves, stations);
    }

    /** Names a station as soon as Create has created its edge point. */
    public static void queueName(ServerLevel level, BlockPos stationPos, String name) {
        SubwaySavedData data = SubwaySavedData.get(level);
        data.names.put(stationPos.asLong(), name);
        data.setDirty();
    }

    private static void applyNames(ServerLevel level, SubwaySavedData data) {
        LongIterator it = data.names.keySet().iterator();
        boolean changed = false;
        while (it.hasNext()) {
            long packed = it.nextLong();
            BlockPos pos = BlockPos.of(packed);
            if (!level.isLoaded(pos)) {
                continue;
            }
            if (StationPlacer.tryName(level, pos, data.names.get(packed))) {
                it.remove();
                changed = true;
            }
        }
        if (changed) {
            data.setDirty();
        }
    }

    private static void removeVanillaRails(ServerLevel level, int ox, int oz, ChunkPlan plan) {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (TrackPiece piece : plan.tracks()) {
            minY = Math.min(minY, piece.y());
            maxY = Math.max(maxY, piece.y());
        }
        for (CurvePiece curve : plan.curves()) {
            minY = Math.min(minY, curve.y());
            maxY = Math.max(maxY, curve.y());
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = minY - 1; y <= maxY + 1; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    pos.set(ox + x, y, oz + z);
                    if (level.getBlockState(pos).getBlock() instanceof BaseRailBlock) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    /** Clears a 3-wide gauge above a track block, perpendicular to its direction. */
    private static void carveAbove(ServerLevel level, BlockPos track, int height, boolean alongZ) {
        for (int side = -1; side <= 1; side++) {
            for (int h = 1; h <= height; h++) {
                BlockPos pos = alongZ ? track.offset(side, h, 0) : track.offset(0, h, side);
                carve(level, pos);
            }
            BlockPos level0 = alongZ ? track.offset(side, 0, 0) : track.offset(0, 0, side);
            if (side != 0) {
                // Leave platforms and walls at track level alone; only remove loose clutter.
                BlockState state = level.getBlockState(level0);
                if (state.canBeReplaced() && !state.isAir() && state.getFluidState().isEmpty()) {
                    level.setBlock(level0, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private static void carveAlong(ServerLevel level, BezierConnection curve) {
        int samples = Math.max(8, (int) Math.ceil(curve.getLength() * 2));
        for (int i = 0; i <= samples; i++) {
            Vec3 p = curve.getPosition(i / (double) samples);
            BlockPos base = BlockPos.containing(p.x, p.y + 0.1, p.z);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int h = 1; h <= 3; h++) {
                        carve(level, base.offset(dx, h, dz));
                    }
                }
            }
        }
    }

    private static void carve(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return;
        }
        if (state.getBlock() instanceof ITrackBlock || AllBlocks.TRACK_STATION.has(state) || state.hasBlockEntity()) {
            return;
        }
        if (state.getDestroySpeed(level, pos) < 0) {
            return; // bedrock and friends
        }
        for (Direction d : Direction.values()) {
            if (!level.getFluidState(pos.relative(d)).isEmpty()) {
                return; // never open a tunnel into water or lava
            }
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    public record StitchResult(int tracks, int curves, int stations) {
        public static final StitchResult NOTHING = new StitchResult(0, 0, 0);
    }
}
