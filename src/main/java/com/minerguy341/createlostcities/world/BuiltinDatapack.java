package com.minerguy341.createlostcities.world;

import com.minerguy341.createlostcities.CreateLostCities;
import com.minerguy341.createlostcities.SubwayConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.ModList;

import java.nio.file.Path;

/**
 * Registers the Create-scale Lost Cities railway parts as a required built-in datapack at the TOP of
 * the pack stack. A plain {@code data/lostcities/...} folder inside the mod jar would share the
 * "mod resources" pack with Lost Cities itself, and which copy wins would depend on mod ordering.
 */
public final class BuiltinDatapack {

    public static final String PACK_ID = "builtin/" + CreateLostCities.MOD_ID + "_subways";

    private BuiltinDatapack() {
    }

    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA || !SubwayConfig.INSTALL_PARTS.get()) {
            return;
        }
        Path root = ModList.get().getModFileById(CreateLostCities.MOD_ID).getFile().findResource("datapacks/subways");
        Pack pack = Pack.readMetaAndCreate(PACK_ID, Component.literal("Create-scale Lost Cities subways"), true,
                id -> new PathPackResources(id, root, true), PackType.SERVER_DATA, Pack.Position.TOP, PackSource.BUILT_IN);
        if (pack == null) {
            CreateLostCities.LOGGER.error("Could not load the built-in subway datapack from {}", root);
            return;
        }
        event.addRepositorySource(consumer -> consumer.accept(pack));
    }
}
