package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Every world goo's cell is goo and sounds as goo: the slime block's steps,
 * placing and breaking, which Vacuum's reclaim plays as it breaks a cell.
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 */
@RegistersGameTests
public final class WorldGooSoundTests {

    private static final String EVERY_CELL_SOUNDS_AS_GOO = "every_world_goo_cell_sounds_as_goo";
    private static final String SHOULD_SOUND_AS_GOO = "%s should sound as slime goo";

    private WorldGooSoundTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, EVERY_CELL_SOUNDS_AS_GOO, WorldGooSoundTests::everyWorldGooCellSoundsAsGoo);
    }

    /**
     * Goop's, Slick's, Molten's, Shatter's, Creep's, Rime's, Wisps', Leech's, Updraft's, Nectar's, Signal's,
     * Spikes', Infest's and Vines' cells each sound as the slime block.
     *
     * @param helper the gametest helper
     */
    public static void everyWorldGooCellSoundsAsGoo(GameTestHelper helper) {
        BlockPos anywhere = helper.absolutePos(BlockPos.ZERO);
        for (Block cell : List.of(GooBlocks.SLIME_COAT.get(), GooBlocks.KINETIC_SLICK.get(),
                GooBlocks.BLAZE_MOLTEN.get(), GooBlocks.CRYSTAL_SHATTER.get(), GooBlocks.SCULK_CREEP.get(),
                GooBlocks.FROST_RIME.get(), GooBlocks.GLOW_WISPS.get(), GooBlocks.HEX_LEECH.get(),
                GooBlocks.TYPHOON_UPDRAFT.get(), GooBlocks.QUANTUM_PHASE.get(), GooBlocks.JELLY_NECTAR.get(),
                GooBlocks.PULSE_SIGNAL.get(), GooBlocks.METAL_SPIKES.get(), GooBlocks.ZOMBIE_INFEST.get(),
                GooBlocks.LEAF_VINES.get(),
                GooBlocks.NETHER_GLOOM.get(),
                GooBlocks.ROCK_MORTAR.get(), GooBlocks.YARN_STITCH.get())) {
            SoundType sound = cell.defaultBlockState().getSoundType(helper.getLevel(), anywhere, null);
            helper.assertTrue(sound == SoundType.SLIME_BLOCK, String.format(SHOULD_SOUND_AS_GOO, cell));
        }
        helper.succeed();
    }
}
