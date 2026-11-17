package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import com.mercuriusxeno.goo.client.throwing.JavelinTurn;
import com.mercuriusxeno.goo.network.StackFormedPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.HashMap;
import java.util.Map;

/**
 * Dart's stacked javelins on this client, as the server counts them for each
 * caster: the local player's left click throws only while one stands formed,
 * and the shink plays as each of the local player's javelins forms.
 * decision dart-stacks-three-javelins-one-per-ten-ticks
 * decision dart-shinks-as-it-forms-and-pitched-down-as-it-reverts
 */
public final class HeldJavelins {

    /** Each caster's javelins formed, by entity id. */
    private static final Map<Integer, Integer> FORMED = new HashMap<>();

    private HeldJavelins() {
    }

    /**
     * Takes the count the server sent for a caster.
     *
     * @param payload the formed count
     * @param context the network context
     */
    public static void onPayload(StackFormedPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> formed(payload.casterId(), payload.formed()));
    }

    /**
     * Records a caster's formed count, shinking for each new javelin of the local player's.
     *
     * @param casterId the caster's entity id
     * @param formed   the javelins formed, 0 once the hold ends
     */
    static void formed(int casterId, int formed) {
        Integer was = formed > 0 ? FORMED.put(casterId, formed) : FORMED.remove(casterId);
        int before = was == null ? 0 : was;
        LocalPlayer local = Minecraft.getInstance().player;
        if (local != null && local.getId() == casterId && formed > before) {
            GloveUseTracker.shink(local, JavelinTurn.FORMS);
        }
    }

    /**
     * The javelins a caster holds formed, as the server last counted them.
     *
     * @param casterId the caster's entity id
     * @return the javelins, 0 for a caster holding none
     */
    public static int formedBy(int casterId) {
        return FORMED.getOrDefault(casterId, 0);
    }

    /** Drops every count, as a disconnect does. */
    public static void clear() {
        FORMED.clear();
    }
}
