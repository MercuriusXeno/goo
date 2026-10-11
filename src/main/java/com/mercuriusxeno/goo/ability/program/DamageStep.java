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
 * target is the entity the goo hit, so the metal javelin is one
 * {@code damage amount=8 source=magic} step. The metal trap's impale is
 * {@code damage amount=6 source=stalagmite knockback=false}, pinning the
 * target where the spike caught it; the crystal cloud's shred is
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
 */
public record DamageStep(Expr amount, DamageKind source, boolean knockback,
                         Optional<Expr> invulnerableTicks) implements Step {

    /**
     * Expire's damage type, which the vanilla bypass tags name.
     * expire-kills-the-struck-mob
     */
    public static final ResourceKey<DamageType> EXPIRE =
            ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(Goo.MODID, "expire"));

    private static final String NAME = "damage";
    private static final String FIELD_AMOUNT = "amount";
    private static final String FIELD_SOURCE = "source";
    private static final String FIELD_KNOCKBACK = "knockback";
    private static final String FIELD_INVULNERABLE_TICKS = "invulnerable_ticks";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<DamageStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_AMOUNT).forGetter(DamageStep::amount),
            DamageKind.CODEC.optionalFieldOf(FIELD_SOURCE, DamageKind.MAGIC).forGetter(DamageStep::source),
            Codec.BOOL.optionalFieldOf(FIELD_KNOCKBACK, true).forGetter(DamageStep::knockback),
            Expr.CODEC.optionalFieldOf(FIELD_INVULNERABLE_TICKS).forGetter(DamageStep::invulnerableTicks)
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

    @Override
    public StepType<DamageStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        LivingEntity target = context.hostAs(TargetHost.class).target();
        // A goo hit clears damage immunity on both sides, so a touch and the melee hit of the same
        // attack-key press both land in full, in either order.
        // decision attack-key-touches-plus-punches
        target.invulnerableTime = 0;
        Vec3 before = target.getDeltaMovement();
        target.hurtServer((ServerLevel) target.level(), damageSource(target, context.hostAs(TargetHost.class).thrower()),
                amount.evaluateFloat(context));
        if (!knockback) {
            // An attacker's hit knocks back on the server, so no knockback restores the motion the hit found.
            target.setDeltaMovement(before);
            target.hurtMarked = false;
        }
        target.invulnerableTime = invulnerableTicks.map(ticks -> ticks.evaluateInt(context)).orElse(0);
        return true;
    }

    /**
     * Maps the step's damage kind to the target's damage source.
     *
     * @param target the entity being hurt
     * @return the damage source
     */
    private DamageSource damageSource(LivingEntity target, @Nullable Entity thrower) {
        if (source == DamageKind.EXPIRE) {
            return new DamageSource(target.level().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
                    .getOrThrow(EXPIRE));
        }
        DamageSources sources = target.damageSources();
        return source == DamageKind.ATTACK ? attackBy(sources, thrower) : sourceOfItsOwn(sources);
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
            case STALAGMITE -> sources.stalagmite();
            case CACTUS -> sources.cactus();
            case FORCE, ATTACK, EXPIRE -> sources.generic();
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
