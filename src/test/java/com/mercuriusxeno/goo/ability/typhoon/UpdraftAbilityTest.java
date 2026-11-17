package com.mercuriusxeno.goo.ability.typhoon;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.ability.program.UpdraftStep;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Typhoon's world goo ships as typhoon_updraft.json, a residual stream
 * spray, and the column a cell of it lifts through stands a block over its
 * top for each of its layers. Over the push stands a glide zone, where a
 * fall slows and a player's keys steer it through the air.
 * updraft-is-typhoons-world-goo-that-lifts-the-entities-above-it
 * updraft-wears-the-residual-badge
 * updraft-goo-lifts-one-block-per-layer
 */
class UpdraftAbilityTest {

    private static final String UPDRAFT = "typhoon_updraft";

    @Test
    void updraftWearsTheResidualBadgeAndSpraysAsAStream() {
        AbilityDefinition updraft = AbilityJson.decode(UPDRAFT);

        assertEquals(AbilityBadge.RESIDUAL, updraft.badge());
        assertEquals(DeliveryKind.STREAM, updraft.delivery().kind());
    }

    @Test
    void forwardSteersAPlayerOverTheGooTowardItsFacingByTheSteerShare() {
        UpdraftStep updraft = shipped();
        Vec3 steered = updraft.steered(Vec3.ZERO, new Vec3(0, 0, 1), 0f);

        assertEquals(new Vec3(0, 0, updraft.airSpeed() * updraft.airSteer()), steered);
    }

    @Test
    void noKeysLeaveAPlayersMotionOverTheGoo() {
        Vec3 drifting = new Vec3(0.2, -0.1, 0.05);

        assertEquals(drifting, shipped().steered(drifting, Vec3.ZERO, 90f));
    }

    @Test
    void aFallOverTheGlideZonesTopIsLeftAlone() {
        UpdraftStep updraft = shipped();
        int layers = 4;
        double overTheZone = (double) layers / 15 + UpdraftStep.heightOver(layers) + updraft.glideHeight() + 0.5;

        assertTrue(updraft.carried(new Vec3(0, -0.8, 0), overTheZone, layers).isEmpty());
    }

    @Test
    void aFallInTheGlideZoneSlowsToTheFallCap() {
        UpdraftStep updraft = shipped();
        int layers = 4;
        double inTheZone = (double) layers / 15 + UpdraftStep.heightOver(layers) + updraft.glideHeight() / 2;

        assertEquals(-updraft.fallCap(), updraft.carried(new Vec3(0, -0.8, 0), inTheZone, layers).orElseThrow().y,
                1e-9);
    }

    private static UpdraftStep shipped() {
        return UpdraftStep.in(AbilityJson.decode(UPDRAFT).behaviors()).orElseThrow();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 4, 15})
    void aCellsColumnStandsABlockOverItsTopForEachLayer(int layers) {
        assertEquals(layers, UpdraftStep.heightOver(layers));
    }
}
