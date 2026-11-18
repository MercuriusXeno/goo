package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.AbilityTags;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.ThrowRange;
import com.mercuriusxeno.goo.ability.program.GrabStep;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The glove's aim mode reads the selected ability's badge, never its entity
 * tag (decision target-kind-configured-per-ability).
 */
class GloveAimTest {

    private static ClientAbility ability(AbilityBadge badge, List<String> tags) {
        return new ClientAbility(Identifier.fromNamespaceAndPath(Goo.MODID, "aimed"), "ability.goo.aimed", "", 0,
                tags, List.of(), 0, Delivery.ARC, badge, List.of());
    }

    private static ClientAbility shipped(String name) {
        AbilityDefinition definition = AbilityJson.decode(name);
        return new ClientAbility(definition.id(), definition.displayName(), "", 0, List.of(), definition.behaviors(),
                0, definition.delivery(), definition.badge(), List.of());
    }

    /**
     * The glove aims as far as the selected ability's delivery names.
     * decision each-ability-names-its-range-and-the-shared-throw-range-retires
     */
    @Test
    void anAbilityWhoseDeliveryNamesSixteenBlocksAimsSixteenBlocks() {
        assertEquals(16, GloveAim.aimRangeOf(shipped("weird_mitosis"), null));
    }

    @Test
    void grabAimsAsFarAsItsReach() {
        ClientAbility grab = shipped("kinetic_grab");

        assertEquals(GrabStep.reachOf(grab.behaviors(), null).orElseThrow(), GloveAim.aimRangeOf(grab, null));
    }

    /**
     * Zap aims 32 blocks, the range its delivery names, and Build's cast ray
     * reads the same aim range as every thrown ability.
     * decision zap-starts-at-thirty-two-blocks
     * decision each-ability-names-its-range-and-the-shared-throw-range-retires
     */
    @Test
    void zapAimsThirtyTwoBlocks() {
        assertEquals(32, GloveAim.aimRangeOf(shipped("pulse_zap"), null));
    }

    @Test
    void buildAimsSixteenBlocks() {
        assertEquals(16, GloveAim.aimRangeOf(shipped("rock_build"), null));
    }

    @Test
    void dartAndKnivesAimAsFarAsTheirOwnReach() {
        assertEquals(ThrowRange.KNIVES_AND_DART_REACH, GloveAim.aimRangeOf(shipped("metal_dart"), null));
        assertEquals(ThrowRange.KNIVES_AND_DART_REACH, GloveAim.aimRangeOf(shipped("crystal_knives"), null));
    }

    /**
     * Every ability aims as far as its data file names, and 16 blocks where
     * it names none.
     * decision each-ability-names-its-range-and-the-shared-throw-range-retires
     * decision abilities-leaving-the-shared-range-start-at-sixteen-blocks
     */
    @Test
    void aStreamAimsAsFarAsItsOwnReach() {
        assertEquals(4, GloveAim.aimRangeOf(shipped("hex_drain"), null));
    }

    @Test
    void clickAimsAsFarAsItsScanReads() {
        assertEquals(32, GloveAim.aimRangeOf(shipped("deep_click"), null));
    }

    @Test
    void frostOrbAimsAsFarAsItRolls() {
        assertEquals(16, GloveAim.aimRangeOf(shipped("frost_orb"), null));
    }

    @Test
    void laserAimsThirtyTwoBlocks() {
        assertEquals(32, GloveAim.aimRangeOf(shipped("glow_laser"), null));
    }

    @Test
    void aSelfAbilityNamingNoRangeAimsSixteenBlocks() {
        assertEquals(16, GloveAim.aimRangeOf(shipped("ender_blink"), null));
    }

    @Test
    void aDripStreamNamingNoRangeAimsSixteenBlocks() {
        assertEquals(16, GloveAim.aimRangeOf(shipped("blaze_molten"), null));
    }

    @Test
    void aMobBadgeFavorsEntitiesWithTheEntityTagAbsent() {
        assertEquals(TargetingHint.ENTITY, GloveAim.hintOf(ability(AbilityBadge.MOB, List.of())));
    }

    @Test
    void theEntityTagNoLongerSteersAWorldOrFreeAbility() {
        assertEquals(TargetingHint.BLOCK, GloveAim.hintOf(ability(AbilityBadge.WORLD, List.of(AbilityTags.ENTITY))));
        assertEquals(TargetingHint.POINT, GloveAim.hintOf(ability(AbilityBadge.FREE, List.of(AbilityTags.ENTITY))));
    }
}
