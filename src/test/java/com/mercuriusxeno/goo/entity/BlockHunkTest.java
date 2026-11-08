package com.mercuriusxeno.goo.entity;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A rising hunk blocks movement, so nothing falls through Spire's stone
 * mid-rise, while a lifted hunk collides with nothing
 * (decision spire-carries-its-riders-up-with-the-rising-stone), and a hunk's
 * block picks its texture's variant by its home cell, not the cell it moves
 * through (decision spire-entity-izes-its-blocks-and-raises-them-smoothly);
 * a placed hunk clicks with one to three of its own blocks' placement sounds
 * (decision placed-hunk-clicks-with-its-blocks-placement-sounds).
 */
class BlockHunkTest {

    /** The pitch step between the click's superimposed sounds. */
    private static final float PITCH_STEP = 0.1f;
    /** Vanilla's sound types of the blocks a hunk carries, as each block's own properties name them. */
    private static final SoundType STONE = SoundType.STONE;
    private static final SoundType DIRT = SoundType.GRAVEL;
    private static final SoundType GRAVEL = SoundType.GRAVEL;
    private static final SoundType OAK_PLANKS = SoundType.WOOD;
    private static final SoundType SAND = SoundType.SAND;

    /**
     * Stands the built-in registries, so vanilla's blocks and their sound
     * types are at hand: their class inits ask FML whether it runs in
     * production, which a stubbed loader answers.
     *
     * @throws ClassNotFoundException never, the class is on the test classpath
     */
    @BeforeAll
    static void standRegistries() throws ClassNotFoundException {
        try (MockedStatic<FMLLoader> loader = mockStatic(FMLLoader.class, RETURNS_DEEP_STUBS)) {
            Class.forName(AttachmentHolder.class.getName(), true, AttachmentHolder.class.getClassLoader());
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
        }
    }

    @Test
    void anAllStoneHunkClicksWithStonesOnePlacementSound() {
        List<BlockHunk.PlaceClick> clicks = BlockHunk.placeClicks(List.of(STONE, STONE, STONE));
        assertEquals(List.of(new BlockHunk.PlaceClick(STONE.getPlaceSound(), STONE.getVolume(), STONE.getPitch())),
                clicks);
    }

    @Test
    void dirtAndGravelShareGravelsSoundTypeSoStoneDirtAndGravelClickTwice() {
        List<BlockHunk.PlaceClick> clicks = BlockHunk.placeClicks(List.of(STONE, DIRT, GRAVEL, STONE));
        assertEquals(List.of(STONE.getPlaceSound(), SoundType.GRAVEL.getPlaceSound()),
                clicks.stream().map(BlockHunk.PlaceClick::sound).toList());
    }

    @Test
    void threeDistinctSoundTypesClickInBlockOrderAtPitchesATenthApart() {
        List<BlockHunk.PlaceClick> clicks = BlockHunk.placeClicks(List.of(STONE, DIRT, OAK_PLANKS, SAND));
        assertEquals(List.of(STONE.getPlaceSound(), DIRT.getPlaceSound(), OAK_PLANKS.getPlaceSound()),
                clicks.stream().map(BlockHunk.PlaceClick::sound).toList());
        assertEquals(STONE.getPitch(), clicks.get(0).pitch(), 1e-6);
        assertEquals(DIRT.getPitch() * (1 - PITCH_STEP), clicks.get(1).pitch(), 1e-6);
        assertEquals(OAK_PLANKS.getPitch() * (1 + PITCH_STEP), clicks.get(2).pitch(), 1e-6);
        assertEquals(OAK_PLANKS.getVolume(), clicks.get(2).volume(), 1e-6);
    }

    @Test
    void aRisingHunkBlocksMovementAndALiftedOneDoesNot() {
        assertTrue(BlockHunk.blocksMovement(true));
        assertFalse(BlockHunk.blocksMovement(false));
    }

    // terrakinesis-release-drops-the-hunk-and-places-it-on-the-grid: a let-go hunk lands whole, a thrown one breaks
    @Test
    void aReleasedHunkAtRestIsPlacedAndAThrownOneStrikes() {
        assertEquals(BlockHunk.Landing.PLACE, BlockHunk.landingOf(false, false, true));
        assertEquals(BlockHunk.Landing.STRIKE, BlockHunk.landingOf(true, false, true));
        assertEquals(BlockHunk.Landing.STRIKE, BlockHunk.landingOf(true, true, false));
        assertEquals(BlockHunk.Landing.FLY, BlockHunk.landingOf(false, true, false));
        assertEquals(BlockHunk.Landing.FLY, BlockHunk.landingOf(true, false, false));
    }

    @Test
    void aBlocksTextureVariantSeedsFromItsHomeCellWhereverTheHunkCarriesIt() {
        BlockPos home = new BlockPos(10, 64, -3);
        BlockHunk.HunkBlock block = new BlockHunk.HunkBlock(new BlockPos(1, 2, -1), mock(BlockState.class));
        assertEquals(new BlockPos(11, 66, -4), BlockHunk.variantCellOf(home, block));
    }

    @Test
    void aSettledHunkFadesOutOverItsStoneFromTheMomentItSettles() {
        // A pillar settled at tick 100 draws faded over the world's stone, not as moving blocks over it.
        assertEquals(0, BlockHunk.lingerShare(100, 100), 1e-9);
        assertEquals(0.5, BlockHunk.lingerShare(100 + BlockHunk.SETTLE_LINGER_TICKS / 2.0, 100), 1e-9);
        assertEquals(1, BlockHunk.lingerShare(100 + BlockHunk.SETTLE_LINGER_TICKS, 100), 1e-9);
    }

    @Test
    void aHunkNotYetSettledDrawsNoFade() {
        assertEquals(BlockHunk.NOT_PLACED, BlockHunk.lingerShare(99.9, 100), 1e-9);
    }
}
