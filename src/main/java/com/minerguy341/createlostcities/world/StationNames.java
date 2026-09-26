package com.minerguy341.createlostcities.world;

import com.minerguy341.createlostcities.SubwayConfig;

import java.util.Random;

/** Deterministic, seed-based station names so a world always gets the same map. */
public final class StationNames {

    private static final String[] HEADS = {
            "Ash", "Bel", "Brack", "Carden", "Cor", "Dun", "Elm", "Fair", "Glen", "Grey", "Hol", "Iron",
            "Kings", "Lark", "Lin", "Mill", "Moor", "North", "Oak", "Pem", "Queens", "Raven", "Red", "Rook",
            "Stone", "Sutter", "Thorn", "Wal", "Wester", "Whit", "Wil", "York"
    };
    private static final String[] TAILS = {
            "bury", "bridge", "cross", "field", "ford", "gate", "ham", "hurst", "ley", "marsh", "mouth",
            "port", "stead", "ton", "vale", "wick", "wood", "worth"
    };
    private static final String[] KINDS = {
            "", "", "", "Central", "Junction", "Park", "Square", "Street", "Parade", "Market", "Docks"
    };

    private StationNames() {
    }

    public static String baseName(long seed, int chunkX, int chunkZ) {
        Random random = new Random(seed ^ (chunkX * 341873128712L) ^ (chunkZ * 132897987541L) ^ 0x5B7A_11CEL);
        String name = HEADS[random.nextInt(HEADS.length)] + TAILS[random.nextInt(TAILS.length)];
        String kind = KINDS[random.nextInt(KINDS.length)];
        return kind.isEmpty() ? name : name + " " + kind;
    }

    public static String format(String baseName, int platform, int platformCount, int chunkX, int chunkZ) {
        String pattern = platformCount > 1 ? SubwayConfig.STATION_NAME_MULTI.get() : SubwayConfig.STATION_NAME_SINGLE.get();
        return pattern.replace("{name}", baseName)
                .replace("{platform}", Integer.toString(platform))
                .replace("{x}", Integer.toString(chunkX))
                .replace("{z}", Integer.toString(chunkZ));
    }
}
