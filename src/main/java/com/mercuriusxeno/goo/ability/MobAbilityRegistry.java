package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.mob.MobAbilities;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * String-keyed registry of entity effect handlers. Each handler name
 * corresponds to one data-driven entity_effect behavior's "handler"
 * param. The actual effect logic lives in GooMobEffects; this registry
 * maps handler names to those methods.
 */
public final class MobAbilityRegistry {

    private static final Map<String, Consumer<Context>> HANDLERS = new HashMap<>();

    static {
        register(MobAbilities.CRYSTAL_FLECHETTES, ctx -> MobAbilities.applyNamed(MobAbilities.CRYSTAL_FLECHETTES, ctx));
        register(MobAbilities.ROCK_PETRIFY, ctx -> MobAbilities.applyNamed(MobAbilities.ROCK_PETRIFY, ctx));
        register(MobAbilities.BLAZE_IGNITE, ctx -> MobAbilities.applyNamed(MobAbilities.BLAZE_IGNITE, ctx));
        register(MobAbilities.GLOW_LASER, ctx -> MobAbilities.applyNamed(MobAbilities.GLOW_LASER, ctx));
        register(MobAbilities.ENDER_TELEPORT, ctx -> MobAbilities.applyNamed(MobAbilities.ENDER_TELEPORT, ctx));
    }

    private MobAbilityRegistry() {
    }

    /**
     * Registers a named entity effect handler.
     *
     * @param name    the handler name matching JSON "handler" param
     * @param handler the effect consumer
     */
    public static void register(String name, Consumer<Context> handler) {
        HANDLERS.put(name, handler);
    }

    /**
     * Looks up a handler by name.
     *
     * @param name the handler name
     * @return the handler, or null if not registered
     */
    public static @Nullable Consumer<Context> get(String name) {
        return HANDLERS.get(name);
    }

    /**
     * Dispatch context for entity effect handlers.
     *
     * @param level   the current level
     * @param target  the entity being affected
     * @param thrower the entity that threw the blob, or null
     */
    public record Context(Level level, LivingEntity target, @Nullable Entity thrower) {
    }
}
