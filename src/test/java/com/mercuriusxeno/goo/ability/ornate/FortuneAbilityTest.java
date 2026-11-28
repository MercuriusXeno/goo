package com.mercuriusxeno.goo.ability.ornate;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.FortuneStep;
import com.mercuriusxeno.goo.block.ability.CellBinding;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ornate's fan over the loaded abilities holds Fortune, its residual, whose
 * program lays Fortune goo on a cell bound to Ornate.
 * fortune-is-ornates-residual-that-makes-things-drop-more
 * ornate-is-the-fortune-goo-with-painting-and-coloring-abilities
 */
class FortuneAbilityTest {

    private static final Identifier FORTUNE = Identifier.fromNamespaceAndPath("goo", "ornate_fortune");

    @Test
    void ornatesResidualIsFortune() {
        AbilityRegistry registry = new AbilityRegistry(AbilityJson.files().stream().map(AbilityJson::decode)
                .collect(Collectors.toMap(AbilityDefinition::id, Function.identity())));

        List<AbilityDefinition> residuals = registry.getAbilitiesForType(GooTypes.ORNATE).stream()
                .filter(ability -> ability.badge() == AbilityBadge.RESIDUAL).toList();

        assertEquals(List.of(FORTUNE), residuals.stream().map(AbilityDefinition::id).toList());
        assertTrue(FortuneStep.in(residuals.getFirst().behaviors()).isPresent(), "Fortune lays no Fortune goo");
        assertEquals(GooTypes.ORNATE, CellBinding.FORTUNE.gooType());
    }
}
