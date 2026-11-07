package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.held.HeldEffects;
import com.mercuriusxeno.goo.ability.held.HeldEffectsEvents;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooMobEffects;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Gametest for a held effect saved under an ability the registry no longer
 * holds, as a save from before deep's dive took the name Sculker carries:
 * the player's next tick ends it and takes its brew effect, and its missing
 * icon, off the effect list (decision dive-is-renamed-sculker).
 */
@RegistersGameTests
public final class StaleHeldEffectTests {
    private static final String STALE_HELD_ENDS = "stale_held_effect_ends_on_the_next_tick";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, STALE_HELD_ENDS, StaleHeldEffectTests::staleHeldEffectEndsOnTheNextTick);
    }

    /** Deep's brew as it was named before the rename, an ability no longer shipped. */
    private static final Identifier DEEP_DIVE = Identifier.parse("goo:deep_dive");
    private static final String SHOULD_END = "The held effect for the removed %s should end, held %b, brew effect shown %b";

    private StaleHeldEffectTests() {
    }

    /**
     * A player carrying a held effect for goo:deep_dive, with deep's brew
     * effect shown, ticks once and holds neither.
     *
     * @param helper the gametest helper
     */
    public static void staleHeldEffectEndsOnTheNextTick(GameTestHelper helper) {
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.DEEP);
        HeldEffects stale = player.getData(GooAttachments.HELD_EFFECTS)
                .start(new HeldEffects.Held(DEEP_DIVE, GooTypes.DEEP, 1, Set.of(), 0L)).after();
        player.setData(GooAttachments.HELD_EFFECTS, stale);
        player.addEffect(new MobEffectInstance(GooMobEffects.BREW_EFFECTS.get(GooTypes.DEEP), MobEffectInstance.INFINITE_DURATION));
        player.doTick();
        boolean held = HeldEffectsEvents.holds(player, DEEP_DIVE);
        boolean shown = player.hasEffect(GooMobEffects.BREW_EFFECTS.get(GooTypes.DEEP));
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(!held && !shown, String.format(SHOULD_END, DEEP_DIVE, held, shown));
        helper.succeed();
    }
}
