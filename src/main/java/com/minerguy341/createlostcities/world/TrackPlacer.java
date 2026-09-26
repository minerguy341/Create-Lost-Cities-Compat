package com.minerguy341.createlostcities.world;

import com.minerguy341.createlostcities.layout.TrackShapeKind;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.ITrackBlock;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackBlockEntity;
import com.simibubi.create.content.trains.track.TrackMaterial;
import com.simibubi.create.content.trains.track.TrackShape;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Places Create tracks in a live {@link ServerLevel} the same way a player would, so that Create's
 * own hooks register them with the {@code TrackGraph}:
 * <ul>
 *     <li>{@code TrackBlock.onPlace} schedules a block tick, and that tick runs
 *     {@code TrackPropagator.onRailAdded}, which walks the connected track and creates or merges graph
 *     nodes/edges. This only happens for real {@code setBlock} calls on a loaded level, never for blocks
 *     written into a proto-chunk during world generation.</li>
 *     <li>Curves are {@link BezierConnection}s stored on a {@link TrackBlockEntity} at both ends,
 *     mirroring the tail of {@code TrackPlacement.placeTracks}.</li>
 * </ul>
 */
public final class TrackPlacer {

    private static final Vec3 UP = new Vec3(0, 1, 0);

    private TrackPlacer() {
    }

    public static TrackShape toCreate(TrackShapeKind kind) {
        return switch (kind) {
            case XO -> TrackShape.XO;
            case ZO -> TrackShape.ZO;
            case AE -> TrackShape.AE;
            case AW -> TrackShape.AW;
        };
    }

    /** Places a plain track block. Existing track blocks are left alone so junction ends and player edits survive. */
    public static boolean placeStraight(ServerLevel level, BlockPos pos, TrackShapeKind shape) {
        BlockState existing = level.getBlockState(pos);
        if (existing.getBlock() instanceof ITrackBlock) {
            return false;
        }
        BlockState state = AllBlocks.TRACK.getDefaultState()
                .setValue(TrackBlock.SHAPE, toCreate(shape))
                .setValue(TrackBlock.HAS_BE, false);
        level.setBlock(pos, ProperWaterloggedBlock.withWater(level, state, pos), Block.UPDATE_ALL);
        return true;
    }

    /**
     * Connects two track blocks with a flat bezier curve. Both end blocks are (re)placed as straight
     * track with {@code HAS_BE} so they carry a {@link TrackBlockEntity}; both chunks must be loaded.
     *
     * @param axisA unit vector pointing from end A into the curve
     * @param axisB unit vector pointing from end B into the curve
     * @return the new connection, or null if it already existed or an end could not be prepared
     */
    @Nullable
    public static BezierConnection placeCurve(ServerLevel level, BlockPos a, Vec3 axisA, BlockPos b, Vec3 axisB) {
        TrackBlockEntity beA = prepareEnd(level, a, axisA);
        TrackBlockEntity beB = prepareEnd(level, b, axisB);
        if (beA == null || beB == null || beA.getConnections().containsKey(b)) {
            return null;
        }
        TrackBlock track = (TrackBlock) AllBlocks.TRACK.get();
        Vec3 startA = track.getCurveStart(level, a, level.getBlockState(a), axisA);
        Vec3 startB = track.getCurveStart(level, b, level.getBlockState(b), axisB);
        BezierConnection curve = new BezierConnection(Couple.create(a, b), Couple.create(startA, startB),
                Couple.create(axisA, axisB), Couple.create(UP, UP), true, false, TrackMaterial.ANDESITE);
        beA.addConnection(curve);
        beB.addConnection(curve.secondary());
        return curve;
    }

    private static TrackBlockEntity prepareEnd(ServerLevel level, BlockPos pos, Vec3 axis) {
        TrackShape shape = Math.abs(axis.x) > Math.abs(axis.z) ? TrackShape.XO : TrackShape.ZO;
        BlockState existing = level.getBlockState(pos);
        BlockState state;
        if (existing.getBlock() instanceof TrackBlock && existing.getValue(TrackBlock.SHAPE) == shape) {
            state = existing.setValue(TrackBlock.HAS_BE, true);
        } else {
            state = AllBlocks.TRACK.getDefaultState().setValue(TrackBlock.SHAPE, shape).setValue(TrackBlock.HAS_BE, true);
        }
        if (state != existing) {
            level.setBlock(pos, ProperWaterloggedBlock.withWater(level, state, pos), Block.UPDATE_ALL);
        }
        return level.getBlockEntity(pos) instanceof TrackBlockEntity tbe ? tbe : null;
    }
}
