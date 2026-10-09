package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.block.ability.MarkerAnchor;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.network.ChainBurnoutPayload;
import com.mercuriusxeno.goo.registry.GooParticles;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The {@link StepHost} over a marker anchor, the ability block a lingering
 * ability stands or the prism a combo runs on: reads the placed
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
        FieldEffectHost, PhasedHost, ConsumedGooHost, TickBankHost {

    private static final String ERR_UNKNOWN_BLOCK = "No block is registered as ";

    @Override
    public HostKind kind() {
        return HostKind.MARKER;
    }

    @Override
    public OptionalDouble read(String name) {
        return OptionalDouble.empty();
    }

    @Override
    public BlockPos position() {
        return pos;
    }

    @Override
    public Direction placedFace() {
        return be.getPlacedFace();
    }

    @Override
    public void bankTicks(int perTick, int spending) {
        be.bankTicks(perTick, spending);
    }

    /**
     * Explodes at the marker with Goo's explosion and vanilla's boom, its
     * particles swapped for none, since the marker's burnout explosion is
     * drawn by its goo type (decision elemental-explosion-per-type), sent
     * here as it explodes rather than when the blob landed.
     */
    @Override
    public void explode(float power, ExplosionMode mode) {
        burnout().sendToTracking(level);
        GooExplosion.detonate(level, Vec3.atCenterOf(pos), power, mode,
                GooExplosion.Look.silent(GooParticles.SILENT_BLAST.get()));
    }

    /**
     * The burnout this marker plays when it explodes, drawn by its goo type.
     *
     * @return the burnout at the marker
     */
    ChainBurnoutPayload burnout() {
        return new ChainBurnoutPayload(pos, be.getPlacedFace().ordinal(), GooTypes.id(be.getGooType()),
                be.getAbilityId());
    }

    @Override
    public boolean anyEntityWithin(SelectionShape shape, double radius, Set<EntityFilter> filters) {
        return EntityScan.anyEntityWithin(level, Vec3.atCenterOf(pos), shape, radius, filters, null);
    }

    @Override
    public void forEachEntityWithin(SelectionShape shape, double radius, Set<EntityFilter> filters,
                                    Consumer<TargetHost> body) {
        BlockAnchoredActions.forEachEntityWithin(level, Vec3.atCenterOf(pos), shape, radius, filters, body);
    }

    @Override
    public void forEntity(int entityId, Consumer<TargetHost> body) {
        BlockAnchoredActions.forEntity(level, entityId, body);
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
    public PhasedState phased() {
        return be.programState().phased();
    }

    @Override
    public void pullEntitiesWithin(double radius, double speed) {
        EntityPull.pullWithin(level, Vec3.atCenterOf(pos), radius, speed, null);
    }

    @Override
    public void consumeValuedBlocks(int radius) {
        be.programState().addConsumedGoo(ValuedBlocks.consumeSphere(level, pos, radius));
    }

    @Override
    public void dropConsumedGoo() {
        GooStacks.dropAll(be.programState().takeConsumedGoo(), level, pos);
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
}
