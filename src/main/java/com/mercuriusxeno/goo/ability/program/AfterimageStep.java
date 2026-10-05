package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.network.AfterimagePayload;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Leaves an afterimage of the host's target where it stands and finishes:
 * every client tracking the target, the target among them, draws a ripple
 * of the model's silhouette edges, frozen in its pose, growing outward and
 * fading in the goo type's color. Ender blink leaves one before its
 * teleport step and one after, so the ripple plays where the player stood
 * and where it lands: {@code afterimage goo=ender}.
 * Decision afterimage-is-one-shared-effect.
 *
 * @param goo  the goo type whose color the ripple wears
 * @param life the ticks each silhouette takes to grow and fade, evaluated when the step runs
 */
public record AfterimageStep(ResourceKey<GooTypeDefinition> goo, Expr life) implements Step {

    private static final String NAME = "afterimage";
    private static final String FIELD_GOO = "goo";
    private static final String FIELD_LIFE = "life";
    /** Ticks each silhouette grows and fades over when the JSON names no life. */
    private static final int DEFAULT_LIFE_TICKS = 12;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<AfterimageStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            GooTypes.ID_CODEC.fieldOf(FIELD_GOO).forGetter(AfterimageStep::goo),
            Expr.CODEC.optionalFieldOf(FIELD_LIFE, Expr.literal(DEFAULT_LIFE_TICKS)).forGetter(AfterimageStep::life)
    ).apply(inst, AfterimageStep::new));

    /**
     * The registered type.
     */
    public static final StepType<AfterimageStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<AfterimageStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        LivingEntity target = context.hostAs(TargetHost.class).target();
        EntityVisuals.sendToWatchers(target,
                new AfterimagePayload(target.getId(), target.position(), goo, life.evaluateInt(context)));
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(life);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
