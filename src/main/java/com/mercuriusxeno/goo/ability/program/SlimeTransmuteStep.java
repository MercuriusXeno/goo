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
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Transmutes the struck slime into a random mob the biome it stands in
 * spawns naturally, hostile or peaceful, and finishes: the slime is
 * consumed, no egg is spent, and the players watching see the goo morph
 * into the mob where the slime stood; the steps under it then run on the
 * new mob. A blob on anything but a slime does nothing. Gloom's turn of a
 * slime standing on it is {@code transmute_slime goo=nether hostile=true};
 * {@code hostile=false} draws a peaceful mob.
 * spawn-hostile-shape-peaceful-from-a-slime
 * gloom-turns-slimes-into-random-hostiles-in-place-of-spawn
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
    public static final int DEFAULT_MORPH_TICKS = 16;
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
        TargetHost host = context.hostAs(TargetHost.class);
        LivingEntity target = host.target();
        if (takesEntity(target) && target.level() instanceof ServerLevel level) {
            transmuteDrawn(level, (Slime) target, host.thrower());
        }
        return true;
    }

    /**
     * Transmutes a slime into a mob drawn from what its biome spawns
     * naturally, of the step's side, and runs the step's steps on the mob
     * born. A slime standing on Gloom turns this way.
     * spawn-hostile-shape-peaceful-from-a-slime
     * gloom-turns-slimes-into-random-hostiles-in-place-of-spawn
     *
     * @param level   the level
     * @param slime   the slime
     * @param thrower the entity behind the goo, or null where none is known
     * @return the mob born, empty where the biome spawns nothing that fits
     */
    public Optional<Entity> transmuteDrawn(ServerLevel level, Slime slime, @Nullable Entity thrower) {
        Optional<Entity> born = NaturalSpawns.drawAt(level, slime.blockPosition(), level.getRandom(),
                        categoryFilter(hostile), type -> type != EntityTypes.SLIME)
                .flatMap(type -> transmute(level, slime, type, goo, morphTicks));
        born.ifPresent(mob -> runOn(level, mob, thrower));
        return born;
    }

    /**
     * Transmutes a slime into a mob of a type: the slime is consumed, and a
     * mob of the type stands in its cell while the players watching see the
     * goo morph into it from where the slime stood. A slime standing on
     * Stitch and one standing on Gloom both turn this way.
     * spawn-hostile-shape-peaceful-from-a-slime
     * stitch-is-yarns-residual-that-turns-slimes-into-passive-mobs
     * model-transformation-is-one-animation
     *
     * @param level      the level
     * @param slime      the slime
     * @param type       the type it becomes
     * @param goo        the goo type that morphs into the mob
     * @param morphTicks the game ticks the morph takes
     * @return the mob born, empty where the type makes none
     */
    public static Optional<Entity> transmute(ServerLevel level, Slime slime, EntityType<?> type,
                                             ResourceKey<GooTypeDefinition> goo, int morphTicks) {
        Entity entity = type.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (entity == null) {
            return Optional.empty();
        }
        BlockPos cell = slime.blockPosition();
        Vec3 morphFrom = slime.getBoundingBox().getCenter();
        slime.discard();
        Vec3 feet = Vec3.atBottomCenterOf(cell);
        entity.snapTo(feet.x, feet.y, feet.z, level.getRandom().nextFloat() * FULL_TURN_DEGREES, 0f);
        if (entity instanceof Mob mob) {
            EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(cell), EntitySpawnReason.MOB_SUMMONED,
                    null);
        }
        TransformationPayload morph = new TransformationPayload(goo, morphFrom, morphFrom, entity.getId(), morphTicks);
        spawnAnnounced(entity, () -> BlockVisuals.sendToWatchers(level, cell, morph), level::addFreshEntity);
        return Optional.of(entity);
    }


    /**
     * Transmute takes a slime.
     * decision steps-declare-the-entities-they-take
     *
     * @param entity the candidate
     * @return true where the step acts on it
     */
    @Override
    public boolean takesEntity(Entity entity) {
        return entity instanceof Slime;
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
     * Runs the steps on the born mob, hosted with the slime's thrower so a
     * hit among them names the player behind the goo.
     * direct-goo-hit-credits-its-caster
     * goo-hit-keeps-its-kind-and-names-the-player-behind-it
     *
     * @param level   the level
     * @param born    the mob the slime became
     * @param thrower the entity that threw the goo at the slime, or null when unknown
     */
    private void runOn(ServerLevel level, Entity born, @Nullable Entity thrower) {
        if (born instanceof LivingEntity living && !steps.isEmpty()) {
            new ProgramBehavior(steps).tick(new EntityHost(level, living, thrower));
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
