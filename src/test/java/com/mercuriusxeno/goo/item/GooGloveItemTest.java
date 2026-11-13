package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.item.GooGloveItem.GloveTier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

/**
 * Each glove tier spells the attack damage its main-hand melee hit adds:
 * 2 for the glove, 4 for the gauntlet and 6 for the exo gauntlet, carried
 * as the tier's main-hand attack damage modifier (decision glove-damage-by-tier);
 * and a right click with the glove answers PASS
 * (decision use-animation-only-when-goo-throws).
 */
class GooGloveItemTest {

    private static final double EXO_GAUNTLET_BONUS = 6;

    @ParameterizedTest
    @CsvSource({"GLOVE, 2", "GAUNTLET, 4", "EXO_GAUNTLET, 6"})
    void eachTierAddsItsAttackDamage(GloveTier tier, double bonus) {
        assertEquals(bonus, tier.attackDamageBonus());
    }

    @Test
    void theExoGauntletAddsSixAttackDamageInTheMainHand() {
        List<ItemAttributeModifiers.Entry> entries = GooGloveItem.attackModifiers(GloveTier.EXO_GAUNTLET).modifiers();

        assertEquals(List.of(Attributes.ATTACK_DAMAGE), entries.stream().map(ItemAttributeModifiers.Entry::attribute).toList());
        assertEquals(List.of(EXO_GAUNTLET_BONUS), entries.stream().map(entry -> entry.modifier().amount()).toList());
        assertEquals(List.of(EquipmentSlotGroup.MAINHAND), entries.stream().map(ItemAttributeModifiers.Entry::slot).toList());
    }

    /**
     * A Success would have vanilla drop and re-raise the held glove, so the right click answers PASS.
     */
    @Test
    void aRightClickWithTheGloveAnswersPass() {
        GooGloveItem glove = mock(GooGloveItem.class, CALLS_REAL_METHODS);

        assertEquals(InteractionResult.PASS, glove.use(mock(Level.class), mock(Player.class), InteractionHand.MAIN_HAND));
    }
}
