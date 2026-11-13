package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooValueTable;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * Neighbouring slick films read as one film, a film hiding its side face
 * toward another film as goop's coat does, asked through the block state as
 * the renderer asks it (decision diagnose-and-correct-the-slick-film-seams),
 * and a broken film gives back the goo it cost.
 */
class KineticSlickBlockTest {

    @Nested
    class Faces {

        private final BlockState film = stateOf(mock(KineticSlickBlock.class, CALLS_REAL_METHODS));

        @Test
        void aFilmHidesItsSideFaceTowardAnotherFilm() {
            BlockState neighbour = stateOf(mock(KineticSlickBlock.class));

            assertTrue(film.skipRendering(neighbour, Direction.NORTH));
        }

        @Test
        void aFilmDrawsItsSideFaceTowardAnythingElse() {
            BlockState stone = stateOf(mock(Block.class));

            assertFalse(film.skipRendering(stone, Direction.NORTH));
        }

        private static BlockState stateOf(Block block) {
            BlockState state = mock(BlockState.class, CALLS_REAL_METHODS);
            doReturn(block).when(state).getBlock();
            return state;
        }
    }

    /**
     * A broken film gives back kinetic goo worth one film, the 12 mb a goop
     * layer gives back as weird goo
     * (decision breaking-slick-drops-kinetic-goo-worth-its-film).
     */
    @Nested
    class Reclaim {

        @Test
        void aBrokenFilmReclaimsKineticGoo() {
            assertEquals(GooTypes.KINETIC, KineticSlickBlock.reclaimed().type());
        }

        @Test
        void aBrokenFilmReclaimsTwelveMb() {
            assertEquals(12, KineticSlickBlock.reclaimed().mb());
        }

        /** slick-drains-per-film-it-lays: one film's price is what a broken film gives back */
        @Test
        void aBrokenFilmReclaimsWhatSlicksCostChargesAFilm() {
            GooFraction filmPrice = AbilityJson.decode("kinetic_slick").cost().exactIn(GooValueTable.EMPTY);

            assertEquals(0, GooFraction.of(KineticSlickBlock.reclaimed().mb()).compareTo(filmPrice),
                    "a film costs " + filmPrice + " and reclaims " + KineticSlickBlock.reclaimed().mb());
        }
    }
}
