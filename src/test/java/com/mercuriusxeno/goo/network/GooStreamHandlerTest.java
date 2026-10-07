package com.mercuriusxeno.goo.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import org.junit.jupiter.api.Test;

/** A stream tick reports each living thing whose health its program raised, the heal payload's list (decision vitality-waves-regenerate-and-court). */
class GooStreamHandlerTest {

    private static final int COW_ID = 7;

    private static LivingEntity cowGoing(float before, float after) {
        LivingEntity cow = mock(LivingEntity.class);
        when(cow.getId()).thenReturn(COW_ID);
        when(cow.getHealth()).thenReturn(before, after);
        return cow;
    }

    @Test
    void aThingWhoseHealthRoseIsReportedHealed() {
        List<Integer> healed = new ArrayList<>();
        GooStreamHandler.runHealing(cowGoing(4f, 4.1f), healed, () -> { });
        assertEquals(List.of(COW_ID), healed);
    }

    @Test
    void aThingAtFullHealthIsNotReported() {
        List<Integer> healed = new ArrayList<>();
        GooStreamHandler.runHealing(cowGoing(10f, 10f), healed, () -> { });
        assertEquals(List.of(), healed);
    }

    @Test
    void aThingTheProgramHurtIsNotReported() {
        List<Integer> healed = new ArrayList<>();
        GooStreamHandler.runHealing(cowGoing(10f, 9f), healed, () -> { });
        assertEquals(List.of(), healed);
    }
}
