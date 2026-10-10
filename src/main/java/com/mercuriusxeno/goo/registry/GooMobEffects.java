package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.GooBrewEffect;
import com.mercuriusxeno.goo.ability.spray.MycosisEffect;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
    /** Aeon brew's movement speed share, vanilla Speed I's. */
    static final double HASTE_SPEED = 0.2;
    /** Aeon brew's mining speed share, vanilla Haste I's. */
    static final double HASTE_MINING = 0.2;
    /** Aeon brew's attack speed share, vanilla Haste I's. */
    static final double HASTE_ATTACK = 0.1;
    private static final Identifier SPEED_MODIFIER = Identifier.fromNamespaceAndPath(Goo.MODID, "aeon_brew_speed");
    private static final Identifier MINING_MODIFIER = Identifier.fromNamespaceAndPath(Goo.MODID, "aeon_brew_mining");
    private static final Identifier ATTACK_MODIFIER = Identifier.fromNamespaceAndPath(Goo.MODID, "aeon_brew_attack");

    /**
     * Goo's spore poison, which the undead take as readily as the living
     * (decision mycosis-spore-stream-buds-and-poisons).
     */
    public static final DeferredHolder<MobEffect, MobEffect> MYCOSIS =
            MOB_EFFECTS.register("mycosis", MycosisEffect::new);

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
            REGISTERED.put(key, MOB_EFFECTS.register(brewEffectName(key), () -> brewEffectOf(key)));
        }
        MOB_EFFECTS.register(modEventBus);
    }

    /**
     * A type's brew effect; aeon's carries Haste's speed and haste itself, so
     * the effect list shows the one aeon brew icon and no vanilla effects
     * (decision haste-stacks-speed-under-the-golden-overlay).
     *
     * @param key the bundled type's key
     * @return the effect
     */
    static MobEffect brewEffectOf(ResourceKey<GooTypeDefinition> key) {
        GooBrewEffect brew = new GooBrewEffect(key);
        if (key == GooTypes.AEON) {
            brew.addAttributeModifier(Attributes.MOVEMENT_SPEED, SPEED_MODIFIER, HASTE_SPEED,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            brew.addAttributeModifier(Attributes.BLOCK_BREAK_SPEED, MINING_MODIFIER, HASTE_MINING,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            brew.addAttributeModifier(Attributes.ATTACK_SPEED, ATTACK_MODIFIER, HASTE_ATTACK,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        }
        return brew;
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
