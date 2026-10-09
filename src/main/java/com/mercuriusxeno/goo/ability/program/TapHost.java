package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.pulse.ZapDevice;
import com.mercuriusxeno.goo.registry.GooServerState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The {@link StepHost} over the block a tap's drip lands on: world actions
 * anchor at the struck face's center, and a placed block goes into the
 * block beyond that face. A tap has no will and no target, and a drip lands
 * in one tick with nothing ticking it afterwards, so this host implements
 * neither {@link TargetHost} nor {@link TickingHost}
 * (decision tap-ability-tagged-program). It scans the entities around the
 * struck face, so a drip acts on what stands where it lands.
 * vitality-drip-heals-below
 *
 * @param level  the server level
 * @param landing the block the drip landed on
 * @param face    the landing block's face the drip struck
 */
public record TapHost(ServerLevel level, BlockPos landing, Direction face)
        implements ExplodeHost, AnchoredWorldHost, PlaceBlockHost, EntityScanHost, DripHost, DeviceToggleHost {

    private static final double HALF = 0.5;

    /**
     * The center of a block's face, where a tap host anchors its world actions.
     *
     * @param landing the block
     * @param face    the face
     * @return the face center
     */
    public static Vec3 faceCenter(BlockPos landing, Direction face) {
        return Vec3.atCenterOf(landing).add(face.getStepX() * HALF, face.getStepY() * HALF, face.getStepZ() * HALF);
    }

    @Override
    public Vec3 anchor() {
        return faceCenter(landing, face);
    }

    @Override
    public Direction.Axis burstAxis() {
        return face.getAxis();
    }

    @Override
    public HostKind kind() {
        return HostKind.TAP;
    }

    @Override
    public OptionalDouble read(String name) {
        return OptionalDouble.empty();
    }

    @Override
    public BlockPos position() {
        return landing;
    }

    @Override
    public int countDrip() {
        return GooServerState.of(level.getServer()).tapDripCounts().countDrip(level.dimension(), landing);
    }

    @Override
    public void resetDrips() {
        GooServerState.of(level.getServer()).tapDripCounts().reset(level.dimension(), landing);
    }

    /**
     * Grows a pointed dripstone tip under the landing block, or under the
     * stalactite already hanging from it, when that cell stands open; the
     * tip it extends thickens through its own shape update.
     */
    @Override
    public void growStalactite() {
        BlockPos below = landing.below();
        while (level.getBlockState(below).is(Blocks.POINTED_DRIPSTONE)) {
            below = below.below();
        }
        if (level.getBlockState(below).isAir()) {
            level.setBlock(below, Blocks.POINTED_DRIPSTONE.defaultBlockState()
                    .setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN), Block.UPDATE_ALL);
        }
    }

    /**
     * Toggles the device below the tap: the landing block when it is one, a
     * closed trapdoor or door the drip struck, else the device standing on
     * the struck face, a lever or button the drip fell through
     * (decision pulser-drip-toggles-the-block-below).
     */
    @Override
    public void toggleDevice() {
        ZapDevice.handDevice(level, landing)
                .or(() -> ZapDevice.handDevice(level, landing.relative(face)))
                .ifPresent(device -> ZapDevice.toggleByHand(level, device));
    }

    @Override
    public void explode(float power, ExplosionMode mode) {
        GooExplosion.detonate(level, anchor(), power, mode, GooExplosion.Look.vanilla());
    }

    @Override
    public boolean anyEntityWithin(SelectionShape shape, double radius, Set<EntityFilter> filters) {
        return EntityScan.anyEntityWithin(level, anchor(), shape, radius, filters, null);
    }

    @Override
    public void forEachEntityWithin(SelectionShape shape, double radius, Set<EntityFilter> filters,
                                    Consumer<TargetHost> body) {
        BlockAnchoredActions.forEachEntityWithin(level, anchor(), shape, radius, filters, body);
    }

    @Override
    public void forEntity(int entityId, Consumer<TargetHost> body) {
        BlockAnchoredActions.forEntity(level, entityId, body);
    }

    @Override
    public void pullEntitiesWithin(double radius, double speed) {
        EntityPull.pullWithin(level, anchor(), radius, speed, null);
    }

    /**
     * Writes the block into the cell beyond the struck face when that cell
     * can be replaced, so a drip never overwrites the tap it fell from or
     * any other standing block.
     */
    @Override
    public void placeBlock(Identifier block, Map<String, String> state) {
        BlockAnchoredActions.placeBeyondFace(level, landing, face, block, state);
    }

}
