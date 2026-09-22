package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.alchemy.Potion;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Registers one potion per bundled goo type, keyed by the type's registry
 * key. Decision potions-stay-per-type: a type a datapack adds has no potion
 * until abilities are data-driven, so registration walks {@link #POTION_TYPES},
 * the bundled keys, rather than the registry. Registration happens in
 * {@link #register}, not at class load, so the key set reads without a
 * registry bootstrap.
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
     * Standard long potion duration: 60 seconds at 20 tps.
     */
    private static final int DURATION_LONG = 1200;
    /**
     * Standard medium potion duration: 30 seconds at 20 tps.
     */
    private static final int DURATION_MEDIUM = 600;
    /**
     * Standard short potion duration: 10 seconds at 20 tps.
     */
    private static final int DURATION_SHORT = 200;
    /**
     * Very short potion duration: 5 seconds at 20 tps.
     */
    private static final int DURATION_BRIEF = 100;
    /**
     * Amplifier level 2 (third tier).
     */
    private static final int AMPLIFIER_II = 2;
    /**
     * Suffix appended to goo type id for potion names.
     */
    private static final String POTION_SUFFIX = "_goo";
    /**
     * Per-type potion factory: each entry produces a themed potion from its name.
     */
    private static final Map<ResourceKey<GooTypeDefinition>, Function<String, Potion>> POTION_FACTORIES =
            Map.ofEntries(
                    Map.entry(GooTypes.METAL, (String n) -> new Potion(n,
                            new MobEffectInstance(MobEffects.SLOW_FALLING, DURATION_LONG, 0),
                            new MobEffectInstance(MobEffects.STRENGTH, DURATION_LONG, 1))),
                    Map.entry(GooTypes.CRYSTAL, (String n) -> new Potion(n,
                            new MobEffectInstance(MobEffects.NIGHT_VISION, DURATION_LONG, 0))),
                    Map.entry(GooTypes.LEAF, (String n) -> new Potion(n,
                            new MobEffectInstance(MobEffects.REGENERATION, DURATION_MEDIUM, 0))),
                    Map.entry(GooTypes.VITAL, (String n) -> new Potion(n,
                            new MobEffectInstance(MobEffects.INSTANT_HEALTH, 1, 1),
                            new MobEffectInstance(MobEffects.SATURATION, DURATION_MEDIUM, 0))),
                    Map.entry(GooTypes.SHROOM, (String n) -> new Potion(n,
                            new MobEffectInstance(MobEffects.NIGHT_VISION, DURATION_MEDIUM, 0),
                            new MobEffectInstance(MobEffects.NAUSEA, DURATION_SHORT, 0))),
                    Map.entry(GooTypes.ROCK, (String n) -> new Potion(n,
                            new MobEffectInstance(MobEffects.ABSORPTION, DURATION_LONG, AMPLIFIER_II))),
                    Map.entry(GooTypes.BLAZE, (String n) -> new Potion(n,
                            new MobEffectInstance(MobEffects.FIRE_RESISTANCE, DURATION_LONG, 0),
                            new MobEffectInstance(MobEffects.STRENGTH, DURATION_MEDIUM, 1))),
                    Map.entry(GooTypes.FROST, (String n) -> new Potion(n,
                            new MobEffectInstance(MobEffects.FIRE_RESISTANCE, DURATION_MEDIUM, 0),
                            new MobEffectInstance(MobEffects.SLOWNESS, DURATION_MEDIUM, 0))),
                    Map.entry(GooTypes.TYPHOON, (String n) -> new Potion(n,
                            new MobEffectInstance(MobEffects.SLOW_FALLING, DURATION_MEDIUM, 0),
                            new MobEffectInstance(MobEffects.JUMP_BOOST, DURATION_MEDIUM, AMPLIFIER_II))),
                    Map.entry(GooTypes.GLOW, (String n) -> new Potion(n,
                            new MobEffectInstance(MobEffects.GLOWING, DURATION_LONG, 0))),
                    Map.entry(GooTypes.HEX, (String n) -> new Potion(n,
                            new MobEffectInstance(MobEffects.NIGHT_VISION, DURATION_LONG, 0),
                            new MobEffectInstance(MobEffects.UNLUCK, DURATION_MEDIUM, 0))),
                    Map.entry(GooTypes.PULSE, (String n) -> new Potion(n,
                            new MobEffectInstance(MobEffects.LUCK, DURATION_MEDIUM, 0))),
                    Map.entry(GooTypes.NETHER, (String n) -> new Potion(n,
                            new MobEffectInstance(MobEffects.WITHER, DURATION_BRIEF, 0),
                            new MobEffectInstance(MobEffects.RESISTANCE, DURATION_LONG, 1))),
                    Map.entry(GooTypes.ENDER, (String n) -> new Potion(n,
                            new MobEffectInstance(MobEffects.INVISIBILITY, DURATION_MEDIUM, 0))),
                    Map.entry(GooTypes.AEON, (String n) -> new Potion(n,
                            new MobEffectInstance(MobEffects.SLOWNESS, DURATION_LONG, 0))),
                    Map.entry(GooTypes.UNSTABLE, (String n) -> new Potion(n,
                            new MobEffectInstance(MobEffects.INSTANT_DAMAGE, 1, AMPLIFIER_II))));

    private GooPotions() {
    }

    /**
     * Registers one potion per type in {@link #POTION_TYPES} and subscribes
     * the deferred register to the mod event bus. Called once from Goo.
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
     * Whether a themed potion factory exists for a type, which every key in
     * {@link #POTION_TYPES} needs before {@link #register} runs.
     *
     * @param key a key in the goo type registry
     * @return true when a factory is defined for it
     */
    static boolean hasPotionFactory(ResourceKey<GooTypeDefinition> key) {
        return POTION_FACTORIES.containsKey(key);
    }

    /**
     * Creates a potion with mob effects themed to the given goo type.
     *
     * @param key  the bundled type's key
     * @param name the potion's registry path
     * @return the configured potion
     */
    private static Potion createPotion(ResourceKey<GooTypeDefinition> key, String name) {
        return POTION_FACTORIES.get(key).apply(name);
    }
}
