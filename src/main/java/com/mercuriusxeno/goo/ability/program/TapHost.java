package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * The {@link StepHost} over the block a tap's drip lands on: world actions
 * anchor at the struck face's center, and a placed block goes into the
 * block beyond that face. A tap has no will and no target, and a drip lands
 * in one tick with nothing ticking it afterwards, so this host implements
 * neither {@link TargetHost} nor {@link TickingHost}
 * (decision tap-ability-tagged-program).
 *
 * @param level   the server level
 * @param landing the block the drip landed on
 * @param face    the landing block's face the drip struck
 */
public record TapHost(ServerLevel level, BlockPos landing, Direction face)
        implements ExplodeHost, AnchoredWorldHost, PlaceBlockHost {

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
    public void explode(float power, ExplosionMode mode) {
        GooExplosion.detonate(level, anchor(), power, mode, GooExplosion.Look.vanilla());
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
