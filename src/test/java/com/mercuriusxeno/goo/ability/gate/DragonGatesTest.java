package com.mercuriusxeno.goo.ability.gate;

import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mockStatic;

/**
 * Where a Dragon Gate's square lies, where it sets travellers down, and how a
 * server's open pairs are kept and closed on their clocks
 * (decision dragon-gate-banishes-blocks-and-opens-a-portal).
 */
class DragonGatesTest {

    private static final BlockPos CENTER = new BlockPos(10, 64, -5);
    private static final int CELLS = 9;
    /** The square's area, two by two blocks. */
    private static final double SQUARE_AREA = 4;
    private static final double EPSILON = 1e-9;
    private static final long CLOSES_AT = 1200L;

    @BeforeAll
    static void standRegistries() {
        try (MockedStatic<FMLLoader> loader = mockStatic(FMLLoader.class, RETURNS_DEEP_STUBS)) {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
        }
    }

    private static GatePatch patchAt(ResourceKey<Level> level, BlockPos center) {
        List<GatePatch.Covered> covered = GateSquare.cells(center, Direction.UP).stream()
                .map(cell -> new GatePatch.Covered(cell.pos(), Blocks.AIR.defaultBlockState())).toList();
        return new GatePatch(level, center, Direction.UP, covered);
    }

    @Nested
    class Square {

        @ParameterizedTest
        @EnumSource(Direction.class)
        void liesInNineOpenCellsInFrontOfTheStruckFace(Direction face) {
            List<GateSquare.Cell> cells = GateSquare.cells(CENTER, face);
            assertEquals(CELLS, new HashSet<>(cells.stream().map(GateSquare.Cell::pos).toList()).size());
            assertTrue(cells.stream().allMatch(cell -> cell.pos().get(face.getAxis())
                    == CENTER.relative(face).get(face.getAxis())));
            assertEquals(CENTER.relative(face), cells.getFirst().pos());
        }

        @ParameterizedTest
        @EnumSource(Direction.class)
        void spansTwoByTwoCentredOnTheStruckBlock(Direction face) {
            double area = GateSquare.cells(CENTER, face).stream()
                    .map(cell -> GateSquare.cellLayer(face, cell.across(), cell.along()))
                    .mapToDouble(layer -> layer.getXsize() * layer.getYsize() * layer.getZsize() / GateSquare.DEPTH)
                    .sum();
            assertEquals(SQUARE_AREA, area, EPSILON);
        }

        @Test
        void aCornerCellHoldsTheQuarterNearestTheMiddleAgainstTheFace() {
            AABB corner = GateSquare.cellLayer(Direction.UP, 0, 2);
            assertEquals(new AABB(0.5, 0, 0, 1, GateSquare.DEPTH, 0.5), corner);
        }

        @ParameterizedTest
        @EnumSource(Direction.class)
        void setsTravellersDownOffTheSquareOnItsOpenSide(Direction face) {
            Vec3 feet = GateSquare.arrival(CENTER, face);
            double side = (feet.get(face.getAxis()) - (CENTER.get(face.getAxis()) + 0.5)) * face.getAxisDirection().getStep();
            assertTrue(side > 0, face + " sets travellers down behind its own surface");
        }
    }

    @Nested
    class Pairs {

        @Test
        void eachGateCarriesTravellersToTheOther() {
            DragonGates gates = new DragonGates();
            GatePatch near = patchAt(Level.OVERWORLD, CENTER);
            GatePatch far = patchAt(Level.END, new BlockPos(100, 48, 0));
            gates.open(new DragonGates.Pair(near, far, CLOSES_AT));
            assertEquals(Optional.of(far), gates.partnerOf(Level.OVERWORLD, CENTER.above().east()));
            assertEquals(Optional.of(near), gates.partnerOf(Level.END, new BlockPos(100, 49, 1)));
            assertEquals(Optional.empty(), gates.partnerOf(Level.END, CENTER));
        }

        @Test
        void aPairClosesOnItsClock() {
            DragonGates gates = new DragonGates();
            gates.open(new DragonGates.Pair(patchAt(Level.OVERWORLD, CENTER),
                    patchAt(Level.END, new BlockPos(100, 48, 0)), CLOSES_AT));
            assertTrue(gates.takeExpired(CLOSES_AT - 1).isEmpty());
            assertEquals(1, gates.takeExpired(CLOSES_AT).size());
            assertTrue(gates.pairs().isEmpty());
        }

        @Test
        void aPairSurvivesBeingSaved() {
            DragonGates gates = new DragonGates();
            DragonGates.Pair pair = new DragonGates.Pair(patchAt(Level.OVERWORLD, CENTER),
                    patchAt(Level.END, new BlockPos(100, 48, 0)), CLOSES_AT);
            gates.open(pair);
            DragonGates loaded = DragonGates.CODEC.parse(JsonOps.INSTANCE,
                    DragonGates.CODEC.encodeStart(JsonOps.INSTANCE, gates).getOrThrow()).getOrThrow();
            assertEquals(List.of(pair), loaded.pairs());
        }
    }
}
