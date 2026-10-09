package com.mercuriusxeno.goo.ability.gate;

import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
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
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mockStatic;

/**
 * Where a Dragon Gate lies, where it sets travellers down, and how a
 * server's open pairs are kept and closed on their clocks
 * (decision dragon-gate-banishes-blocks-and-opens-a-portal).
 */
class DragonGatesTest {

    private static final BlockPos CENTER = new BlockPos(10, 64, -5);
    private static final int CELLS = 9;
    private static final long CLOSES_AT = 1200L;

    @BeforeAll
    static void standRegistries() {
        try (MockedStatic<FMLLoader> loader = mockStatic(FMLLoader.class, RETURNS_DEEP_STUBS)) {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
        }
    }

    private static GatePatch patchAt(ResourceKey<Level> level, BlockPos center) {
        List<GatePatch.Covered> covered = GateFootprint.patch(center, Direction.UP).stream()
                .map(cell -> new GatePatch.Covered(cell, Blocks.STONE.defaultBlockState())).toList();
        return new GatePatch(level, center, Direction.UP, covered);
    }

    @Nested
    class Footprint {

        @ParameterizedTest
        @EnumSource(Direction.class)
        void coversNineCellsInTheStruckFacesPlane(Direction face) {
            List<BlockPos> cells = GateFootprint.patch(CENTER, face);
            assertEquals(CELLS, new HashSet<>(cells).size());
            assertTrue(cells.stream().allMatch(cell -> cell.get(face.getAxis()) == CENTER.get(face.getAxis())));
            assertTrue(cells.contains(CENTER));
        }

        @ParameterizedTest
        @EnumSource(Direction.class)
        void setsTravellersDownOutsideThePatchOnItsOpenSide(Direction face) {
            Vec3 feet = GateFootprint.arrival(CENTER, face);
            Set<BlockPos> cells = Set.copyOf(GateFootprint.patch(CENTER, face));
            assertFalse(cells.contains(BlockPos.containing(feet)));
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
            assertEquals(Optional.of(far), gates.partnerOf(Level.OVERWORLD, CENTER.east()));
            assertEquals(Optional.of(near), gates.partnerOf(Level.END, new BlockPos(100, 48, 1)));
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
