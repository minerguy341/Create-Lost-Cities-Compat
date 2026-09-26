package com.minerguy341.createlostcities;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public final class SubwayConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.BooleanValue INSTALL_PARTS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DIMENSIONS;
    public static final ForgeConfigSpec.BooleanValue PLACE_STATIONS;
    public static final ForgeConfigSpec.BooleanValue CARVE_CLEARANCE;
    public static final ForgeConfigSpec.BooleanValue REMOVE_VANILLA_RAILS;
    public static final ForgeConfigSpec.BooleanValue RETROFIT_EXISTING;
    public static final ForgeConfigSpec.IntValue MAX_CHUNKS_PER_TICK;
    public static final ForgeConfigSpec.ConfigValue<String> STATION_NAME_SINGLE;
    public static final ForgeConfigSpec.ConfigValue<String> STATION_NAME_MULTI;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Create: Lost Cities Subways").push("subways");

        ENABLED = b.comment("Master switch. When false no tracks or stations are placed (the Create-scale tunnel parts are still installed unless installCreateScaleParts is false).")
                .define("enabled", true);
        INSTALL_PARTS = b.comment("Install the built-in datapack that replaces The Lost Cities railway parts with Create-scale tunnels and platforms.",
                        "Takes effect on the next world load / datapack reload. Without it tracks are still placed, but inside the narrow vanilla-rail tunnels.")
                .define("installCreateScaleParts", true);
        DIMENSIONS = b.comment("Dimensions to process (e.g. \"minecraft:overworld\"). Empty = every dimension that has a Lost Cities profile.")
                .defineListAllowEmpty("dimensions", List.of(), o -> o instanceof String);
        PLACE_STATIONS = b.comment("Place and name a create:track_station on every Lost Cities station chunk (one per platform track).")
                .define("placeStations", true);
        CARVE_CLEARANCE = b.comment("Clear a 3-wide, 3-high loading gauge above every placed track (never next to fluids).")
                .define("carveClearance", true);
        REMOVE_VANILLA_RAILS = b.comment("Remove vanilla rails left in a railway chunk (e.g. from a custom world style that is not overridden).")
                .define("removeVanillaRails", true);
        RETROFIT_EXISTING = b.comment("Also stitch railway chunks that were generated before this mod was installed.",
                        "Those chunks keep the old narrow tunnels, so expect tracks to cut through walls.")
                .define("retrofitExistingChunks", false);
        MAX_CHUNKS_PER_TICK = b.comment("Upper bound on railway chunks stitched per level per tick.")
                .defineInRange("maxChunksPerTick", 4, 1, 64);
        STATION_NAME_SINGLE = b.comment("Station name for single-track stations. Tokens: {name} {x} {z}")
                .define("stationNameSingle", "{name}");
        STATION_NAME_MULTI = b.comment("Station name for multi-track stations. Tokens: {name} {platform} {x} {z}")
                .define("stationNameMulti", "{name} P{platform}");

        b.pop();
        SPEC = b.build();
    }

    private SubwayConfig() {
    }
}
