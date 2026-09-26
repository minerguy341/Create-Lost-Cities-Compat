package com.minerguy341.createlostcities.command;

import com.minerguy341.createlostcities.compat.LostCitiesBridge;
import com.minerguy341.createlostcities.layout.ChunkPlan;
import com.minerguy341.createlostcities.layout.RailCell;
import com.minerguy341.createlostcities.layout.SubwayLayout;
import com.minerguy341.createlostcities.layout.TrackShapeKind;
import com.minerguy341.createlostcities.world.StationPlacer;
import com.minerguy341.createlostcities.world.SubwaySavedData;
import com.minerguy341.createlostcities.world.SubwayStitcher;
import com.minerguy341.createlostcities.world.TrackPlacer;
import com.mojang.brigadier.CommandDispatcher;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.List;

/**
 * {@code /createlostcities info|stitch [radius]|spike|graph}. All subcommands need permission level 2.
 */
public final class SubwayCommands {

    private SubwayCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("createlostcities")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("info").executes(SubwayCommands::info))
                .then(Commands.literal("stitch")
                        .executes(ctx -> stitch(ctx, 0))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(0, 16))
                                .executes(ctx -> stitch(ctx, IntegerArgumentType.getInteger(ctx, "radius")))))
                .then(Commands.literal("spike").executes(SubwayCommands::spike))
                .then(Commands.literal("graph").executes(SubwayCommands::graph)));
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        ChunkPos pos = new ChunkPos(BlockPos.containing(source.getPosition()));
        if (!LostCitiesBridge.isLostCitiesLevel(level)) {
            source.sendFailure(Component.literal("This dimension has no Lost Cities profile."));
            return 0;
        }
        RailCell cell = LostCitiesBridge.cell(level, pos.x, pos.z);
        ChunkPlan plan = SubwayLayout.plan(cell,
                LostCitiesBridge.cell(level, pos.x, pos.z - 1), LostCitiesBridge.cell(level, pos.x, pos.z + 1));
        SubwaySavedData data = SubwaySavedData.get(level);
        long key = pos.toLong();
        source.sendSuccess(() -> Component.literal(String.format(
                "Chunk %d,%d: %s dir=%s level=%d rails=%d trackY=%d | plan: %d tracks, %d curves, %d stations | %s",
                pos.x, pos.z, cell.type(), cell.direction(), cell.level(), cell.rails(), cell.trackY(),
                plan.tracks().size(), plan.curves().size(), plan.stations().size(),
                data.isDone(key) ? "stitched" : data.isPending(key) ? "pending" : "untracked")), false);
        source.sendSuccess(() -> Component.literal(String.format("Level totals: %d pending, %d stitched, %d stations awaiting a name",
                data.pendingCount(), data.doneCount(), data.unnamedCount())), false);
        return 1;
    }

    /** Force-stitches loaded railway chunks around the caller, ignoring the new-chunk rule (retrofit / debugging). */
    private static int stitch(CommandContext<CommandSourceStack> ctx, int radius) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        if (!LostCitiesBridge.isLostCitiesLevel(level)) {
            source.sendFailure(Component.literal("This dimension has no Lost Cities profile."));
            return 0;
        }
        ChunkPos center = new ChunkPos(BlockPos.containing(source.getPosition()));
        SubwaySavedData data = SubwaySavedData.get(level);
        int chunks = 0, tracks = 0, curves = 0, stations = 0, skipped = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int cx = center.x + dx, cz = center.z + dz;
                if (LostCitiesBridge.cell(level, cx, cz).isNone()) {
                    continue;
                }
                if (!SubwayStitcher.neighbourhoodLoaded(level, cx, cz)) {
                    skipped++;
                    continue;
                }
                SubwayStitcher.StitchResult r = SubwayStitcher.stitch(level, cx, cz, data);
                chunks++;
                tracks += r.tracks();
                curves += r.curves();
                stations += r.stations();
            }
        }
        int fc = chunks, ft = tracks, fcu = curves, fs = stations, fsk = skipped;
        source.sendSuccess(() -> Component.literal(String.format(
                "Stitched %d railway chunks: %d tracks, %d curves, %d stations (%d skipped: neighbours not loaded)",
                fc, ft, fcu, fs, fsk)), true);
        return chunks;
    }

    /** Summarises Create's track graphs: the quickest in-game check that stitched track is actually connected. */
    private static int graph(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        int graphs = 0;
        for (TrackGraph graph : Create.RAILWAYS.trackNetworks.values()) {
            graphs++;
            List<String> names = graph.getPoints(EdgePointType.STATION).stream().map(st -> st.name).sorted().toList();
            int nodes = graph.getNodes().size();
            // Node locations are stored at half-block resolution.
            int minX = graph.getNodes().stream().mapToInt(n -> n.getX() / 2).min().orElse(0);
            int maxX = graph.getNodes().stream().mapToInt(n -> n.getX() / 2).max().orElse(0);
            int minZ = graph.getNodes().stream().mapToInt(n -> n.getZ() / 2).min().orElse(0);
            int maxZ = graph.getNodes().stream().mapToInt(n -> n.getZ() / 2).max().orElse(0);
            source.sendSuccess(() -> Component.literal(String.format("Graph %s: %d nodes, x %d..%d, z %d..%d, %d stations %s",
                    graph.id.toString().substring(0, 8), nodes, minX, maxX, minZ, maxZ, names.size(), names)), false);
        }
        int total = graphs;
        source.sendSuccess(() -> Component.literal(total + " track graph(s)"), false);
        return total;
    }

    /**
     * Phase 0 spike: at the caller's feet, lay 32 blocks of straight track east, a 90 degree curve
     * north with the same geometry the junctions use, 16 blocks north, and a station bound to the
     * straight section. Works in any world; no Lost Cities needed.
     */
    private static int spike(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        BlockPos origin = BlockPos.containing(source.getPosition());
        for (int x = 0; x < 32; x++) {
            TrackPlacer.placeStraight(level, origin.offset(x, 0, 0), TrackShapeKind.XO);
        }
        // Curve from the east end (heading +X) to a north-running line, radius 7.5 like the junctions.
        BlockPos a = origin.offset(32, 0, 0);
        BlockPos b = origin.offset(32 + SubwayLayout.CONNECTOR_X, 0, -(SubwayLayout.CURVE_REACH + SubwayLayout.LANE_NORTH));
        TrackPlacer.placeCurve(level, a, new Vec3(1, 0, 0), b, new Vec3(0, 0, 1));
        for (int z = 1; z <= 16; z++) {
            TrackPlacer.placeStraight(level, b.offset(0, 0, -z), TrackShapeKind.ZO);
        }
        BlockPos stationPos = origin.offset(8, 0, -2);
        boolean placed = StationPlacer.place(level, stationPos, origin.offset(8, 0, 0));
        if (placed) {
            SubwayStitcher.queueName(level, stationPos, "Spike Station");
        }
        source.sendSuccess(() -> Component.literal("Spike built at " + origin.toShortString()
                + (placed ? "; station 'Spike Station' will be named once Create registers it" : "; station could not be placed")), true);
        return 1;
    }
}
