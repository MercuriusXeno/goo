package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.pulse.RedstoneBeat;
import com.mercuriusxeno.goo.ability.pulse.RelayNetwork;
import com.mercuriusxeno.goo.block.ability.MarkerAnchor;
import com.mercuriusxeno.goo.block.ability.PrismBlockEntity;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
        FieldEffectHost, PhasedHost, ConsumedGooHost, StateWriteHost, LevelHost, ConvokeHost, PowerEmitHost,
        BeatHost, RelayHost, AgitateHost, FrostHost, GreeningHost {

    private static final String ERR_UNKNOWN_BLOCK = "No block is registered as ";
    /** The power a block gives at full strength. */
    private static final int FULL_POWER = 15;

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

    /**
     * The marker cell's center, where a verdant prism's greening reaches out from.
     * verdant-prism-greens-blocks-slowly
     */
    @Override
    public Vec3 center() {
        return Vec3.atCenterOf(pos);
    }

    @Override
    public AgitationState agitation() {
        return be.programState().agitation();
    }

    @Override
    public Direction placedFace() {
        return be.getPlacedFace();
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

    /**
     * Gives full power or none (decision thumper-blob-pulses-periodically-then-fades).
     */
    @Override
    public void emitPower(boolean on) {
        setPowerLevel(on ? FULL_POWER : 0);
    }

    /**
     * Sets the power the marker block gives, so the blocks beside it read it
     * through its signal: an ability block's powered state, on for any power,
     * or a prism's power level (decision relay-prism-carries-the-signal-through-air).
     *
     * @param power the power, 0 to 15
     */
    private void setPowerLevel(int power) {
        BlockState state = level.getBlockState(pos);
        BlockState after = state;
        if (state.hasProperty(BlockStateProperties.POWERED)) {
            after = state.setValue(BlockStateProperties.POWERED, power > 0);
        } else if (state.hasProperty(BlockStateProperties.POWER)) {
            after = state.setValue(BlockStateProperties.POWER, power);
        }
        if (after != state) {
            level.setBlock(pos, after, Block.UPDATE_ALL);
        }
    }

    /**
     * Gives the strongest signal reaching any relay the prism links to
     * through air (decision relay-prism-carries-the-signal-through-air).
     */
    @Override
    public void carrySignal() {
        if (be instanceof PrismBlockEntity prism) {
            setPowerLevel(RelayNetwork.carriedTo(level, pos, prism));
        }
    }

    /**
     * The beat a prism has heard; an ability block hears none
     * (decision metronome-prism-pulses-at-the-learned-rate).
     */
    @Override
    public RedstoneBeat beat() {
        return be instanceof PrismBlockEntity prism ? prism.beat() : RedstoneBeat.SILENT;
    }

    @Override
    public long gameTime() {
        return level.getGameTime();
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

    /**
     * Pulls a mob from the marker's chunk to stand in the marker's cell.
     * decision convoke-blob-throbs-until-a-mob-arrives
     */
    @Override
    public boolean convokeFromChunk() {
        return ChunkConvoke.convoke(level, Vec3.atBottomCenterOf(pos));
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

    /** The prism or marker's center, which a glacial prism holds frozen around (decision glacial-prism-holds-the-area-frozen). */
    @Override
    public Vec3 frostCenter() {
        return Vec3.atCenterOf(pos);
    }
}
