package com.mercuriusxeno.goo.ability.mob;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.ability.MobAbilityRegistry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Applies goo-type-specific mob effects when blobs hit living entities.
 * Each goo type has a unique effect dispatched through a type-indexed map.
 */
public final class MobAbilities {

    // ── Handler name constants; a type migrated to a program (metal, leaf, typhoon, shroom, nether, frost, pulse, aeon, unstable, hex, vital) has none ──
    public static final String CRYSTAL_FLECHETTES = "crystal_flechettes";
    public static final String ROCK_PETRIFY = "rock_petrify";
    public static final String BLAZE_IGNITE = "blaze_ignite";
    public static final String GLOW_LASER = "glow_laser";
    public static final String ENDER_TELEPORT = "ender_teleport";
    /**
     * Per-type effect handler map.
     */
    private static final Map<GooType, Consumer<EffectContext>> EFFECTS =
            new EnumMap<>(Map.ofEntries(
                    Map.entry(GooType.CRYSTAL, ctx -> CrystalFlechettes.apply(ctx.level(), ctx.target())),
                    Map.entry(GooType.ROCK, ctx -> RockPetrify.apply(ctx.level(), ctx.target())),
                    Map.entry(GooType.BLAZE, ctx -> BlazeIgnite.apply(ctx.level(), ctx.target())),
                    Map.entry(GooType.GLOW, ctx -> GlowLaser.apply(ctx.level(), ctx.target())),
                    Map.entry(GooType.ENDER, ctx -> EnderTeleport.apply(ctx.level(), ctx.target()))));
    /**
     * String-keyed handler map for data-driven entity_effect dispatch.
     */
    private static final Map<String, Consumer<EffectContext>> NAMED = buildNamedMap();

    private MobAbilities() {
    }

    /**
     * Applies the mob effect for the given goo type to a living entity.
     *
     * @param level   the world
     * @param target  the entity to affect
     * @param type    the goo type whose effect to apply
     * @param thrower the entity that threw the blob, or null if unknown
     */
    public static void apply(Level level, LivingEntity target, GooType type, @Nullable Entity thrower) {
        if (level.isClientSide()) {
            return;
        }
        var handler = EFFECTS.get(type);
        if (handler != null) {
            handler.accept(new EffectContext(level, target, thrower));
        }
    }

    /**
     * Applies a named entity effect handler. Used by EntityEffectRegistry
     * for data-driven ability dispatch.
     *
     * @param name the handler name (e.g., "crystal_flechettes")
     * @param ctx  the entity effect context
     */
    public static void applyNamed(String name, MobAbilityRegistry.Context ctx) {
        if (ctx.level().isClientSide()) {
            return;
        }
        var handler = NAMED.get(name);
        if (handler != null) {
            handler.accept(new EffectContext(ctx.level(), ctx.target(), ctx.thrower()));
        }
    }

    private static Map<String, Consumer<EffectContext>> buildNamedMap() {
        Map<String, Consumer<EffectContext>> map = new HashMap<>();
        map.put(CRYSTAL_FLECHETTES, ctx -> CrystalFlechettes.apply(ctx.level(), ctx.target()));
        map.put(ROCK_PETRIFY, ctx -> RockPetrify.apply(ctx.level(), ctx.target()));
        map.put(BLAZE_IGNITE, ctx -> BlazeIgnite.apply(ctx.level(), ctx.target()));
        map.put(GLOW_LASER, ctx -> GlowLaser.apply(ctx.level(), ctx.target()));
        map.put(ENDER_TELEPORT, ctx -> EnderTeleport.apply(ctx.level(), ctx.target()));
        return Map.copyOf(map);
    }

    /**
     * Dispatch context: all parameters an effect handler might need.
     */
    private record EffectContext(Level level, LivingEntity target, @Nullable Entity thrower) {
    }
}
