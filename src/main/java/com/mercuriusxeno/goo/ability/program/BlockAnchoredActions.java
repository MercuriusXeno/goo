package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The world actions a host anchored at a block shares whatever block it
 * is, the ability block or a tap's landing: entity scans handing each body
 * an entity host with no thrower, and particle bursts spread along the
 * anchor's axis.
 */
final class BlockAnchoredActions {

    private static final String ERR_UNKNOWN_BLOCK = "Place step names block which no registry holds: ";

    private BlockAnchoredActions() {
    }

    /**
     * Writes a block into the cell beyond a struck face when that cell can be
     * replaced and the block can stand there, so a drip or a spore never
     * overwrites a standing block nor leaves one that would break at once.
     *
     * @param level   the server level
     * @param struck  the block whose face was struck
     * @param face    the struck face
     * @param block   the block's registry id
     * @param state   each state property, by name, to its value
     */
    static void placeBeyondFace(ServerLevel level, BlockPos struck, Direction face, Identifier block,
                                Map<String, String> state) {
        BlockPos cell = struck.relative(face);
        if (!level.getBlockState(cell).canBeReplaced()) {
            return;
        }
        Block found = BuiltInRegistries.BLOCK.getOptional(block)
                .orElseThrow(() -> new IllegalArgumentException(ERR_UNKNOWN_BLOCK + block));
        List<Property.Value<?>> values = StatePropertyWriter.resolve(found.getStateDefinition(), state, block);
        BlockState written = StatePropertyWriter.write(found.defaultBlockState(), values);
        if (written.canSurvive(level, cell)) {
            level.setBlock(cell, written, Block.UPDATE_ALL);
        }
    }

    /**
     * Hands the body an entity host for each living entity in the volume
     * that every filter keeps.
     *
     * @param level   the server level
     * @param center  the volume's center
     * @param shape   the volume shape
     * @param radius  the volume radius
     * @param filters the filters an entity must pass
     * @param body    what to run on each entity's host
     */
    static void forEachEntityWithin(ServerLevel level, Vec3 center, SelectionShape shape, double radius,
                                    Set<EntityFilter> filters, Consumer<TargetHost> body) {
        EntityScan.forEachLivingWithin(level, center, shape, radius, filters, null,
                living -> body.accept(new EntityHost(level, living, null)));
    }

    /**
     * Hands the body an entity host for the living entity with this id, when
     * one still stands in the level.
     *
     * @param level    the server level
     * @param entityId the entity's id
     * @param body     what to run on the entity's host
     */
    static void forEntity(ServerLevel level, int entityId, Consumer<TargetHost> body) {
        if (level.getEntity(entityId) instanceof LivingEntity living && living.isAlive()) {
            body.accept(new EntityHost(level, living, null));
        }
    }

    /**
     * Sends a particle burst centered above the anchor, spread along the
     * anchor's axis by the burst's along spread and across it by the other.
     *
     * @param level  the server level
     * @param center the anchor
     * @param along  the anchor's axis
     * @param burst  the burst
     */
    static void sendBurst(ServerLevel level, Vec3 center, Direction.Axis along, ParticleBurst burst) {
        SimpleParticles.resolve(burst.particle()).ifPresent(particle -> level.sendParticles(particle,
                center.x(), center.y() + burst.lift(), center.z(), burst.count(),
                spreadOn(Direction.Axis.X, along, burst), spreadOn(Direction.Axis.Y, along, burst),
                spreadOn(Direction.Axis.Z, along, burst), burst.speed()));
    }

    /**
     * Picks the burst's spread for one axis: along where the axis is the
     * anchor's, across otherwise.
     *
     * @param axis  the axis to spread on
     * @param along the anchor's axis
     * @param burst the burst
     * @return the spread on the axis
     */
    private static double spreadOn(Direction.Axis axis, Direction.Axis along, ParticleBurst burst) {
        return axis == along ? burst.spreadAlong() : burst.spreadAcross();
    }
}
