package com.mercuriusxeno.goo.ability.gluttony;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Runs a glutton's points each tick while Gluttony stands: hunger and health
 * fill, then bank past their caps, the overheal standing as absorption hearts
 * under a max absorption the effect lends and takes back when it ends.
 * gluttony-overheals-and-overhungers
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class GluttonyEvents {

    /** The max absorption Gluttony lends for its overheal. */
    static final Identifier OVERHEAL_ID = Identifier.fromNamespaceAndPath(Goo.MODID, "gluttony_overheal");
    /** The food points one point or one banked refill adds. */
    private static final int FOOD_PER_POINT = 1;
    /** A point adds hunger, not saturation. */
    private static final float NO_SATURATION = 0f;

    private GluttonyEvents() {
    }

    /**
     * Advances a standing gluttony one tick on the server.
     *
     * @param event the player tick event
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.hasData(GooAttachments.GLUTTONY)) {
            return;
        }
        Gluttony standing = player.getData(GooAttachments.GLUTTONY);
        if (!standing.stands()) {
            return;
        }
        long now = player.level().getGameTime();
        lendMaxAbsorption(player, standing.maxOverheal());
        Gluttony fed = feed(player, standing.spentTo(player.getAbsorptionAmount()), now);
        Gluttony after = fed.tick(now);
        if (!after.stands()) {
            end(player);
        } else if (!after.equals(standing)) {
            player.setData(GooAttachments.GLUTTONY, after);
        }
    }

    /**
     * Ends the player's gluttony: the overheal it owns leaves the extra
     * hearts, the max absorption it lent returns and the banks empty.
     *
     * @param player the player
     */
    public static void end(ServerPlayer player) {
        Gluttony standing = player.getData(GooAttachments.GLUTTONY);
        float owned = standing.spentTo(player.getAbsorptionAmount()).overheal();
        player.setAbsorptionAmount(Math.max(0f, player.getAbsorptionAmount() - owned));
        AttributeInstance maxAbsorption = player.getAttribute(Attributes.MAX_ABSORPTION);
        if (maxAbsorption != null) {
            maxAbsorption.removeModifier(OVERHEAL_ID);
        }
        player.setData(GooAttachments.GLUTTONY, Gluttony.NONE);
    }

    private static Gluttony feed(ServerPlayer player, Gluttony gluttony, long now) {
        FoodData food = player.getFoodData();
        Gluttony fed = gluttony;
        if (fed.refills(!food.needsFood())) {
            food.eat(FOOD_PER_POINT, NO_SATURATION);
            fed = fed.refilled();
        }
        if (!fed.feeds(now)) {
            return fed;
        }
        Gluttony.Landing landing = fed.land(!food.needsFood(), player.getHealth() >= player.getMaxHealth());
        if (landing.eats()) {
            food.eat(FOOD_PER_POINT, NO_SATURATION);
        }
        if (landing.heals()) {
            player.heal(Gluttony.HEALTH_PER_POINT);
        }
        float grown = landing.after().overheal() - fed.overheal();
        if (grown > 0f) {
            player.setAbsorptionAmount(player.getAbsorptionAmount() + grown);
        }
        return landing.after();
    }

    private static void lendMaxAbsorption(ServerPlayer player, int maxOverheal) {
        AttributeInstance maxAbsorption = player.getAttribute(Attributes.MAX_ABSORPTION);
        if (maxAbsorption != null && !maxAbsorption.hasModifier(OVERHEAL_ID)) {
            maxAbsorption.addTransientModifier(new AttributeModifier(OVERHEAL_ID, maxOverheal,
                    AttributeModifier.Operation.ADD_VALUE));
        }
    }
}
