package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.colonize.ShroomNetwork;
import com.mercuriusxeno.goo.block.ability.AbilityBlock;
import com.mercuriusxeno.goo.block.ability.AbilityBlockEntity;
import com.mercuriusxeno.goo.block.ability.PrismBlock;
import com.mercuriusxeno.goo.network.BlockVisuals;
import com.mercuriusxeno.goo.network.TransformationPayload;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooParticles;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * The {@link StepHost} over the cell a thrown world ability's blob lands in:
 * world actions anchor at the cell's center, or at the aimed point for an
 * ability aiming one, a placed block goes into the
 * cell, and an ability that lingers stands its own block there (decisions
 * splat-runs-the-program-no-fuse, lingering-abilities-place-their-own-thing).
 * The landing runs in one tick with nothing ticking it afterwards, so this
 * host implements no {@link TickingHost}; a step that waits runs in the
 * lingering block instead.
 *
 * @param level       the server level
 * @param cell        the cell the blob landed in
 * @param face        the face of the struck block the blob landed on
 * @param waterlogged whether the cell holds water a standing block keeps
 * @param gooType     the goo type thrown
 * @param abilityId   the ability the blob names
 * @param anchor      where world actions anchor: the aimed point for an ability
 *                    aiming one, the cell's center otherwise (decision aim-point-follows-the-cursor)
 */
public record LandingHost(ServerLevel level, BlockPos cell, Direction face, boolean waterlogged,
                          ResourceKey<GooTypeDefinition> gooType, String abilityId, Vec3 anchor)
        implements PlacedFaceHost, ExplodeHost, AnchoredWorldHost, PlaceBlockHost, LingerHost, BlockBreakHost,
        ColonizeHost, FloorScanHost {

    private static final String ERR_UNKNOWN_BLOCK = "No block is registered as ";

    @Override
    public Direction.Axis burstAxis() {
        return face.getAxis();
    }

    @Override
    public HostKind kind() {
        return HostKind.LANDING;
    }

    @Override
    public OptionalDouble read(String name) {
        return OptionalDouble.empty();
    }

    @Override
    public BlockPos position() {
        return cell;
    }

    @Override
    public Direction placedFace() {
        return face;
    }

    /**
     * Explodes at the anchor with Goo's explosion and vanilla's boom, its
     * particles swapped for none, since the landing's burnout explosion is
     * drawn by its goo type (decision elemental-explosion-per-type).
     */
    @Override
    public void explode(float power, ExplosionMode mode) {
        GooExplosion.detonate(level, anchor(), power, mode, GooExplosion.Look.silent(GooParticles.SILENT_BLAST.get()));
    }

    @Override
    public void placeBlock(Identifier block, Map<String, String> state) {
        Block found = BuiltInRegistries.BLOCK.getOptional(block)
                .orElseThrow(() -> new IllegalArgumentException(ERR_UNKNOWN_BLOCK + block));
        List<Property.Value<?>> values = StatePropertyWriter.resolve(found.getStateDefinition(), state, block);
        if (found instanceof PrismBlock) {
            // prism-blob-becomes-a-milky-quartz-crystal: announced before the block, so the client
            // holds the transformation when the prism's renderer first draws it
            BlockVisuals.sendToWatchers(level, cell, TransformationPayload.intoBlock(gooType, anchor,
                    Vec3.atCenterOf(cell), cell, CloneEntityStep.TRANSFORMATION_TICKS));
        }
        level.setBlock(cell, StatePropertyWriter.write(found.defaultBlockState(), values), Block.UPDATE_ALL);
    }


    @Override
    public void linger(List<Step> steps) {
        level.setBlock(cell, GooBlocks.ABILITY_BLOCK.get().defaultBlockState()
                .setValue(AbilityBlock.WATERLOGGED, waterlogged), Block.UPDATE_ALL);
        if (level.getBlockEntity(cell) instanceof AbilityBlockEntity be) {
            be.stand(gooType, face, abilityId, steps);
        }
    }

    /**
     * Grows from the block the blob struck: the struck block's network
     * spreads, and a block on no network grows nothing
     * (decision colonize-blob-grows-the-network).
     */
    @Override
    public boolean colonize(int radius) {
        BlockPos struck = cell.relative(face.getOpposite());
        BlockPos landedOn = level.getBlockState(cell).isAir() ? struck : cell;
        Optional<ShroomNetwork> network = ShroomNetwork.of(level.getBlockState(landedOn));
        network.ifPresent(grows -> grows.spread(level, struck, radius));
        return network.isPresent();
    }
}
