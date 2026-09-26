package com.mercuriusxeno.goo.block.tap;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.block.canister.ICanisterHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * One tap drip draws its grade's mB of the held type from the tap's slot alone, and
 * sends one particle tinted with the type's color from the spigot underside.
 */
class TapDripTest {

    private static final int SLOT = 0;
    private static final BlockPos TAP_POS = new BlockPos(10, 64, -3);
    private static final int RGB = 0x336699;
    private static final float CHANNEL_MAX = 255f;
    private static final float COLOR_TOLERANCE = 1e-6f;
    private static final int FOUR_MB = 4;

    private static ICanisterHolder holderHolding(ResourceKey<GooTypeDefinition> type, int amount) {
        ICanisterHolder holder = mock(ICanisterHolder.class);
        when(holder.getSlotGooType(SLOT)).thenReturn(type);
        when(holder.extractGoo(eq(SLOT), any(), anyInt()))
                .thenAnswer(call -> Math.min(amount, call.<Integer>getArgument(2)));
        return holder;
    }

    @Test
    void emptySlotDrawsNothingAndReportsNoType() {
        ICanisterHolder holder = holderHolding(null, 0);

        assertNull(TapDrip.draw(holder, SLOT, 1));
        verify(holder, never()).extractGoo(anyInt(), any(), anyInt());
    }

    @Test
    void drawExtractsTheGradesVolumeOfTheHeldTypeOnly() {
        ICanisterHolder holder = holderHolding(GooTypes.ROCK, 1000);

        assertEquals(new TapDrip.Drawn(GooTypes.ROCK, FOUR_MB), TapDrip.draw(holder, SLOT, FOUR_MB));
        verify(holder, times(1)).extractGoo(SLOT, GooTypes.ROCK, FOUR_MB);
        verify(holder, times(1)).extractGoo(anyInt(), any(), anyInt());
    }

    @Test
    void drawFromALowCanisterCarriesWhatWasLeft() {
        ICanisterHolder holder = holderHolding(GooTypes.ROCK, 2);

        assertEquals(new TapDrip.Drawn(GooTypes.ROCK, 2), TapDrip.draw(holder, SLOT, FOUR_MB));
    }

    @Test
    void drawThatExtractsNothingReportsNoType() {
        ICanisterHolder holder = holderHolding(GooTypes.ROCK, 0);

        assertNull(TapDrip.draw(holder, SLOT, 1));
    }

    /**
     * One drip sends one tinted particle from the spigot underside, falling
     * straight down: zero x and z speed (decision tap-drip-own-square-particles).
     */
    @Test
    @SuppressWarnings("unchecked")
    void oneDripSendsOneTintedParticleStraightDownFromTheSpigot() {
        TapDrip.ParticleSink sink = mock(TapDrip.ParticleSink.class);
        ParticleType<ColorParticleOption> drip = mock(ParticleType.class);

        TapDrip.emit(sink, drip, RGB, TapSpigot.underside(TAP_POS));

        ArgumentCaptor<ColorParticleOption> option = ArgumentCaptor.forClass(ColorParticleOption.class);
        ArgumentCaptor<Vec3> start = ArgumentCaptor.forClass(Vec3.class);
        ArgumentCaptor<Vec3> velocity = ArgumentCaptor.forClass(Vec3.class);
        verify(sink, times(1)).send(option.capture(), start.capture(), velocity.capture());

        assertSame(drip, option.getValue().getType());
        assertEquals(0x33 / CHANNEL_MAX, option.getValue().getRed(), COLOR_TOLERANCE);
        assertEquals(0x66 / CHANNEL_MAX, option.getValue().getGreen(), COLOR_TOLERANCE);
        assertEquals(0x99 / CHANNEL_MAX, option.getValue().getBlue(), COLOR_TOLERANCE);
        AABB spigot = TapSpigot.box(TAP_POS);
        Vec3 at = start.getValue();
        assertTrue(spigot.contains(at), "drip starts inside the spigot box, at " + at);
        assertEquals(spigot.minY, at.y, "drip starts at the spigot underside");
        assertEquals(new Vec3(0.0, TapDrip.DRIP_LEAVE_SPEED, 0.0), velocity.getValue());
    }
}
