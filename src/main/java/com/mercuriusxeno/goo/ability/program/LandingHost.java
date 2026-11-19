package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.hole.HoleOpening;
import com.mercuriusxeno.goo.ability.pulse.ZapDevice;
import com.mercuriusxeno.goo.ability.quantum.Portal;
import com.mercuriusxeno.goo.ability.quantum.PortalColor;
import com.mercuriusxeno.goo.ability.quantum.PortalPairs;
import com.mercuriusxeno.goo.ability.quantum.PortalShots;
import com.mercuriusxeno.goo.ability.quantum.Wormhole;
import com.mercuriusxeno.goo.block.ability.AbilityBlock;
import com.mercuriusxeno.goo.block.ability.AbilityBlockEntity;
import com.mercuriusxeno.goo.block.ability.PrismBlock;
import com.mercuriusxeno.goo.entity.Meteor;
import com.mercuriusxeno.goo.network.BlockVisuals;
import com.mercuriusxeno.goo.network.TransformationPayload;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

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
 * @param size        the size the cast was dragged to, zero for one naming none
 *                    (decision black-hole-leaves-a-compression-sphere)
 * @param caster      the player whose goo landed, or null for none
 *                    (decision void-opens-a-pocket-dimension)
 */
public record LandingHost(ServerLevel level, BlockPos cell, Direction face, boolean waterlogged,
                          ResourceKey<GooTypeDefinition> gooType, String abilityId, Vec3 anchor, double size,
                          @Nullable UUID caster)
        implements PlacedFaceHost, ExplodeHost, AnchoredWorldHost, PlaceBlockHost, LingerHost, BlockBreakHost,
        FloorScanHost, PowerPulseHost, MobSpawnHost, FrostHost, MeteorHost,
        WormholeHost, PortalHost, IgniteHost, HoleHost {

    private static final String ERR_UNKNOWN_BLOCK = "No block is registered as ";

    @Override
    public Direction.Axis burstAxis() {
        return face.getAxis();
    }

    @Override
    public HostKind kind() {
        return HostKind.LANDING;
    }

    /** The point the throw landed at, where a landing nova spreads from (decision orb-carries-a-swirling-nova). */
    @Override
    public Vec3 frostCenter() {
        return anchor;
    }

    @Override
    public BlockPos spawnCell() {
        return cell;
    }

    @Override
    public Vec3 morphFrom() {
        return anchor;
    }

    @Override
    public OptionalDouble read(String name) {
        return HostVariables.SIZE.equals(name) ? OptionalDouble.of(size) : OptionalDouble.empty();
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
     * Opens a wormhole from the struck face, the block behind the cell, to the
     * first opening ahead; with no caster about, or no opening, the face shows
     * the hole collapse.
     * hole-closes-the-space-to-an-opening-ahead
     */
    @Override
    public boolean openHole(int reach, int lifetime) {
        BlockPos struck = cell.relative(face.getOpposite());
        ServerPlayer player = caster == null ? null : level.getServer().getPlayerList().getPlayer(caster);
        if (player == null || player.level() != level) {
            Wormhole.collapse(level, struck, face);
            return false;
        }
        return Wormhole.open(level, player, struck, face, reach, lifetime);
    }

    /**
     * Opens the caster's portable hole on the struck face, the block behind
     * the cell; with no caster in this level the face shows the hole refused.
     * decision hole-is-an-in-world-portable-hole-for-items
     */
    @Override
    public boolean openPortableHole(int lifetime, int refresh) {
        BlockPos struck = cell.relative(face.getOpposite());
        ServerPlayer player = caster == null ? null : level.getServer().getPlayerList().getPlayer(caster);
        if (player == null || player.level() != level || !HoleOpening.takes(face)) {
            HoleOpening.refuse(level, struck, face);
            return false;
        }
        HoleOpening.open(level, player, struck, face, lifetime, refresh);
        return true;
    }

    /**
     * Places a portal frame on the struck face, the block behind the cell, for
     * the caster: two tall on a wall, flat along the caster's facing on a
     * floor or ceiling. A face with no room for the frame places none.
     * portals-link-by-looking-through-portal-vision
     */
    @Override
    public boolean placePortal() {
        ServerPlayer player = caster == null ? null : level.getServer().getPlayerList().getPlayer(caster);
        Optional<PortalColor> color = PortalShots.colorOf(caster);
        if (player == null || color.isEmpty()) {
            return false;
        }
        Optional<Portal> frame = Portal.frameOn(level, cell.relative(face.getOpposite()), face, player.getDirection(),
                color.get());
        frame.ifPresent(portal -> PortalPairs.place(level, caster, portal));
        return frame.isPresent();
    }

    /**
     * Calls a meteor down toward the cell's center, crashing into whatever
     * stands above it.
     * decision meteo-crashes-through-whatever-is-above
     */
    @Override
    public void callMeteor(float power, int fallTicks) {
        Meteor.call(level, Vec3.atCenterOf(cell), power, fallTicks);
    }

    /**
     * Explodes at the anchor with Goo's explosion in the look the step
     * wrote, beside the landing's burnout its goo type draws (decision
     * elemental-explosion-per-type), each hit naming the caster where the
     * caster stands on the server.
     * direct-goo-hit-credits-its-caster
     * goo-hit-keeps-its-kind-and-names-the-player-behind-it
     */
    @Override
    public void explode(float power, double reach, boolean gathers, ExplosionMode mode, ExplosionLook look) {
        ServerPlayer player = caster == null ? null : level.getServer().getPlayerList().getPlayer(caster);
        GooExplosion.detonate(level, anchor(), power, new GooExplosion.Cut(reach, gathers), mode, look.shown(),
                player);
    }

    @Override
    public void lightFire() {
        IgniteLanding.lightFire(level, cell, face);
    }

    @Override
    public boolean detonateExplosive(float power) {
        return IgniteLanding.detonateExplosive(level, cell, face, power);
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
                    Vec3.atCenterOf(cell), cell, SlimeTransmuteStep.DEFAULT_MORPH_TICKS));
        }
        level.setBlock(cell, StatePropertyWriter.write(found.defaultBlockState(), values), Block.UPDATE_ALL);
    }

    @Override
    public void linger(List<Step> steps) {
        level.setBlock(cell, GooBlocks.ABILITY_BLOCK.get().defaultBlockState()
                .setValue(AbilityBlock.WATERLOGGED, waterlogged), Block.UPDATE_ALL);
        if (level.getBlockEntity(cell) instanceof AbilityBlockEntity be) {
            be.stand(gooType, face, abilityId, steps, size, caster);
        }
    }

    /**
     * Ticks the block the blob landed on where it is a redstone device
     * (decision zap-ticks-the-device-and-stuns).
     */
    @Override
    public void powerPulse() {
        BlockPos landedOn = ZapDevice.landedOn(level, cell, face);
        if (ZapDevice.ticks(level.getBlockState(landedOn))) {
            ZapDevice.pulse(level, landedOn, cell);
        }
    }

    /**
     * Toggles each hand device the wave crosses behind the landed-on block
     * once, a door's two halves counting as one, where that block is no
     * redstone device (decision zap-disperses-into-signal).
     */
    @Override
    public void signalWave(double range, double coneDegrees) {
        if (ZapDevice.ticks(level.getBlockState(ZapDevice.landedOn(level, cell, face)))) {
            return;
        }
        SignalWaveStep.cellsBehind(cell, face, range, coneDegrees).stream()
                .map(pos -> ZapDevice.handDevice(level, pos)).flatMap(Optional::stream).distinct()
                .forEach(device -> ZapDevice.toggleByHand(level, device));
    }
}
