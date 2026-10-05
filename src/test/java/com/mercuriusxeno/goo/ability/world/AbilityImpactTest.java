package com.mercuriusxeno.goo.ability.world;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.program.Expr;
import com.mercuriusxeno.goo.ability.program.FxAnchor;
import com.mercuriusxeno.goo.ability.program.SoundKind;
import com.mercuriusxeno.goo.ability.program.SoundStep;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AbilityImpact.land places no block of its own: a program that ends the
 * tick its blob lands leaves only its effect (decisions
 * splat-runs-the-program-no-fuse, lingering-abilities-place-their-own-thing).
 */
class AbilityImpactTest {

    private static final BlockPos WALL = new BlockPos(3, 64, -2);
    private static final Identifier CHIME = Identifier.parse("minecraft:block.note_block.chime");

    /**
     * Stands NeoForge's AttachmentHolder and the built-in registries before the
     * first mock of a level: both class inits ask FML whether it runs in
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
    void aOneTickProgramLandsNoBlock() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.getBlockState(WALL)).thenReturn(Blocks.STONE.defaultBlockState());
        when(level.getBlockState(WALL.south())).thenReturn(Blocks.AIR.defaultBlockState());
        when(level.players()).thenReturn(List.of());
        AbilityDefinition chime = new AbilityDefinition(Identifier.parse("goo:test_chime"), GooTypes.UNSTABLE,
                "chime", "", 0, 0, Delivery.ARC,
                List.of(new SoundStep(CHIME, FxAnchor.HOST, SoundKind.BLOCKS, Expr.literal(1), Expr.literal(1))),
                List.of(), AbilityBadge.WORLD, List.of());

        AbilityImpact.land(level, WALL, GooTypes.UNSTABLE, Direction.SOUTH, chime);

        assertTrue(mockingDetails(level).getInvocations().stream()
                .anyMatch(call -> "playSound".equals(call.getMethod().getName())), "the program did not run");
        verify(level, never()).setBlock(any(), any(), anyInt());
    }
}
