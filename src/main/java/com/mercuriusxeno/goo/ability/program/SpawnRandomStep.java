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
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Conjures a random mob the biome spawns naturally into the host's spawn
 * cell and finishes: the players watching see the goo morph into the mob
 * right where it splatted, the mob growing out of the shrinking blob, then
 * the steps under it run on the new mob. Hex
 * spawn is {@code spawn_random goo=hex morph_ticks=20 steps=[ailment_overlay,
 * afterimage]}; a cell nothing the biome spawns fits conjures nothing.
 * spawn-goo-morphs-into-the-mob-it-births
 *
 * <p>A tap rolls for each drip: {@code chance=5} conjures on one drip in
 * twenty (decision spawn-drip-rolls-a-fresh-spawn).
 *
 * @param goo        the goo type that morphs into the mob
 * @param morphTicks the game ticks the morph takes
 * @param steps      the steps run on the conjured mob, instant ones
 * @param chance     the percent chance a mob is conjured, evaluated when the step runs
 */
public record SpawnRandomStep(ResourceKey<GooTypeDefinition> goo, int morphTicks, List<Step> steps, Expr chance)
        implements Step {

    private static final String NAME = "spawn_random";
    private static final String FIELD_GOO = "goo";
    private static final String FIELD_MORPH_TICKS = "morph_ticks";
    private static final String FIELD_STEPS = "steps";
    private static final String FIELD_CHANCE = "chance";
    /** A whole chance, in percent: the throw always conjures. */
    private static final float PERCENT = 100;
    /** Game ticks a goo morph takes where the JSON names none. */
    static final int DEFAULT_MORPH_TICKS = 16;
    /** A whole turn, in degrees, over which the conjured mob's facing is drawn. */
    private static final float FULL_TURN_DEGREES = 360f;

    /**
     * Codec for the step's params. The list codec is read lazily because
     * {@link StepTypes} registers this type while building it.
     */
    public static final MapCodec<SpawnRandomStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            GooTypes.ID_CODEC.fieldOf(FIELD_GOO).forGetter(SpawnRandomStep::goo),
            Codec.INT.optionalFieldOf(FIELD_MORPH_TICKS, DEFAULT_MORPH_TICKS).forGetter(SpawnRandomStep::morphTicks),
            Codec.lazyInitialized(() -> StepTypes.LIST_CODEC).optionalFieldOf(FIELD_STEPS, List.of())
                    .forGetter(SpawnRandomStep::steps),
            Expr.CODEC.optionalFieldOf(FIELD_CHANCE, Expr.literal(PERCENT)).forGetter(SpawnRandomStep::chance)
    ).apply(inst, SpawnRandomStep::new));

    /**
     * The registered type.
     */
    public static final StepType<SpawnRandomStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<SpawnRandomStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        MobSpawnHost host = context.hostAs(MobSpawnHost.class);
        ServerLevel level = host.level();
        if (!rolls(chance.evaluateFloat(context), level.getRandom().nextFloat())) {
            return true;
        }
        NaturalSpawns.drawAt(level, host.spawnCell(), level.getRandom())
                .ifPresent(type -> conjure(level, type, host.spawnCell(), host.morphFrom()));
        return true;
    }

    /**
     * Makes a mob of the type standing in the cell, announces the goo
     * morphing into it, adds it to the level and runs the steps on it.
     *
     * @param level     the level
     * @param type      the type drawn
     * @param cell      the cell the mob stands in
     * @param morphFrom where the goo morphs from
     */
    private void conjure(ServerLevel level, EntityType<?> type, BlockPos cell, Vec3 morphFrom) {
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
        // spawn-goo-morphs-into-the-mob-it-births: the splat morphs in place, no hop
        TransformationPayload morph = new TransformationPayload(goo, morphFrom, morphFrom, entity.getId(),
                morphTicks);
        spawnAnnounced(entity, () -> BlockVisuals.sendToWatchers(level, cell, morph),
                level::addFreshEntity);
        if (entity instanceof LivingEntity living && !steps.isEmpty()) {
            new ProgramBehavior(steps).tick(new EntityHost(level, living, null));
        }
    }

    /**
     * Whether a conjure roll lands: a roll in [0, 1) lands under a percent chance.
     *
     * @param chancePercent the percent chance the step names
     * @param roll          the uniform roll in [0, 1)
     * @return true when the roll lands
     */
    static boolean rolls(float chancePercent, float roll) {
        return roll * PERCENT < chancePercent;
    }

    /**
     * Announces a mob's transformation, then adds the mob to the level.
     * Adding it sends its spawn to the watchers at once, so the
     * transformation goes first: the client keys it by the mob's id, which
     * the mob holds from construction, and draws the mob at nothing from its
     * first frame (decision model-transformation-is-one-animation).
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
        return Stream.of(chance);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.SPAWN_MOB);
    }
}
