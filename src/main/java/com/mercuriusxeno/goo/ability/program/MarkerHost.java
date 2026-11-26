package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.GooHitCredit;
import com.mercuriusxeno.goo.ability.pulse.RedstoneBeat;
import com.mercuriusxeno.goo.ability.pulse.RelayNetwork;
import com.mercuriusxeno.goo.block.ability.MarkerAnchor;
import com.mercuriusxeno.goo.block.ability.PrismBlockEntity;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The {@link StepHost} over a marker anchor, the prism a combo runs on:
 * reads the placed
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
        FieldEffectHost, StateWriteHost, LevelHost, PowerEmitHost,
        BeatHost, RelayHost, FrostHost, GreeningHost {

    private static final String ERR_UNKNOWN_BLOCK = "No block is registered as ";
    /** The power a block gives at full strength. */
    private static final int FULL_POWER = 15;

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

    /**
     * The marker cell's center, where a verdant prism's greening reaches out from.
     * verdant-prism-greens-blocks-slowly
     */
    @Override
    public Vec3 center() {
        return Vec3.atCenterOf(pos);
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

    /**
     * Gives full power or none (decision metronome-prism-pulses-at-the-learned-rate).
     */
    @Override
    public void emitPower(boolean on) {
        setPowerLevel(on ? FULL_POWER : 0);
    }

    /**
     * Sets the power the marker block gives, so the blocks beside it read it
     * through the prism's power level (decision relay-prism-carries-the-signal-through-air).
     *
     * @param power the power, 0 to 15
     */
    private void setPowerLevel(int power) {
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(BlockStateProperties.POWER) && state.getValue(BlockStateProperties.POWER) != power) {
            level.setBlock(pos, state.setValue(BlockStateProperties.POWER, power), Block.UPDATE_ALL);
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
     * The beat a prism has heard
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

    /** The prism or marker's center, which a glacial prism holds frozen around (decision glacial-prism-holds-the-area-frozen). */
    @Override
    public Vec3 frostCenter() {
        return Vec3.atCenterOf(pos);
    }
}
