package com.mercuriusxeno.goo.ability.deep;

import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * A Sculker body passes through blocks only in the dive, hangs still with no key held, stops at a bedrock face and
 * is never trapped by bedrock it already overlaps (decision sculker-swims-solid-blocks-and-surfaces-at-will). The
 * vanilla registries stand Blocks.BEDROCK, so a stubbed FML loader answers while they bootstrap.
 */
class SculkerBodyTest {

    private static final Vec3 SOUTH = new Vec3(0, 0, 1);
    /** A player's body standing on the bedrock layer's top face at y 0. */
    private static final AABB ON_THE_LAYER = new AABB(0.2, 0, 0.2, 0.8, 1.8, 0.8);
    /** The same body with its feet 0.3 into the bedrock layer. */
    private static final AABB IN_THE_LAYER = ON_THE_LAYER.move(0, -0.3, 0);

    @BeforeAll
    static void bootstrapVanillaRegistries() {
        try (MockedStatic<FMLLoader> loader = mockStatic(FMLLoader.class, RETURNS_DEEP_STUBS)) {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
        }
    }

    private static Player sculker(boolean diving) {
        Level level = mock(Level.class);
        Player player = mock(Player.class);
        when(player.level()).thenReturn(level);
        when(player.getData(GooAttachments.SCULKER)).thenReturn(new Sculker(Sculker.NEVER_EXPIRES, diving));
        return player;
    }

    // A level whose blocks below y 0 are bedrock and every other block air.
    private static Level overABedrockLayer() {
        BlockState bedrock = mock(BlockState.class);
        when(bedrock.is(Blocks.BEDROCK)).thenReturn(true);
        BlockState air = mock(BlockState.class);
        Level level = mock(Level.class);
        when(level.getBlockState(any(BlockPos.class)))
                .thenAnswer(call -> call.<BlockPos>getArgument(0).getY() < 0 ? bedrock : air);
        return level;
    }

    @Test
    void aPlayerWhoseSculkerStandsPassesThroughBlocksOnlyInTheDive() {
        assertEquals(List.of(false, true),
                List.of(SculkerSwim.passesThrough(sculker(false)), SculkerSwim.passesThrough(sculker(true))));
    }

    @Test
    void aStrokeFromRestWithNoKeyHeldIsRest() {
        assertEquals(Vec3.ZERO, SculkerSwim.swum(Vec3.ZERO, 0, 0, 0, SOUTH));
    }

    @Test
    void aSinkIntoBedrockStopsAtItsTopFace() {
        assertEquals(Vec3.ZERO, SculkerSwim.heldOutOfBedrock(overABedrockLayer(), ON_THE_LAYER, new Vec3(0, -0.5, 0)));
    }

    @Test
    void aBodyAlreadyInBedrockKeepsItsWholeMove() {
        Vec3 rise = new Vec3(0, 0.5, 0);

        assertEquals(rise, SculkerSwim.heldOutOfBedrock(overABedrockLayer(), IN_THE_LAYER, rise));
    }
}
