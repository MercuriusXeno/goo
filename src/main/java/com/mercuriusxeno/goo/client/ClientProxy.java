package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.ISidedProxy;
import com.mercuriusxeno.goo.ability.SelfEatRoute;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.client.ability.HunkCrumbles;
import com.mercuriusxeno.goo.client.knownitems.KnownItemsOverlay;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.sound.FadingLoops;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import com.mercuriusxeno.goo.entity.BlockHunk;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Client-side proxy implementation. References {@link Minecraft} directly;
 * only classloaded on the client dist.
 */
public final class ClientProxy implements ISidedProxy {

    @Override
    public @Nullable HitResult getCrosshairHit() {
        return Minecraft.getInstance().hitResult;
    }

    @Override
    public void pressGlove(InteractionHand hand) {
        GloveUseTracker.pressGlove(hand);
    }

    @Override
    public void hunkShattered(BlockHunk hunk) {
        HunkCrumbles.begin(hunk);
    }

    @Override
    public void keepLoopAlive(Object key, Identifier sound, SoundSource source, float volume, Vec3 at) {
        FadingLoops.keepAlive(key, sound, source, volume, 1f, at);
    }

    @Override
    public boolean syncedAbilityEats(String abilityId) {
        ClientAbility ability = AbilitySyncHandler.findAbility(abilityId);
        return ability != null && SelfEatRoute.eats(ability.delivery(), ability.badge());
    }

    @Override
    public List<Step> syncedAbilityProgram(String abilityId) {
        ClientAbility ability = AbilitySyncHandler.findAbility(abilityId);
        return ability == null ? List.of() : ability.behaviors();
    }

    @Override
    public void openPlexerMenu(BlockPos plexer) {
        KnownItemsOverlay.open(plexer);
    }
}
