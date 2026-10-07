package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Flatten's step breaks one block a tick from the cursor's 3x3 above the
 * plane, up to 3 blocks high, top down, passing blocks outside its tag or
 * out of reach; a host outside a held channel breaks nothing
 * (decision flatten-disc-cursor-breaks-above-the-plane).
 */
class FlattenStepTest {

    private static final TagKey<Block> BREAKABLE =
            TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("goo", "flatten_breakable"));
    /** The plane remembered from the cursor resting on the block at y 63. */
    private static final double PLANE = 64;
    private static final Vec3 EYE = new Vec3(8, 65.6, 0.5);
    /** The west face of the block at (10, 64, 0). */
    private static final Vec3 AIM = new Vec3(10, 64.5, 0.5);

    private static ChannelHost hostWhere(Predicate<BlockPos> inTag, Optional<ChannelAim> aim) {
        ChannelHost host = mock(ChannelHost.class);
        when(host.kind()).thenReturn(HostKind.PLAYER);
        when(host.channelAim()).thenReturn(aim);
        when(host.eye()).thenReturn(EYE);
        when(host.blockIn(any(), any())).thenAnswer(call -> inTag.test(call.getArgument(0)));
        when(host.reaches(any())).thenReturn(true);
        return host;
    }

    private static Optional<ChannelAim> aimed() {
        return Optional.of(new ChannelAim(AIM, PLANE));
    }

    private static void run(ChannelHost host) {
        ProgramBehavior.forHost(List.of(new FlattenStep(BREAKABLE)), HostKind.PLAYER).tick(host);
    }

    @Test
    void oneTickBreaksOneBlockFromTheTopLayer() {
        ChannelHost host = hostWhere(pos -> true, aimed());

        run(host);

        verify(host, times(1)).breakBlock(any());
        verify(host).breakBlock(new BlockPos(9, 66, -1));
    }

    @Test
    void aTopLayerOutsideTheTagPassesToTheNextLayerDown() {
        ChannelHost host = hostWhere(pos -> pos.getY() < 66, aimed());

        run(host);

        verify(host).breakBlock(new BlockPos(9, 65, -1));
    }

    @Test
    void theCursorsLevelAndBelowStay() {
        ChannelHost host = hostWhere(pos -> pos.getY() < 64, aimed());

        run(host);

        verify(host, never()).breakBlock(any());
    }

    @Test
    void aHostOutsideAHeldChannelBreaksNothing() {
        ChannelHost host = hostWhere(pos -> true, Optional.empty());

        run(host);

        verify(host, never()).breakBlock(any());
    }
}
