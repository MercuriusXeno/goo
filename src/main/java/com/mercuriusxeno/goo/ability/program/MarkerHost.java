package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.GooHitCredit;
import com.mercuriusxeno.goo.block.ability.MarkerAnchor;
import com.mercuriusxeno.goo.network.ChainBurnoutPayload;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The {@link StepHost} over a marker anchor: reads the placed
 * face and goo type from the block entity, and acts on
 * the server level at the marker position. Built fresh each tick from
 * what the {@link ProgramBehavior} marker callbacks
 * hand over, so it holds no state of its own.
 *
 * @param level the server level
 * @param pos   the marker position
 * @param be    the anchor block entity
 */
public record MarkerHost(ServerLevel level, BlockPos pos, MarkerAnchor be)
        implements PlacedFaceHost, TickingHost, ExplodeHost, EntityScanHost, PlaceBlockHost,
        FieldEffectHost, StateWriteHost {

    private static final String ERR_UNKNOWN_BLOCK = "No block is registered as ";

    @Override
    public HostKind kind() {
        return HostKind.MARKER;
    }

    @Override
    public OptionalDouble read(String name) {
        // black-hole-leaves-a-compression-sphere: a sized cast's program reads the size it was dragged to
        return HostVariables.SIZE.equals(name) ? OptionalDouble.of(be.programState().castSize())
                : OptionalDouble.empty();
    }

    @Override
    public BlockPos position() {
        return pos;
    }

    @Override
    public Direction placedFace() {
        return be.getPlacedFace();
    }

    /**
     * Explodes at the marker with Goo's explosion in the look the step
     * wrote, beside the marker's burnout its goo type draws (decision
     * elemental-explosion-per-type), sent here as it explodes rather than
     * when the blob landed. Each hit names the player who stood the marker.
     * standing-goo-thing-credits-whoever-placed-it
     */
    @Override
    public void explode(float power, double reach, boolean gathers, ExplosionMode mode, ExplosionLook look) {
        burnout().sendToTracking(level);
        GooExplosion.detonate(level, Vec3.atCenterOf(pos), power, new GooExplosion.Cut(reach, gathers), mode,
                look.shown(), placer());
    }

    /**
     * The burnout this marker plays when it explodes, drawn by its goo type.
     *
     * @return the burnout at the marker
     */
    ChainBurnoutPayload burnout() {
        return new ChainBurnoutPayload(pos, be.getPlacedFace().ordinal(), GooTypes.id(be.getGooType()),
                be.getAbilityId(), be.programState().castSize());
    }

    @Override
    public boolean anyEntityWithin(SelectionShape shape, double radius, Set<EntityFilter> filters) {
        return EntityScan.anyEntityWithin(level, Vec3.atCenterOf(pos), shape, radius, filters, null, null);
    }

    @Override
    public void forEachEntityWithin(SelectionShape shape, double radius, Set<EntityFilter> filters,
                                    Consumer<TargetHost> body) {
        BlockAnchoredActions.forEachEntityWithin(level, Vec3.atCenterOf(pos), shape, radius, filters, placer(), body);
    }

    /**
     * The player who stood the marker's field, whom each hit it deals credits.
     * standing-goo-thing-credits-whoever-placed-it
     *
     * @return the player, or null where no player stood it or that player is not on the server
     */
    public @Nullable ServerPlayer placer() {
        return GooHitCredit.placerOf(level, be.placer());
    }

    @Override
    public void forEntity(int entityId, Consumer<TargetHost> body) {
        BlockAnchoredActions.forEntity(level, entityId, placer(), body);
    }

    @Override
    public FieldEffectState fieldEffect() {
        return be.programState().fieldEffect();
    }

    @Override
    public double rollFraction() {
        return level.getRandom().nextDouble();
    }

    @Override
    public void spawnParticles(ParticleBurst burst) {
        BlockAnchoredActions.sendBurst(level, Vec3.atCenterOf(pos), be.getPlacedFace().getAxis(), burst);
    }

    @Override
    public void playSound(SoundCue cue) {
        SoundPlays.play(level, Vec3.atCenterOf(pos), cue);
    }


    @Override
    public void placeBlock(Identifier block, Map<String, String> state) {
        Block found = BuiltInRegistries.BLOCK.getOptional(block)
                .orElseThrow(() -> new IllegalArgumentException(ERR_UNKNOWN_BLOCK + block));
        List<Property.Value<?>> values = StatePropertyWriter.resolve(found.getStateDefinition(), state, block);
        level.setBlock(pos, StatePropertyWriter.write(found.defaultBlockState(), values), Block.UPDATE_ALL);
    }

    @Override
    public void writeOwnState(Map<String, String> state) {
        BlockState standing = level.getBlockState(pos);
        List<Property.Value<?>> values = StatePropertyWriter.resolve(standing.getBlock().getStateDefinition(), state,
                BuiltInRegistries.BLOCK.getKey(standing.getBlock()));
        level.setBlock(pos, StatePropertyWriter.write(standing, values), Block.UPDATE_ALL);
    }
}
