package com.mercuriusxeno.goo.block.tap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A hit on a tap sorts to the valve, the canister or the body on every
 * facing, so the valve panel and the canister panel each show only for their
 * own part and neither shows for the body.
 */
class TapHitRegionTest {

    private static final BlockPos TAP_POS = new BlockPos(10, 64, -3);
    /** Low on the body, clear of the valve, the slot and the spigot, in blocks. */
    private static final Vec3 BODY_POINT = new Vec3(0.5, 0.05, 0.5);

    private static BlockHitResult hitAt(Vec3 local) {
        return new BlockHitResult(Vec3.atLowerCornerOf(TAP_POS).add(local), Direction.UP, TAP_POS, false);
    }

    private static Vec3 centerOf(AABB box) {
        return box.getCenter();
    }

    @ParameterizedTest
    @EnumSource(value = Direction.class, names = {"NORTH", "SOUTH", "EAST", "WEST"})
    void aValveHitSortsToTheValve(Direction facing) {
        BlockHitResult hit = hitAt(centerOf(TapValve.shape(facing).bounds()));

        assertEquals(TapHitRegion.VALVE, TapHitRegion.of(hit, TAP_POS, facing, true));
    }

    @ParameterizedTest
    @EnumSource(value = Direction.class, names = {"NORTH", "SOUTH", "EAST", "WEST"})
    void aCanisterHitSortsToTheCanisterOnlyWhileOneIsSlotted(Direction facing) {
        BlockHitResult hit = hitAt(centerOf(TapHitRegion.canisterSlotShape(facing).bounds()));

        assertEquals(TapHitRegion.CANISTER, TapHitRegion.of(hit, TAP_POS, facing, true));
        assertEquals(TapHitRegion.BODY, TapHitRegion.of(hit, TAP_POS, facing, false));
    }

    @ParameterizedTest
    @EnumSource(value = Direction.class, names = {"NORTH", "SOUTH", "EAST", "WEST"})
    void aBodyHitSortsToTheBody(Direction facing) {
        assertEquals(TapHitRegion.BODY, TapHitRegion.of(hitAt(BODY_POINT), TAP_POS, facing, true));
    }
}
