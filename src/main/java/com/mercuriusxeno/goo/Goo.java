package com.mercuriusxeno.goo;

import com.mercuriusxeno.goo.data.GooValueRegistry;
import com.mercuriusxeno.goo.registry.*;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

@Mod(Goo.MODID)
public class Goo {

    public static final String MODID = GooTypes.NAMESPACE;
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final GooValueRegistry GOO_VALUES = new GooValueRegistry();

    /**
     * Registers all deferred registries, event listeners, and config on mod construction.
     *
     * @param modEventBus  the mod event bus
     * @param modContainer the mod container
     */
    public Goo(IEventBus modEventBus, ModContainer modContainer) {
        registerDeferredRegistries(modEventBus);
        GooTypeRegistry.init(modEventBus);
        registerModListeners(modEventBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, GooConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, GooClientConfig.SPEC);

        GooEventWiring.register(modEventBus);

        GOO_VALUES.setEffectiveCachePath(
                FMLPaths.CONFIGDIR.get().resolve("goo_derived_values.json"));

        LOGGER.info("Goo mod initialized");
    }

    /**
     * Registers all deferred registries with the mod event bus.
     *
     * @param modEventBus the mod event bus
     */
    private static void registerDeferredRegistries(IEventBus modEventBus) {
        registerCoreRegistries(modEventBus);
        registerContentRegistries(modEventBus);
    }

    /**
     * Registers fluid, block, item, and entity registries.
     *
     * @param modEventBus the mod event bus to register on
     */
    private static void registerCoreRegistries(IEventBus modEventBus) {
        GooFluidTypes.FLUID_TYPES.register(modEventBus);
        GooFluids.FLUIDS.register(modEventBus);
        GooBlocks.BLOCKS.register(modEventBus);
        GooItems.ITEMS.register(modEventBus);
        GooBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        GooEntities.ENTITIES.register(modEventBus);
    }

    /**
     * Registers data component, potion, particle, and creative tab registries.
     *
     * @param modEventBus the mod event bus to register on
     */
    private static void registerContentRegistries(IEventBus modEventBus) {
        GooDataComponents.DATA_COMPONENTS.register(modEventBus);
        GooAttachments.ATTACHMENT_TYPES.register(modEventBus);
        GooPotions.register(modEventBus);
        GooParticles.PARTICLE_TYPES.register(modEventBus);
        GooSounds.SOUND_EVENTS.register(modEventBus);
        GooCreativeTabs.TABS.register(modEventBus);
    }

    /**
     * Registers the mod event bus listener for tickets.
     *
     * @param modEventBus the mod event bus
     */
    private static void registerModListeners(IEventBus modEventBus) {
        modEventBus.addListener(GooTickets::register);
    }
}
