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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Flatten's step breaks the block under the cursor only when it stands above
 * the hold's plane, belongs to the tag the ability's JSON names and lies in
 * the player's reach; a host outside a held channel breaks nothing
 * (decision flatten-disc-cursor-breaks-above-the-plane).
 */
class FlattenStepTest {

    private static final TagKey<Block> BREAKABLE =
            TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("goo", "flatten_breakable"));
    private static final double PLANE = 64;
    private static final Vec3 EYE = new Vec3(8, 65.6, 0.5);
    /** The west face of the block at (10, 64, 0), the first block above the plane. */
    private static final Vec3 ABOVE_PLANE_FACE = new Vec3(10, 64.5, 0.5);
    private static final BlockPos ABOVE_PLANE_BLOCK = new BlockPos(10, 64, 0);
    /** The top face of the block at (10, 63, 0), the block the plane rests on. */
    private static final Vec3 PLANE_TOP_FACE = new Vec3(10.5, 64, 0.5);

    private static ChannelHost hostAiming(Vec3 aimPoint, boolean inTag, boolean inReach) {
        ChannelHost host = mock(ChannelHost.class);
        when(host.kind()).thenReturn(HostKind.PLAYER);
        when(host.channelAim()).thenReturn(Optional.of(new ChannelAim(aimPoint, PLANE)));
        when(host.eye()).thenReturn(EYE);
        when(host.blockIn(any(), any())).thenReturn(inTag);
        when(host.reaches(any())).thenReturn(inReach);
        return host;
    }

    private static void run(ChannelHost host) {
        ProgramBehavior.forHost(List.of(new FlattenStep(BREAKABLE)), HostKind.PLAYER).tick(host);
    }

    @Test
    void aimedBlockAbovePlaneInTagAndReachBreaks() {
        ChannelHost host = hostAiming(ABOVE_PLANE_FACE, true, true);

        run(host);

        verify(host).breakBlock(ABOVE_PLANE_BLOCK);
    }

    @Test
    void blockThePlaneRestsOnStays() {
        ChannelHost host = hostAiming(PLANE_TOP_FACE, true, true);

        run(host);

        verify(host, never()).breakBlock(any());
    }

    @Test
    void blockOutsideTheTagStays() {
        ChannelHost host = hostAiming(ABOVE_PLANE_FACE, false, true);

        run(host);

        verify(host, never()).breakBlock(any());
    }

    @Test
    void blockOutOfReachStays() {
        ChannelHost host = hostAiming(ABOVE_PLANE_FACE, true, false);

        run(host);

        verify(host, never()).breakBlock(any());
    }

    @Test
    void hostOutsideAHeldChannelBreaksNothing() {
        ChannelHost host = hostAiming(ABOVE_PLANE_FACE, true, true);
        when(host.channelAim()).thenReturn(Optional.empty());

        run(host);

        verify(host, never()).breakBlock(any());
    }
}
