package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Spawns a fresh entity of the host's target's type beside it on a roll
 * and finishes. Vital clone is {@code clone_entity chance="100 / pow(max_health, 0.6)"}.
 *
 * @param chance the percent chance of a clone, evaluated when the step runs
 */
public record CloneEntityStep(Expr chance) implements Step {

    private static final String NAME = "clone_entity";
    private static final float PERCENT = 100;
    private static final String FIELD_CHANCE = "chance";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<CloneEntityStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_CHANCE).forGetter(CloneEntityStep::chance)
    ).apply(inst, CloneEntityStep::new));

    /**
     * The registered type.
     */
    public static final StepType<CloneEntityStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<CloneEntityStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        float chancePercent = chance.evaluateFloat(context);
        LivingEntity target = context.host().target();
        ServerLevel level = (ServerLevel) target.level();
        if (level.getRandom().nextFloat() * PERCENT < chancePercent) {
            spawnClone(target, level);
        }
        return true;
    }

    /**
     * Spawns a fresh entity of the target's type a gaussian step away on
     * each horizontal axis.
     *
     * @param target the entity cloned
     * @param level  the level the clone joins
     */
    private static void spawnClone(LivingEntity target, ServerLevel level) {
        Entity clone = target.getType().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (clone == null) {
            return;
        }
        RandomSource random = level.getRandom();
        clone.setPos(target.getX() + random.nextGaussian(), target.getY(), target.getZ() + random.nextGaussian());
        level.addFreshEntity(clone);
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(chance);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
