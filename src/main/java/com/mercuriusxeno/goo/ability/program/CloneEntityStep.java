package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.network.TransformationPayload;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Spawns a fresh entity of the host's target's type beside it on a roll
 * and finishes; the players watching see a blob of the goo hop off the
 * target and transform into the clone. Vital clone is
 * {@code clone_entity chance="100 / pow(max_health, 0.6)" goo=vital}.
 * Decision model-transformation-is-one-animation.
 *
 * @param chance the percent chance of a clone, evaluated when the step runs
 * @param goo    the goo type of the blob that becomes the clone
 */
public record CloneEntityStep(Expr chance, ResourceKey<GooTypeDefinition> goo) implements Step {

    private static final String NAME = "clone_entity";
    private static final float PERCENT = 100;
    private static final String FIELD_CHANCE = "chance";
    private static final String FIELD_GOO = "goo";
    /** Game ticks the blob takes to hop off the target and become the clone. */
    static final int TRANSFORMATION_TICKS = 16;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<CloneEntityStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_CHANCE).forGetter(CloneEntityStep::chance),
            GooTypes.ID_CODEC.fieldOf(FIELD_GOO).forGetter(CloneEntityStep::goo)
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
        LivingEntity target = context.hostAs(TargetHost.class).target();
        ServerLevel level = (ServerLevel) target.level();
        if (level.getRandom().nextFloat() * PERCENT < chancePercent) {
            spawnClone(target, level);
        }
        return true;
    }

    /**
     * Spawns a fresh entity of the target's type a gaussian step away on
     * each horizontal axis, and tells the target's watchers to play the
     * blob becoming it, before the clone's own spawn reaches them.
     *
     * @param target the entity cloned
     * @param level  the level the clone joins
     */
    private void spawnClone(LivingEntity target, ServerLevel level) {
        Entity clone = target.getType().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (clone == null) {
            return;
        }
        RandomSource random = level.getRandom();
        clone.setPos(target.getX() + random.nextGaussian(), target.getY(), target.getZ() + random.nextGaussian());
        TransformationPayload transformation = new TransformationPayload(goo, target.getBoundingBox().getCenter(),
                clone.getBoundingBox().getCenter(), clone.getId(), TRANSFORMATION_TICKS);
        spawnAnnounced(clone, () -> EntityVisuals.sendToWatchers(target, transformation), level::addFreshEntity);
    }

    /**
     * Announces a clone's transformation, then adds the clone to the level.
     * Adding it sends its spawn to the watchers at once, so the
     * transformation goes first: the client keys it by the clone's id,
     * which the clone holds from construction, and draws the clone at
     * nothing from its first frame.
     *
     * @param clone    the clone, its id and position set
     * @param announce sends the transformation to the watchers
     * @param add      adds the clone to the level, which sends its spawn to the watchers at once
     * @param <E>      the clone's type
     */
    static <E> void spawnAnnounced(E clone, Runnable announce, Consumer<E> add) {
        announce.run();
        add.accept(clone);
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
