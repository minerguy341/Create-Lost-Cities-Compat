package com.minerguy341.createlostcities;

import com.minerguy341.createlostcities.command.SubwayCommands;
import com.minerguy341.createlostcities.world.BuiltinDatapack;
import com.minerguy341.createlostcities.world.SubwayStitcher;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(CreateLostCities.MOD_ID)
public class CreateLostCities {

    public static final String MOD_ID = "create_lostcities";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CreateLostCities() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SubwayConfig.SPEC);

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(BuiltinDatapack::onAddPackFinders);

        MinecraftForge.EVENT_BUS.addListener(SubwayStitcher::onChunkLoad);
        MinecraftForge.EVENT_BUS.addListener(SubwayStitcher::onLevelTick);
        MinecraftForge.EVENT_BUS.addListener(SubwayStitcher::onLevelUnload);
        MinecraftForge.EVENT_BUS.addListener(SubwayCommands::register);
    }
}
