package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.lunar.LunarEldritch;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * Registers the data maps goo's data names.
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class GooDataMaps {

    /**
     * {@code goo:dark_side_eldritch}, on biomes: how many times larger the eldritch groups run on lunar's dark side.
     * decision dark-side-is-an-endless-night-of-eldritch-rich-in-void-and-xeno
     */
    public static final DataMapType<Biome, LunarEldritch.DarkSideGroups> DARK_SIDE_ELDRITCH = DataMapType.builder(
            Identifier.fromNamespaceAndPath(Goo.MODID, "dark_side_eldritch"), Registries.BIOME,
            LunarEldritch.DarkSideGroups.CODEC).build();

    private GooDataMaps() {
    }

    /**
     * Registers goo's data map types.
     *
     * @param event the registration event
     */
    @SubscribeEvent
    public static void onRegisterDataMapTypes(RegisterDataMapTypesEvent event) {
        event.register(DARK_SIDE_ELDRITCH);
    }
}
