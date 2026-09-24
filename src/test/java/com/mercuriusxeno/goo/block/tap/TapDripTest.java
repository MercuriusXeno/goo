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
 * One tap drip draws 1 mB of the held type from the tap's slot alone, and
 * sends one particle tinted with the type's color from the spigot underside.
 */
class TapDripTest {

    private static final int SLOT = 0;
    private static final BlockPos TAP_POS = new BlockPos(10, 64, -3);
    private static final int RGB = 0x336699;
    private static final float CHANNEL_MAX = 255f;
    private static final float COLOR_TOLERANCE = 1e-6f;

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

        assertNull(TapDrip.draw(holder, SLOT));
        verify(holder, never()).extractGoo(anyInt(), any(), anyInt());
    }

    @Test
    void drawExtractsOneMbOfTheHeldTypeOnly() {
        ICanisterHolder holder = holderHolding(GooTypes.ROCK, 1000);

        assertSame(GooTypes.ROCK, TapDrip.draw(holder, SLOT));
        verify(holder, times(1)).extractGoo(SLOT, GooTypes.ROCK, 1);
        verify(holder, times(1)).extractGoo(anyInt(), any(), anyInt());
    }

    @Test
    void drawThatExtractsNothingReportsNoType() {
        ICanisterHolder holder = holderHolding(GooTypes.ROCK, 0);

        assertNull(TapDrip.draw(holder, SLOT));
    }

    @Test
    @SuppressWarnings("unchecked")
    void oneDripSendsOneTintedParticleFromTheSpigot() {
        TapDrip.ParticleSink sink = mock(TapDrip.ParticleSink.class);
        ParticleType<ColorParticleOption> drip = mock(ParticleType.class);

        TapDrip.emit(sink, drip, RGB, TapSpigot.underside(TAP_POS));

        ArgumentCaptor<ColorParticleOption> option = ArgumentCaptor.forClass(ColorParticleOption.class);
        ArgumentCaptor<Vec3> start = ArgumentCaptor.forClass(Vec3.class);
        ArgumentCaptor<Double> fall = ArgumentCaptor.forClass(Double.class);
        verify(sink, times(1)).send(option.capture(), start.capture(), fall.capture());

        assertSame(drip, option.getValue().getType());
        assertEquals(0x33 / CHANNEL_MAX, option.getValue().getRed(), COLOR_TOLERANCE);
        assertEquals(0x66 / CHANNEL_MAX, option.getValue().getGreen(), COLOR_TOLERANCE);
        assertEquals(0x99 / CHANNEL_MAX, option.getValue().getBlue(), COLOR_TOLERANCE);
        AABB spigot = TapSpigot.box(TAP_POS);
        Vec3 at = start.getValue();
        assertTrue(spigot.contains(at), "drip starts inside the spigot box, at " + at);
        assertEquals(spigot.minY, at.y, "drip starts at the spigot underside");
        assertTrue(fall.getValue() < 0, "drip leaves the spigot moving down");
    }
}
