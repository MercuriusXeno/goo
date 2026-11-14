package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.Goo;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Hurts the host's target and finishes. On the struck entity host the
 * target is the entity the goo hit, so the metal Dart is one
 * {@code damage amount=8 source=magic} step. The metal trap's impale is
 * {@code damage amount=6 source=stalagmite knockback=false}, pinning the
 * target where the spike caught it; the Shatter cloud's shred is
 * {@code damage amount=1 source=cactus invulnerable_ticks=1}, leaving the
 * target open to the next shred a tick later, beside a crit particles
 * step that shows each shred land. The step clears the
 * target's damage immunity before it hurts, so a goo hit lands through a
 * hit just taken.
 *
 * @param amount            the damage, evaluated when the step runs
 * @param source            the damage source
 * @param knockback         whether the hit may push the target
 * @param invulnerableTicks the immunity ticks the hit leaves, when set; none otherwise
 * @param iFrames           whether the hit respects the target's i-frames, landing only as vanilla lets a hit land
 *                          and leaving vanilla's immunity behind, rather than clearing it on both sides
 * @param healsUndead       whether an undead target is healed by the amount instead, as Decay's bite and Hive's
 *                          strike heal it; a hit respecting i-frames heals no faster than it would land, and a
 *                          living caster the hit reaches takes nothing
 */
public record DamageStep(Expr amount, DamageKind source, boolean knockback,
                         Optional<Expr> invulnerableTicks, boolean iFrames, boolean healsUndead) implements Step {

    /**
     * Expire's damage type, which the vanilla bypass tags name.
     * expire-kills-the-struck-mob
     */
    public static final ResourceKey<DamageType> EXPIRE =
            ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(Goo.MODID, "expire"));

    /**
     * Urchin's impale, goo's own copy of vanilla's stalagmite carrying its
     * death message. Vanilla's is_fall tag holds minecraft:stalagmite, so
     * every mob in the fall_damage_immune tag, a chicken, a cat, a bee, a
     * blaze, took Urchin's stab and no damage; goo:stalagmite stands in no
     * fall tag.
     * urchin-rests-bristled-and-spears
     */
    public static final ResourceKey<DamageType> STALAGMITE =
            ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(Goo.MODID, "stalagmite"));

    private static final String NAME = "damage";
    private static final String FIELD_AMOUNT = "amount";
    private static final String FIELD_SOURCE = "source";
    private static final String FIELD_KNOCKBACK = "knockback";
    private static final String FIELD_INVULNERABLE_TICKS = "invulnerable_ticks";
    private static final String FIELD_I_FRAMES = "i_frames";
    private static final String FIELD_HEALS_UNDEAD = "heals_undead";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<DamageStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_AMOUNT).forGetter(DamageStep::amount),
            DamageKind.CODEC.optionalFieldOf(FIELD_SOURCE, DamageKind.MAGIC).forGetter(DamageStep::source),
            Codec.BOOL.optionalFieldOf(FIELD_KNOCKBACK, true).forGetter(DamageStep::knockback),
            Expr.CODEC.optionalFieldOf(FIELD_INVULNERABLE_TICKS).forGetter(DamageStep::invulnerableTicks),
            Codec.BOOL.optionalFieldOf(FIELD_I_FRAMES, false).forGetter(DamageStep::iFrames),
            Codec.BOOL.optionalFieldOf(FIELD_HEALS_UNDEAD, false).forGetter(DamageStep::healsUndead)
    ).apply(inst, DamageStep::new));

    /**
     * The registered type.
     */
    public static final StepType<DamageStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * Creates a damage step whose hit may push the target and leaves the
     * source's own immunity, the defaults the JSON reads when both fields
     * are absent.
     *
     * @param amount the damage, evaluated when the step runs
     * @param source the damage source
     */
    public DamageStep(Expr amount, DamageKind source) {
        this(amount, source, true, Optional.empty());
    }

    /**
     * Creates a damage step that clears the target's immunity on both sides of its hit.
     *
     * @param amount            the damage, evaluated when the step runs
     * @param source            the damage source
     * @param knockback         whether the hit may push the target
     * @param invulnerableTicks the immunity ticks the hit leaves, when set; none otherwise
     */
    public DamageStep(Expr amount, DamageKind source, boolean knockback, Optional<Expr> invulnerableTicks) {
        this(amount, source, knockback, invulnerableTicks, false, false);
    }

    @Override
    public StepType<DamageStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        LivingEntity target = context.hostAs(TargetHost.class).target();
        if (healsUndead && healedOrSpared(target, context)) {
            return true;
        }
        // A goo hit clears damage immunity on both sides, so a touch and the melee hit of the same
        // attack-key press both land in full, in either order; a held bite respecting i-frames leaves it be.
        // decision attack-key-touches-plus-punches
        // decision decay-gnats-degrade-each-block-once
        if (!iFrames) {
            target.invulnerableTime = 0;
        }
        Vec3 before = target.getDeltaMovement();
        target.hurtServer((ServerLevel) target.level(), damageSource(target, context.hostAs(TargetHost.class).thrower()),
                amount.evaluateFloat(context));
        if (!knockback) {
            // An attacker's hit knocks back on the server, so no knockback restores the motion the hit found.
            target.setDeltaMovement(before);
            target.hurtMarked = false;
        }
        if (!iFrames) {
            target.invulnerableTime = invulnerableTicks.map(ticks -> ticks.evaluateInt(context)).orElse(0);
        }
        return true;
    }

    /**
     * Heals an undead target in place of the hit, or leaves a living caster
     * the hit reached as it stands.
     * decision decay-and-hive-heal-an-undead-target
     * decision undead-player-counts-as-undead-for-heal-and-decay
     *
     * @param target  the entity the hit reaches
     * @param context the tick's scope
     * @return true when the hit is spent without hurting
     */
    private boolean healedOrSpared(LivingEntity target, StepContext context) {
        if (UndeadInversion.inverts(target)) {
            UndeadInversion.healUndead(target, amount.evaluateFloat(context), iFrames);
            return true;
        }
        return UndeadInversion.sparesCaster(target, context.hostAs(TargetHost.class).thrower());
    }

    /**
     * Maps the step's damage kind to the target's damage source.
     *
     * @param target the entity being hurt
     * @return the damage source
     */
    private DamageSource damageSource(LivingEntity target, @Nullable Entity thrower) {
        Optional<ResourceKey<DamageType>> owned = gooTypeOf(source);
        if (owned.isPresent()) {
            return new DamageSource(target.level().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
                    .getOrThrow(owned.get()));
        }
        DamageSources sources = target.damageSources();
        return source == DamageKind.ATTACK ? attackBy(sources, thrower) : sourceOfItsOwn(sources);
    }

    /**
     * The damage type goo owns for a kind, read from the level's registry
     * when the step hurts; a kind vanilla's damage sources answer owns none.
     *
     * @param kind the step's damage kind
     * @return goo's damage type key, or empty for a vanilla kind
     */
    static Optional<ResourceKey<DamageType>> gooTypeOf(DamageKind kind) {
        return switch (kind) {
            case EXPIRE -> Optional.of(EXPIRE);
            case STALAGMITE -> Optional.of(STALAGMITE);
            case MAGIC, FREEZE, CACTUS, FORCE, ATTACK -> Optional.empty();
        };
    }

    /**
     * The source of a kind that names its own, owing nothing to the thrower.
     *
     * @param sources the level's damage sources
     * @return the damage source
     */
    private DamageSource sourceOfItsOwn(DamageSources sources) {
        return switch (source) {
            case MAGIC -> sources.magic();
            case FREEZE -> sources.freeze();
            case CACTUS -> sources.cactus();
            case FORCE, ATTACK, EXPIRE, STALAGMITE -> sources.generic();
        };
    }

    /**
     * The thrower's attack: a player's where a player threw, a mob's where a
     * mob did, generic where none stands.
     *
     * @param sources the level's damage sources
     * @param thrower the entity that threw the goo, or null
     * @return the damage source
     */
    private static DamageSource attackBy(DamageSources sources, @Nullable Entity thrower) {
        if (thrower instanceof Player player) {
            return sources.playerAttack(player);
        }
        return thrower instanceof LivingEntity mob ? sources.mobAttack(mob) : sources.generic();
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.concat(Stream.of(amount), invulnerableTicks.stream());
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
