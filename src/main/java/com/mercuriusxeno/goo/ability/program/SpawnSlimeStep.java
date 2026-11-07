package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.network.BlockVisuals;
import com.mercuriusxeno.goo.network.TransformationPayload;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.phys.Vec3;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Births a slime where the blob lands and finishes: the players watching
 * see the goo morph into the slime right where it splatted, and the slime
 * comes to life once the morph ends, never before. Weird's Slime is
 * {@code spawn_slime goo=weird size=1}.
 * slime-creates-slimes-at-will
 * slime-blob-morphs-into-the-slime-it-becomes
 * model-transformation-is-one-animation
 *
 * @param goo  the goo type of the blob that becomes the slime
 * @param size the slime's size, one the smallest
 */
public record SpawnSlimeStep(ResourceKey<GooTypeDefinition> goo, int size) implements TakesNoEntity {

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
        // slime-blob-morphs-into-the-slime-it-becomes: the client morphs into a stand-in under the slime's id,
        // and the slime's own spawn replaces it once the morph ends
        BlockVisuals.sendToWatchers(level, cell, morphOf(goo, host.morphFrom(), slime.getId()));
        GooServerState.of(level.getServer()).morphBirths().holdUntil(level, slime,
                level.getServer().getTickCount() + TRANSFORMATION_TICKS);
        return true;
    }

    /**
     * The transformation of the landed blob into the slime it births, in
     * place where the blob splatted: a morph whose ends sit apart hops the
     * blob up and down first, which the operator ruled out.
     * slime-blob-morphs-into-the-slime-it-becomes
     *
     * @param goo     the blob's goo type
     * @param splat   where the blob landed
     * @param slimeId the id the held slime holds
     * @return the transformation the watchers play
     */
    static TransformationPayload morphOf(ResourceKey<GooTypeDefinition> goo, Vec3 splat, int slimeId) {
        return TransformationPayload.intoHeldEntity(goo, splat, splat, slimeId,
                BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.SLIME), TRANSFORMATION_TICKS);
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
