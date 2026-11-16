package com.mercuriusxeno.goo.ability.frost;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.RimeStep;
import com.mercuriusxeno.goo.block.ability.CellBinding;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import java.util.Optional;

/**
 * Sends back, every second, each block Rime froze that no Rime cell stands in
 * reach of any longer, by the reach Rime's step names; where Rime's ability
 * does not load, every block it froze thaws.
 * rime-freezes-water-and-lava-and-freezes-and-damages-the-shoeless
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class RimeThawEvents {

    /** Ticks between the passes that thaw what no Rime cell holds frozen. */
    static final int THAW_EVERY_TICKS = 20;

    private RimeThawEvents() {
    }

    /**
     * Thaws, once a second, each block Rime froze that no Rime cell holds.
     *
     * @param event the level tick event
     */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && level.getGameTime() % THAW_EVERY_TICKS == 0) {
            int reach = Optional.ofNullable(AbilityRegistry.of(level).getAbility(CellBinding.RIME_ABILITY))
                    .map(AbilityDefinition::behaviors).flatMap(RimeStep::in).map(RimeStep::reach).orElse(0);
            RimeFrozenBlocks.of(level).thawUnheld(level, reach);
        }
    }
}
