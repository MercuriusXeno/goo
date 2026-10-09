package com.mercuriusxeno.goo.ability.oculus;

import com.mercuriusxeno.goo.ability.program.BlinkLanding;
import com.mercuriusxeno.goo.block.ability.PrismBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import java.util.Optional;

/**
 * What a blink to an oculus costs and what it leaves in the oculus: a blink
 * the oculus's charge covers is free and spends that much of the charge; any
 * other blink to it costs its price and adds that price to the charge, so
 * charging an oculus through Blink lets later blinks to it run free.
 * Decision oculus-prism-becomes-a-hovering-eye.
 */
public final class OculusCharge {

    private OculusCharge() {
    }

    /**
     * The goo a blink costs, read against the oculus it snapped to where it
     * snapped to one.
     *
     * @param level the level the oculus stands in, null where none is loaded
     * @param price the blink's price
     * @param trip  the blink's trip, empty where it makes none
     * @return the cost in mB
     */
    public static int costAt(@Nullable Level level, int price, Optional<BlinkLanding> trip) {
        Optional<BlockPos> node = trip.flatMap(BlinkLanding::node);
        return level == null || node.isEmpty() ? price : costOf(price, OculusNodes.chargeAt(level, node.get()));
    }

    /**
     * Settles a blink to an oculus with the oculus: a free blink spends its
     * price from the charge, a paid one adds its price to it.
     *
     * @param level the level the oculus stands in
     * @param price the blink's price
     * @param trip  the blink's trip
     */
    public static void settle(Level level, int price, Optional<BlinkLanding> trip) {
        trip.flatMap(BlinkLanding::node).ifPresent(node -> {
            if (level.getBlockEntity(node) instanceof PrismBlockEntity prism) {
                prism.setCharge(chargeAfter(price, prism.charge()));
            }
        });
    }

    /**
     * The goo a blink to an oculus costs.
     *
     * @param price  the blink's price
     * @param charge the oculus's charge
     * @return zero where the charge covers the price, the price otherwise
     */
    public static int costOf(int price, int charge) {
        return charge >= price ? 0 : price;
    }

    /**
     * The oculus's charge after a blink to it.
     *
     * @param price  the blink's price
     * @param charge the oculus's charge before the blink
     * @return the charge less the price for a free blink, plus the price for a paid one
     */
    public static int chargeAfter(int price, int charge) {
        return charge >= price ? charge - price : charge + price;
    }
}
