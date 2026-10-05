package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.network.GhostTrailPayload;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Lays a trail of ghosts of the host's target along the jump it just made
 * and finishes: every client tracking the target, the target among them,
 * draws translucent echoes of it from where the teleport step before this
 * one took it to where it stands. A target no teleport moved lays no trail.
 * Ender blink is {@code ghost_trail goo=ender} after its teleport step.
 * Decision ghost-trail-spans-the-blink.
 *
 * @param goo  the goo type whose color the ghosts wear
 * @param life the ticks the trail takes to fade out, evaluated when the step runs
 */
public record GhostTrailStep(ResourceKey<GooTypeDefinition> goo, Expr life) implements Step {

    private static final String NAME = "ghost_trail";
    private static final String FIELD_GOO = "goo";
    private static final String FIELD_LIFE = "life";
    /** Ticks the trail fades over when the JSON names no life. */
    private static final int DEFAULT_LIFE_TICKS = 20;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<GhostTrailStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            GooTypes.ID_CODEC.fieldOf(FIELD_GOO).forGetter(GhostTrailStep::goo),
            Expr.CODEC.optionalFieldOf(FIELD_LIFE, Expr.literal(DEFAULT_LIFE_TICKS)).forGetter(GhostTrailStep::life)
    ).apply(inst, GhostTrailStep::new));

    /**
     * The registered type.
     */
    public static final StepType<GhostTrailStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<GhostTrailStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        LivingEntity target = context.hostAs(TargetHost.class).target();
        if (target.hasData(GooAttachments.JUMP_SOURCE)) {
            EntityVisuals.sendToWatchers(target, new GhostTrailPayload(target.getId(),
                    target.getData(GooAttachments.JUMP_SOURCE), target.position(), goo, life.evaluateInt(context)));
            target.removeData(GooAttachments.JUMP_SOURCE);
        }
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
