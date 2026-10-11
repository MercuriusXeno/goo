package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.hex.NaturalSpawns;
import com.mercuriusxeno.goo.network.BlockVisuals;
import com.mercuriusxeno.goo.network.TransformationPayload;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Transmutes the struck slime into a random mob the biome it stands in
 * spawns naturally, hostile or peaceful, and finishes: the slime is
 * consumed, no egg is spent, and the players watching see the goo morph
 * into the mob where the slime stood; the steps under it then run on the
 * new mob. A blob on anything but a slime does nothing. Zoo's Spawn is
 * {@code transmute_slime goo=zoo hostile=true}, Shape the same with
 * {@code hostile=false}.
 * spawn-hostile-shape-peaceful-from-a-slime
 * model-transformation-is-one-animation
 *
 * @param goo        the goo type that morphs into the mob
 * @param hostile    true draws a monster, false a mob of a peaceful category
 * @param morphTicks the game ticks the morph takes
 * @param steps      the steps run on the born mob, instant ones
 */
public record SlimeTransmuteStep(ResourceKey<GooTypeDefinition> goo, boolean hostile, int morphTicks,
                                 List<Step> steps) implements Step {

    private static final String NAME = "transmute_slime";
    private static final String FIELD_GOO = "goo";
    private static final String FIELD_HOSTILE = "hostile";
    private static final String FIELD_MORPH_TICKS = "morph_ticks";
    private static final String FIELD_STEPS = "steps";
    /** Game ticks a goo morph takes where the JSON names none. */
    static final int DEFAULT_MORPH_TICKS = 16;
    /** A whole turn, in degrees, over which the born mob's facing is drawn. */
    private static final float FULL_TURN_DEGREES = 360f;

    /**
     * Codec for the step's params. The list codec is read lazily because
     * {@link StepTypes} registers this type while building it.
     */
    public static final MapCodec<SlimeTransmuteStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            GooTypes.ID_CODEC.fieldOf(FIELD_GOO).forGetter(SlimeTransmuteStep::goo),
            Codec.BOOL.fieldOf(FIELD_HOSTILE).forGetter(SlimeTransmuteStep::hostile),
            Codec.INT.optionalFieldOf(FIELD_MORPH_TICKS, DEFAULT_MORPH_TICKS).forGetter(SlimeTransmuteStep::morphTicks),
            Codec.lazyInitialized(() -> StepTypes.LIST_CODEC).optionalFieldOf(FIELD_STEPS, List.of())
                    .forGetter(SlimeTransmuteStep::steps)
    ).apply(inst, SlimeTransmuteStep::new));

    /**
     * The registered type.
     */
    public static final StepType<SlimeTransmuteStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<SlimeTransmuteStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        if (context.hostAs(TargetHost.class).target() instanceof Slime slime
                && slime.level() instanceof ServerLevel level) {
            BlockPos cell = slime.blockPosition();
            Vec3 morphFrom = slime.getBoundingBox().getCenter();
            NaturalSpawns.drawAt(level, cell, level.getRandom(), categoryFilter(hostile), type -> type != EntityType.SLIME)
                    .ifPresent(type -> {
                        slime.discard();
                        bear(level, type, cell, morphFrom);
                    });
        }
        return true;
    }

    /**
     * The categories a draw takes from: monsters for a hostile draw, every
     * friendly category for a peaceful one.
     *
     * @param hostile whether the draw is hostile
     * @return the category filter
     */
    static Predicate<MobCategory> categoryFilter(boolean hostile) {
        return hostile ? category -> category == MobCategory.MONSTER : MobCategory::isFriendly;
    }

    /**
     * Makes a mob of the type standing in the slime's cell, announces the goo
     * morphing into it, adds it to the level and runs the steps on it.
     *
     * @param level     the level
     * @param type      the type drawn
     * @param cell      the cell the mob stands in
     * @param morphFrom where the goo morphs from, the slime's center
     */
    private void bear(ServerLevel level, EntityType<?> type, BlockPos cell, Vec3 morphFrom) {
        Entity entity = type.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (entity == null) {
            return;
        }
        Vec3 feet = Vec3.atBottomCenterOf(cell);
        entity.snapTo(feet.x, feet.y, feet.z, level.getRandom().nextFloat() * FULL_TURN_DEGREES, 0f);
        if (entity instanceof Mob mob) {
            EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(cell), EntitySpawnReason.MOB_SUMMONED,
                    null);
        }
        TransformationPayload morph = new TransformationPayload(goo, morphFrom, morphFrom, entity.getId(), morphTicks);
        spawnAnnounced(entity, () -> BlockVisuals.sendToWatchers(level, cell, morph), level::addFreshEntity);
        if (entity instanceof LivingEntity living && !steps.isEmpty()) {
            new ProgramBehavior(steps).tick(new EntityHost(level, living, null));
        }
    }

    /**
     * Announces a mob's transformation, then adds the mob to the level.
     * Adding it sends its spawn to the watchers at once, so the
     * transformation goes first: the client keys it by the mob's id, which
     * the mob holds from construction, and draws the mob at nothing from its
     * first frame.
     *
     * @param mob      the mob, its id and position set
     * @param announce sends the transformation to the watchers
     * @param add      adds the mob to the level, which sends its spawn to the watchers at once
     * @param <E>      the mob's type
     */
    static <E> void spawnAnnounced(E mob, Runnable announce, Consumer<E> add) {
        announce.run();
        add.accept(mob);
    }

    @Override
    public Stream<Step> children() {
        return steps.stream();
    }

    @Override
    public Stream<HostedStep> hostedChildren(HostKind host) {
        return steps.stream().map(child -> new HostedStep(child, HostKind.ENTITY));
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
