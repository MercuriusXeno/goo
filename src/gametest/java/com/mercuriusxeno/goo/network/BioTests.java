package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.registry.GooMobEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Gametests for Leaf Bio: a struck zombie loses the toxin's share of its max
 * health each second, a second strike raises the toxin to amplitude 2 and
 * doubles the second's loss, and a third leaves it at 2 with its duration
 * refreshed.
 * bio-toxin-stacks-to-amplitude-two
 */
public final class BioTests {

    private static final String BIO = "goo:leaf_bio";
    private static final BlockPos ZOMBIE_POS = new BlockPos(2, 1, 2);
    private static final int SECOND = 20;
    /** leaf_bio.json's share of max health a second, and its duration. */
    private static final double SHARE = 0.06;
    private static final int DURATION = 100;
    /** A strike lands a tick in, the second two seconds later, the third a second after that. */
    private static final int FIRST_STRIKE = 1;
    private static final int SECOND_STRIKE = FIRST_STRIKE + 2 * SECOND;
    private static final int THIRD_STRIKE = SECOND_STRIKE + 1 + SECOND;
    private static final float TOLERANCE = 0.01f;
    private static final String ABILITY_REQUIRED = "Ability registry must hold leaf_bio";
    private static final String ONE_SECOND_LOSS = "A second at amplitude %d should take %.2f, took %.2f";
    private static final String AMPLITUDE = "The toxin should stand at amplitude %d, stands at %d";
    private static final String REFRESHED = "The third strike should refresh the toxin to %d ticks, stands at %d";

    private BioTests() {
    }

    /**
     * Strikes a helmeted zombie with Bio three times: one second at amplitude
     * 1 takes 6% of its max health, the second strike makes it amplitude 2
     * and a second then takes 12%, and the third leaves it at 2 with its
     * duration refreshed to the full five seconds.
     *
     * @param helper the gametest helper
     */
    public static void bioStacksToTwo(GameTestHelper helper) {
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, ZOMBIE_POS);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        float perSecond = (float) (SHARE * zombie.getMaxHealth());
        float[] health = new float[1];
        helper.runAfterDelay(FIRST_STRIKE, () -> strike(helper, zombie));
        helper.runAfterDelay(FIRST_STRIKE + SECOND, () -> health[0] = zombie.getHealth());
        helper.runAfterDelay(FIRST_STRIKE + 2 * SECOND, () -> {
            assertLoss(helper, 1, perSecond, health[0] - zombie.getHealth());
            assertAmplitude(helper, zombie, 1);
            strike(helper, zombie);
            assertAmplitude(helper, zombie, 2);
            health[0] = zombie.getHealth();
        });
        helper.runAfterDelay(SECOND_STRIKE + SECOND, () -> {
            assertLoss(helper, 2, 2 * perSecond, health[0] - zombie.getHealth());
            strike(helper, zombie);
        });
        helper.runAfterDelay(THIRD_STRIKE, () -> {
            assertAmplitude(helper, zombie, 2);
            int left = zombie.getEffect(GooMobEffects.BIO_TOXIN).getDuration();
            helper.assertTrue(left >= DURATION - 1, String.format(REFRESHED, DURATION, left));
            helper.succeed();
        });
    }

    private static void assertLoss(GameTestHelper helper, int amplitude, float expected, float lost) {
        helper.assertTrue(Math.abs(lost - expected) < TOLERANCE,
                String.format(ONE_SECOND_LOSS, amplitude, expected, lost));
    }

    private static void assertAmplitude(GameTestHelper helper, Mob zombie, int amplitude) {
        MobEffectInstance toxin = zombie.getEffect(GooMobEffects.BIO_TOXIN);
        int standing = toxin == null ? 0 : toxin.getAmplifier() + 1;
        helper.assertTrue(standing == amplitude, String.format(AMPLITUDE, amplitude, standing));
    }

    private static void strike(GameTestHelper helper, Mob mob) {
        AbilityDefinition bio = AbilityRegistry.of(helper.getLevel()).getAbility(Identifier.parse(BIO));
        helper.assertTrue(bio != null, ABILITY_REQUIRED);
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, bio.gooType(),
                mob.getId(), mob.blockPosition(), Direction.UP, BIO));
    }
}
