package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
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
 *
 * @param level   the server level
 * @param tapPos  the tap the drip fell from
 * @param landing the block the drip landed on
 * @param face    the landing block's face the drip struck
 */
public record TapHost(ServerLevel level, BlockPos tapPos, BlockPos landing, Direction face)
        implements ExplodeHost, AnchoredWorldHost, PlaceBlockHost, EntityScanHost, ConvokeHost, FrostHost {

    private static final double HALF = 0.5;

    /**
     * A tap host whose tap stands right beyond the struck face, as a tap
     * dripping straight onto the block below it does.
     *
     * @param level   the server level
     * @param landing the block the drip landed on
     * @param face    the landing block's face the drip struck
     */
    public TapHost(ServerLevel level, BlockPos landing, Direction face) {
        this(level, landing.relative(face), landing, face);
    }

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
    public Vec3 frostCenter() {
        return anchor();
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
    public long gameTime() {
        return level.getGameTime();
    }

    /**
     * Pulls a mob from the landing's chunk to stand in the cell beyond the
     * struck face, under the tap.
     * decision convoke-drip-rolls-a-small-chance
     */
    @Override
    public boolean convokeFromChunk(ResourceKey<GooTypeDefinition> goo) {
        return ChunkConvoke.convoke(level, Vec3.atBottomCenterOf(landing.relative(face)), goo);
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

    @Override
    public void liftEntitiesInColumn(double radius, double height, double speed) {
        EntityLift.liftInColumn(level, anchor(), radius, height, speed);
    }

    @Override
    public void rideShaftAbove(int cap, double rise, double sink) {
        EntityLift.rideShaft(level, BlockPos.containing(anchor()).above(), cap, rise, sink);
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
