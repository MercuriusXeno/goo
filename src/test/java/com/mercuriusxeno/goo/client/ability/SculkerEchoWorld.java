package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.HeldSweepWorld;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

/**
 * The fixed synthetic world every Sculker echo count and timing reads: the
 * held sweeps' stone cut by a lattice of tunnels three blocks wide every
 * twelve blocks ({@link HeldSweepWorld}), every stone block lighting where
 * an echo reaches it and every tunnel block passing sound, with sculk lining
 * every third block of every tunnel floor, as the deep dark lines its caves,
 * so the shroom scan finds sculk in nearly every section within its reach.
 * Every block's model is one quad a side. The world is the assumption every
 * count stands on: a real cave holds fewer walls and less sculk.
 * decision diagnose-and-correct-the-lag-on-four-sense-abilities
 */
final class SculkerEchoWorld implements SculkerEchoRenderer.EchoWorld, ShroomVision.ShroomWorld {

    private static final int SCULK_EVERY = 3;

    private final BlockState sculk = mock(BlockState.class, withSettings().stubOnly());
    private final BlockState stone = mock(BlockState.class, withSettings().stubOnly());
    private final List<BlockStateModelPart> cube = List.of(new CubePart(List.of(mock(BakedQuad.class))));

    /**
     * @param pos a block
     * @return true for stone, which an echo lights where it reaches it
     */
    static boolean lights(BlockPos pos) {
        return !HeldSweepWorld.isAir(pos);
    }

    /**
     * @param pos a block
     * @return true for a tunnel block, which sound passes through
     */
    static boolean passesSoundAt(BlockPos pos) {
        return HeldSweepWorld.isAir(pos);
    }

    /**
     * @param pos a block
     * @return true where sculk lines a tunnel floor
     */
    static boolean isSculk(BlockPos pos) {
        return !HeldSweepWorld.isAir(pos) && HeldSweepWorld.isAir(pos.above())
                && Math.floorMod(pos.getX() + pos.getZ(), SCULK_EVERY) == 0;
    }

    /**
     * @param state a block
     * @return true for sculk, which belongs to the shroom network
     */
    boolean inNetwork(BlockState state) {
        return state == sculk;
    }

    @Override
    public BlockState stateAt(BlockPos pos) {
        return isSculk(pos) ? sculk : stone;
    }

    @Override
    public void collectParts(BlockPos pos, BlockState state, List<BlockStateModelPart> parts) {
        parts.addAll(cube);
    }

    @Override
    public boolean passesSound(BlockPos pos) {
        return passesSoundAt(pos);
    }

    @Override
    public ShroomVision.FungusSection fungusSection(int sectionX, int sectionY, int sectionZ) {
        BlockPos base = SectionPos.of(sectionX, sectionY, sectionZ).origin();
        return (x, y, z) -> isSculk(base.offset(x, y, z));
    }
}
