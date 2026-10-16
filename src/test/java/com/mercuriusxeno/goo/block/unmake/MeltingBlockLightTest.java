package com.mercuriusxeno.goo.block.unmake;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.loading.FMLLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * A melting block darkens nothing about it: light passes it, and the
 * blocks beside it are shaded as if it were air, so a cone being unmade is
 * lit as the open air it is becoming.
 */
class MeltingBlockLightTest {

    private static final BlockPos POS = new BlockPos(1, 2, 3);
    private static final float UNSHADED = 1.0F;

    private static BlockState melting;

    /**
     * The vanilla bootstrap asks FML whether it runs in production, which a test JVM
     * cannot answer, so a stubbed loader answers it while the bootstrap stands the blocks;
     * the bootstrap freezes the block registry, which a new block must write its holder to.
     */
    @BeforeAll
    @SuppressWarnings({"deprecation", "unchecked"})
    static void standAMeltingBlock() {
        try (MockedStatic<FMLLoader> loader = mockStatic(FMLLoader.class, RETURNS_DEEP_STUBS)) {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
        }
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK,
                Identifier.fromNamespaceAndPath("goo", "melting_block"));
        ((MappedRegistry<Block>) BuiltInRegistries.BLOCK).unfreeze(false);
        melting = new MeltingBlock(MeltingBlock.standInProperties().setId(key)).defaultBlockState();
        melting.initCache();
    }

    @Test
    void aMeltingBlockPassesAllLight() {
        assertEquals(0, melting.getLightDampening());
    }

    @Test
    void aMeltingBlockShadesTheBlocksBesideItAsAirDoes() {
        assertEquals(UNSHADED, melting.getShadeBrightness(mock(BlockGetter.class), POS));
    }

    @Test
    void aMeltingBlockBlocksNoView() {
        assertFalse(melting.isViewBlocking(mock(BlockGetter.class), POS));
    }
}
