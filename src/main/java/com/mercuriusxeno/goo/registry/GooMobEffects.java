package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.GooBrewEffect;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registers one brew effect per bundled goo type, the effect that type's
 * potion carries. Registration happens in {@link #register}, not at class
 * load, so the names read without a registry bootstrap.
 * decision brew-grants-the-self-ability-for-an-hour
 */
public final class GooMobEffects {

    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, Goo.MODID);
    private static final Map<ResourceKey<GooTypeDefinition>, DeferredHolder<MobEffect, MobEffect>> REGISTERED =
            new LinkedHashMap<>();
    /**
     * The brew effect of each bundled type, filled by {@link #register}.
     */
    public static final Map<ResourceKey<GooTypeDefinition>, DeferredHolder<MobEffect, MobEffect>> BREW_EFFECTS =
            Collections.unmodifiableMap(REGISTERED);
    private static final String BREW_SUFFIX = "_brew";

    private GooMobEffects() {
    }

    /**
     * Registers one brew effect per bundled type and subscribes the deferred
     * register to the mod event bus. Called once from Goo, before GooPotions.
     *
     * @param modEventBus the mod event bus
     */
    public static void register(IEventBus modEventBus) {
        for (ResourceKey<GooTypeDefinition> key : GooTypes.BUNDLED) {
            REGISTERED.put(key, MOB_EFFECTS.register(brewEffectName(key), () -> new GooBrewEffect(key)));
        }
        MOB_EFFECTS.register(modEventBus);
    }

    /**
     * The brew effect's registry path: the type id and the brew suffix.
     *
     * @param key the bundled type's key
     * @return the effect path
     */
    public static String brewEffectName(ResourceKey<GooTypeDefinition> key) {
        return key.identifier().getPath() + BREW_SUFFIX;
    }
}
