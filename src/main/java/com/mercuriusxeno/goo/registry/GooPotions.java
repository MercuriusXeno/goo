package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registers one potion per bundled goo type, keyed by the type's registry
 * key. Each potion carries its type's brew effect for an hour, which runs the
 * type's self + brew ability when drunk. Decision potions-stay-per-type: a
 * type a datapack adds has no potion, so registration walks
 * {@link #POTION_TYPES}, the bundled keys, rather than the registry.
 * Registration happens in {@link #register}, not at class load, so the key
 * set reads without a registry bootstrap.
 * decision brew-grants-the-self-ability-for-an-hour
 */
public final class GooPotions {

    public static final DeferredRegister<Potion> POTIONS =
            DeferredRegister.create(Registries.POTION, Goo.MODID);
    /**
     * The types that get a potion: every bundled key, in bundled order.
     */
    public static final List<ResourceKey<GooTypeDefinition>> POTION_TYPES = GooTypes.BUNDLED;
    private static final Map<ResourceKey<GooTypeDefinition>, DeferredHolder<Potion, Potion>> REGISTERED =
            new LinkedHashMap<>();
    /**
     * The potion of each type in {@link #POTION_TYPES}, filled by {@link #register}.
     */
    public static final Map<ResourceKey<GooTypeDefinition>, DeferredHolder<Potion, Potion>> GOO_POTIONS =
            Collections.unmodifiableMap(REGISTERED);
    /**
     * How long a drunk brew holds its ability: an hour at 20 tps.
     */
    public static final int BREW_DURATION = 72000;
    private static final int NO_AMPLIFIER = 0;
    private static final boolean NOT_AMBIENT = false;
    private static final boolean NO_PARTICLES = false;
    private static final boolean SHOWS_ICON = true;
    /**
     * Suffix appended to goo type id for potion names.
     */
    private static final String POTION_SUFFIX = "_goo";

    private GooPotions() {
    }

    /**
     * Registers one potion per type in {@link #POTION_TYPES} and subscribes
     * the deferred register to the mod event bus. Called once from Goo, after
     * GooMobEffects, whose brew effects the potions carry.
     *
     * @param modEventBus the mod event bus
     */
    public static void register(IEventBus modEventBus) {
        for (ResourceKey<GooTypeDefinition> key : POTION_TYPES) {
            String name = potionName(key);
            REGISTERED.put(key, POTIONS.register(name, () -> createPotion(key, name)));
        }
        POTIONS.register(modEventBus);
    }

    /**
     * The potion's registry path: the type id and the goo suffix.
     *
     * @param key the bundled type's key
     * @return the potion path
     */
    public static String potionName(ResourceKey<GooTypeDefinition> key) {
        return key.identifier().getPath() + POTION_SUFFIX;
    }

    /**
     * Creates a type's potion: its brew effect for {@link #BREW_DURATION}.
     *
     * @param key  the bundled type's key
     * @param name the potion's registry path
     * @return the configured potion
     */
    private static Potion createPotion(ResourceKey<GooTypeDefinition> key, String name) {
        // brew-runs-the-crawl-prepaid-on-a-shown-clock: the brew shows its icon and time, never particles
        return new Potion(name, new MobEffectInstance(GooMobEffects.BREW_EFFECTS.get(key), BREW_DURATION, NO_AMPLIFIER,
                NOT_AMBIENT, NO_PARTICLES, SHOWS_ICON));
    }
}
