package com.mercuriusxeno.goo.ability.gluttony;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Banks a glutton's overflow while Gluttony stands: healing past full health
 * stands as absorption hearts under a max absorption the effect lends and
 * takes back when it ends, and banked overhunger refills hunger as it drops.
 * gluttony-overheals-and-overhungers
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class GluttonyEvents {

    /** The max absorption Gluttony lends for its overheal. */
    static final Identifier OVERHEAL_ID = Identifier.fromNamespaceAndPath(Goo.MODID, "gluttony_overheal");
    /** The food points one banked refill adds. */
    private static final int FOOD_PER_REFILL = 1;
    /** A refill adds hunger, not saturation. */
    private static final float NO_SATURATION = 0f;

    private GluttonyEvents() {
    }

    /**
     * Advances a standing gluttony one tick on the server: banked hunger
     * refills a dropped bar and the gluttony ends at its expiry.
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
        lendMaxAbsorption(player, standing.maxOverheal());
        Gluttony after = refill(player, standing.spentTo(player.getAbsorptionAmount()))
                .tick(player.level().getGameTime());
        if (!after.stands()) {
            end(player);
        } else if (!after.equals(standing)) {
            player.setData(GooAttachments.GLUTTONY, after);
        }
    }

    /**
     * Banks what a heal pushes past a glutton's full health as absorption
     * hearts. Runs last, so a listener cancelling or shrinking the heal acts first.
     *
     * @param event the heal event, before the heal lands
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHeal(LivingHealEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)
                || !player.hasData(GooAttachments.GLUTTONY)) {
            return;
        }
        Gluttony standing = player.getData(GooAttachments.GLUTTONY).spentTo(player.getAbsorptionAmount());
        Gluttony banked = standing.bankHeal(player.getHealth(), player.getMaxHealth(), event.getAmount());
        float grown = banked.overheal() - standing.overheal();
        if (grown <= 0f) {
            return;
        }
        lendMaxAbsorption(player, banked.maxOverheal());
        player.setAbsorptionAmount(player.getAbsorptionAmount() + grown);
        player.setData(GooAttachments.GLUTTONY, banked);
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

    private static Gluttony refill(ServerPlayer player, Gluttony gluttony) {
        FoodData food = player.getFoodData();
        if (!gluttony.refills(!food.needsFood())) {
            return gluttony;
        }
        food.eat(FOOD_PER_REFILL, NO_SATURATION);
        return gluttony.refilled();
    }

    private static void lendMaxAbsorption(ServerPlayer player, int maxOverheal) {
        AttributeInstance maxAbsorption = player.getAttribute(Attributes.MAX_ABSORPTION);
        if (maxAbsorption != null && !maxAbsorption.hasModifier(OVERHEAL_ID)) {
            maxAbsorption.addTransientModifier(new AttributeModifier(OVERHEAL_ID, maxOverheal,
                    AttributeModifier.Operation.ADD_VALUE));
        }
    }
}
