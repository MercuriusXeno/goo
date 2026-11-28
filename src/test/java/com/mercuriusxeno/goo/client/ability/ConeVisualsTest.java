package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityArea;
import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.throwing.ConeShape;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * Every coned stream holds a cone-bound visual: each shipped channeled stream whose cone reaches out to a range
 * answers a visual in the registry, so a coned stream added later fails here until its visual joins; and the
 * glove's held visual of Cold, Gust, Vacuum and Heal draws at its Delivery's cone and range, the one cone the
 * ability carries (decisions spray-abilities-render-procedurally-bound-to-their-cone,
 * cone-style-abilities-are-reviewed-for-consistency).
 */
class ConeVisualsTest {

    /**
     * The coned streams shipped: the twelve standing when the review closed, and Paint.
     * paint-recolors-dyeable-things-to-the-off-hand-dye
     */
    private static final int SHIPPED_CONED_STREAMS = 13;
    private static final List<String> GLOVE_HELD = List.of("frost_cold", "typhoon_gust", "void_vacuum", "jelly_heal");
    private static final Vec3 HAND = new Vec3(3, 65.2, -1.4);
    private static final Vec3 LOOK = new Vec3(0, 0, 1);

    @Test
    void everyShippedConedStreamAnswersAConeVisual() {
        List<AbilityDefinition> coned = AbilityJson.files().stream().map(AbilityJson::decode)
                .filter(ConeVisualsTest::isConedStream).toList();
        for (AbilityDefinition ability : coned) {
            System.out.println("coned stream walked: " + ability.id());
            assertNotNull(ConeVisuals.visualFor(ability.id().toString()), ability.id() + " holds no cone visual");
        }
        assertEquals(SHIPPED_CONED_STREAMS, coned.size(), "the coned streams walked");
    }

    @Test
    void theGlovesHeldVisualOfColdGustVacuumAndHealDrawsAtItsDeliverysCone() {
        for (String name : GLOVE_HELD) {
            AbilityDefinition ability = AbilityJson.decode(name);
            Delivery delivery = ability.delivery();
            SprayCone cone = SprayCone.ofGlove(delivery, HAND, LOOK);
            assertEquals(ConeShape.round(delivery.coneDegrees()), cone.shape(), name + "'s shape");
            assertEquals(delivery.range(), cone.reach(), name + "'s reach");
            assertNotSame(AbilityArea.Shape.CONE, ability.area().shape(), name + " carries a second cone in its area");
        }
    }

    /**
     * A coned stream: a channeled ability whose delivery is a stream with a cone reaching out to a range.
     *
     * @param ability the ability
     * @return true for a coned stream
     */
    private static boolean isConedStream(AbilityDefinition ability) {
        Delivery delivery = ability.delivery();
        return ability.badge() == AbilityBadge.CHANNELED && delivery.kind() == DeliveryKind.STREAM
                && delivery.coneDegrees() > 0 && delivery.range() > 0;
    }
}
