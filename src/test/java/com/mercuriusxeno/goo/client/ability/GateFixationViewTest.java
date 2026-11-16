package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.gate.GateFixation;
import com.mercuriusxeno.goo.ability.gate.GateSight;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The gate fixation view's hold on the field of view: every handler GateFixationView subscribes leaves a caster
 * mid-fixation the field of view they hold with no fixation, the moon coming to them rather than the view closing
 * in on it.
 * decision gate-fixates-your-view-on-the-moon-until-it-takes-you
 */
class GateFixationViewTest {

    private static final long CAST_AT = 1000L;
    private static final int FIXATION_TICKS = 60;
    private static final long MID_FIXATION = CAST_AT + FIXATION_TICKS * 3 / 4;
    private static final float SPRINTING_MODIFIER = 1.15F;
    private static final float FULL_SCALE = 1F;
    private static final float EXACT = 1e-6F;

    /**
     * The field of view modifier a caster ends on once every GateFixationView handler has seen the event.
     *
     * @param fixation the fixation the caster holds
     * @return the modifier
     */
    private static float fovModifierUnder(GateFixation fixation) {
        Level level = mock(Level.class);
        when(level.getGameTime()).thenReturn(MID_FIXATION);
        Player caster = mock(Player.class);
        when(caster.level()).thenReturn(level);
        when(caster.getData(GooAttachments.GATE_FIXATION)).thenReturn(fixation);
        IEventBus bus = BusBuilder.builder().build();
        bus.register(GateFixationView.class);
        ComputeFovModifierEvent event = new ComputeFovModifierEvent(caster, SPRINTING_MODIFIER, FULL_SCALE);
        bus.post(event);
        return event.getNewFovModifier();
    }

    @Test
    void aStandingFixationLeavesTheFieldOfViewAsNoFixationDoes() {
        GateFixation standing = GateFixation.held(GateSight.Sky.MOON, new Vec3(0, 1, 0), Vec3.ZERO, CAST_AT,
                FIXATION_TICKS);
        assertEquals(fovModifierUnder(GateFixation.NONE), fovModifierUnder(standing), EXACT);
    }
}
