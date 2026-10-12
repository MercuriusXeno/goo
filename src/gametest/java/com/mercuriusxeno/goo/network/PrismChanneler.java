package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Holds a channeled stream on a prism through the stream handler's real
 * path, as a player holding the glove on it does, so a gametest converts a
 * bare prism the way play does.
 * channeled-hosts-convert-prisms-over-the-hold
 */
public final class PrismChanneler {

    private static final String ABILITY_REQUIRED = "The ability registry must hold %s";

    private PrismChanneler() {
    }

    /**
     * The hold a stream's conversion takes: one charge's ticks, as its JSON names them.
     *
     * @param helper  the gametest helper
     * @param ability the stream ability
     * @return the ticks of unbroken stream that convert a prism
     */
    public static int hold(GameTestHelper helper, Identifier ability) {
        AbilityDefinition stream = AbilityRegistry.of(helper.getLevel()).getAbility(ability);
        helper.assertTrue(stream != null, String.format(ABILITY_REQUIRED, ability));
        return stream.delivery().ticksPerCharge();
    }

    /**
     * Stands a player holding a glove and the type's goo, taught the stream,
     * looking at the prism's middle, and sends one stream tick a game tick
     * for a run of ticks. The caller removes the player once done.
     *
     * @param helper  the gametest helper
     * @param type    the stream's goo type
     * @param ability the stream ability
     * @param prism   the prism, relative to the test
     * @param stand   where the player stands, relative to the test
     * @param ticks   the ticks to hold, from the first
     * @return the holding player
     */
    public static ServerPlayer holdOn(GameTestHelper helper, ResourceKey<GooTypeDefinition> type, Identifier ability,
                                      BlockPos prism, BlockPos stand, int ticks) {
        ServerPlayer player = SelfDeliveryTests.invoker(helper, type);
        KnownRecipes.teachRequires(player, AbilityRegistry.of(helper.getLevel()).getAbility(ability));
        Vec3 feet = Vec3.atBottomCenterOf(helper.absolutePos(stand));
        player.setPos(feet.x, feet.y, feet.z);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(helper.absolutePos(prism)));
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(type), ability.toString(),
                player.getEyePosition(), player.getEyePosition());
        for (int at = 1; at <= ticks; at++) {
            helper.runAfterDelay(at, () -> GooStreamHandler.streamTick(player, tick));
        }
        return player;
    }
}
