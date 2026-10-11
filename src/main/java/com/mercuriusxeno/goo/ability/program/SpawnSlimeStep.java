package com.mercuriusxeno.goo.ability.program;

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
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.phys.Vec3;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Births a slime where the blob lands and finishes: the players watching
 * see the goo morph into the slime right where it splatted. Weird's Slime is
 * {@code spawn_slime goo=weird size=1}.
 * slime-creates-slimes-at-will
 * model-transformation-is-one-animation
 *
 * @param goo  the goo type of the blob that becomes the slime
 * @param size the slime's size, one the smallest
 */
public record SpawnSlimeStep(ResourceKey<GooTypeDefinition> goo, int size) implements Step {

    private static final String NAME = "spawn_slime";
    private static final String FIELD_GOO = "goo";
    private static final String FIELD_SIZE = "size";
    private static final int SMALLEST = 1;
    /** Game ticks the blob takes to become the mob it births. */
    static final int TRANSFORMATION_TICKS = 16;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<SpawnSlimeStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            GooTypes.ID_CODEC.fieldOf(FIELD_GOO).forGetter(SpawnSlimeStep::goo),
            Codec.intRange(SMALLEST, Slime.MAX_NATURAL_SIZE).optionalFieldOf(FIELD_SIZE, SMALLEST)
                    .forGetter(SpawnSlimeStep::size)
    ).apply(inst, SpawnSlimeStep::new));

    /**
     * The registered type.
     */
    public static final StepType<SpawnSlimeStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<SpawnSlimeStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        MobSpawnHost host = context.hostAs(MobSpawnHost.class);
        ServerLevel level = host.level();
        Slime slime = EntityType.SLIME.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (slime == null) {
            return true;
        }
        BlockPos cell = host.spawnCell();
        Vec3 feet = Vec3.atBottomCenterOf(cell);
        slime.setSize(size, true);
        slime.snapTo(feet.x, feet.y, feet.z, 0f, 0f);
        TransformationPayload morph = new TransformationPayload(goo, host.morphFrom(), host.morphFrom(),
                slime.getId(), TRANSFORMATION_TICKS);
        spawnAnnounced(slime, () -> BlockVisuals.sendToWatchers(level, cell, morph), level::addFreshEntity);
        return true;
    }

    /**
     * Announces a birth's transformation, then adds the newborn to the level.
     * Adding it sends its spawn to the watchers at once, so the
     * transformation goes first: the client keys it by the newborn's id,
     * which it holds from construction, and draws it at nothing from its
     * first frame.
     *
     * @param born     the newborn, its id and position set
     * @param announce sends the transformation to the watchers
     * @param add      adds the newborn to the level, which sends its spawn to the watchers at once
     * @param <E>      the newborn's type
     */
    static <E> void spawnAnnounced(E born, Runnable announce, Consumer<E> add) {
        announce.run();
        add.accept(born);
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.SPAWN_MOB);
    }
}
