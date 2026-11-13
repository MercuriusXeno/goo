package com.mercuriusxeno.goo.block.hub;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The hub's pick-block target: the pick reads a canister only when the crosshair hit is a block
 * hit on the hub's own position.
 */
class HubBlockHandlersTest {

    private static final BlockPos HUB_POS = new BlockPos(4, 64, 4);

    @Test
    void aBlockHitOnTheHubsPositionAimsAtTheHub() {
        BlockHitResult onTheHub = new BlockHitResult(Vec3.atCenterOf(HUB_POS), Direction.UP, HUB_POS, false);

        assertEquals(true, HubBlockHandlers.aimsAt(onTheHub, HUB_POS));
    }
}
