package com.mercuriusxeno.goo.ability.nether;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Nether's Invert on a player: while its held effect stands, a modifier on
 * vanilla's gravity attribute turns the player's gravity over, so the player
 * falls up and the ceiling is its ground. The attribute syncs to the player's
 * client, which moves the player, so both sides read the modifier as the
 * inversion. Saved with the player, it comes off as the held effect ends.
 * invert-flips-your-gravity-and-lets-you-walk-on-ceilings
 * nether-keeps-its-name-and-takes-inversion-as-its-theme
 */
public final class Invert {

    /** The gravity modifier's id. */
    static final Identifier MODIFIER_ID = Identifier.fromNamespaceAndPath(Goo.MODID, "invert_gravity");
    /** Multiplies the total gravity by one less two, turning it over at its own strength. */
    private static final double TURNS_GRAVITY_OVER = -2.0;

    private Invert() {
    }

    /**
     * Turns a living entity's gravity over; laying it again changes nothing.
     *
     * @param target the entity
     */
    public static void lay(LivingEntity target) {
        AttributeInstance gravity = target.getAttribute(Attributes.GRAVITY);
        if (gravity != null) {
            gravity.addOrReplacePermanentModifier(modifier());
        }
    }

    /**
     * Gives a living entity its gravity back.
     *
     * @param target the entity
     */
    public static void clear(LivingEntity target) {
        AttributeInstance gravity = target.getAttribute(Attributes.GRAVITY);
        if (gravity != null) {
            gravity.removeModifier(MODIFIER_ID);
        }
    }

    /**
     * Answers whether an entity's gravity is turned over by Invert. An entity
     * still being built, whose attributes stand on nothing yet, is upright.
     *
     * @param entity the entity
     * @return true while Invert's modifier stands on its gravity
     */
    public static boolean isInverted(Entity entity) {
        if (!(entity instanceof LivingEntity living) || living.getAttributes() == null) {
            return false;
        }
        AttributeInstance gravity = living.getAttribute(Attributes.GRAVITY);
        return gravity != null && gravity.hasModifier(MODIFIER_ID);
    }

    /**
     * The modifier Invert lays.
     *
     * @return the gravity modifier turning gravity over
     */
    static AttributeModifier modifier() {
        return new AttributeModifier(MODIFIER_ID, TURNS_GRAVITY_OVER, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }
}
