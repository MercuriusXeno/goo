package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.MutateStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.xeno.Mutation;
import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import java.util.List;
import java.util.stream.Stream;

/**
 * Gametest for xeno's Mutate: a pig the blob strikes carries one mutation
 * drawn from the table the shipped JSON names, and the change it works
 * stands on the pig.
 * xeno-blob-mutates-the-struck
 */
public final class MutateTests {

    private static final String XENO_MUTATE = "goo:xeno_mutate";
    private static final int NO_ENTITY = -1;
    private static final BlockPos PIG_POS = new BlockPos(2, 1, 2);
    private static final double UNCHANGED_SCALE = 1.0;
    /** How near an attribute reads to its expected value, past float rounding. */
    private static final double TOLERANCE = 1e-6;
    private static final String SHOULD_SHIP = "goo:xeno_mutate should ship with a mutate step naming a table";
    private static final String SHOULD_CARRY_ONE = "The struck pig should carry one mutation from the JSON's table %s, carried %s";
    private static final String SHOULD_STAND = "The pig's %s mutation should stand on it: scale %s, speed %s of %s, max health %s of %s";

    private MutateTests() {
    }

    /**
     * The xeno blob lands on a pig: the pig carries one mutation, an entry of
     * the shipped table, and the attribute it changes reads changed (a drop
     * mutation changes none, and only its entry shows).
     *
     * @param helper the gametest helper
     */
    public static void xenoMutatesTheStruckPig(GameTestHelper helper) {
        List<Mutation> table = shippedTable(helper);
        Mob pig = helper.spawnWithNoFreeWill(EntityType.PIG, PIG_POS);
        double baseSpeed = pig.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
        double baseHealth = pig.getAttributeBaseValue(Attributes.MAX_HEALTH);

        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, GooTypes.XENO,
                pig.getId(), pig.blockPosition(), Direction.UP, XENO_MUTATE));

        List<Mutation> worn = pig.getData(GooAttachments.MUTATIONS).worn();
        helper.assertTrue(worn.size() == 1 && table.contains(worn.getFirst()),
                String.format(SHOULD_CARRY_ONE, table, worn));
        Mutation mutation = worn.getFirst();
        double scale = pig.getAttributeValue(Attributes.SCALE);
        double speed = pig.getAttributeValue(Attributes.MOVEMENT_SPEED);
        double maxHealth = pig.getAttributeValue(Attributes.MAX_HEALTH);
        boolean stands = switch (mutation.kind()) {
            case SIZE -> near(scale, UNCHANGED_SCALE * (1 + mutation.amount()));
            case SPEED -> near(speed, baseSpeed * (1 + mutation.amount()));
            case HEALTH -> near(maxHealth, baseHealth + mutation.amount());
            case DROP -> near(scale, UNCHANGED_SCALE) && near(speed, baseSpeed) && near(maxHealth, baseHealth);
        };
        helper.assertTrue(stands, String.format(SHOULD_STAND, mutation.kind(), scale, speed, baseSpeed,
                maxHealth, baseHealth));
        helper.succeed();
    }

    private static boolean near(double actual, double expected) {
        return Math.abs(actual - expected) < TOLERANCE;
    }

    private static List<Mutation> shippedTable(GameTestHelper helper) {
        AbilityDefinition mutate = AbilityRegistry.of(helper.getLevel()).getAbility(Identifier.parse(XENO_MUTATE));
        helper.assertTrue(mutate != null, SHOULD_SHIP);
        List<Mutation> table = mutate.behaviors().stream()
                .flatMap(MutateTests::withDescendants)
                .filter(MutateStep.class::isInstance)
                .map(step -> ((MutateStep) step).table())
                .findFirst()
                .orElse(List.of());
        helper.assertTrue(!table.isEmpty(), SHOULD_SHIP);
        return table;
    }

    private static Stream<Step> withDescendants(Step step) {
        return Stream.concat(Stream.of(step), step.children().flatMap(MutateTests::withDescendants));
    }
}
