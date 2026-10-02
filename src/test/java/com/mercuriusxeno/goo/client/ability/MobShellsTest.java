package com.mercuriusxeno.goo.client.ability;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.SheepRenderState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

/**
 * A sheep's splat sits on the wool it shows, its age's wool until it is
 * sheared, and on its body after.
 */
class MobShellsTest {

    private final EntityModel<?> adultWool = mock(EntityModel.class);
    private final EntityModel<?> babyWool = mock(EntityModel.class);

    @Test
    void woollySheepShowsItsWool() {
        assertSame(adultWool, MobShells.woolShown(new SheepRenderState(), adultWool, babyWool));
    }

    @Test
    void woollyLambShowsTheBabyWool() {
        SheepRenderState lamb = new SheepRenderState();
        lamb.isBaby = true;

        assertSame(babyWool, MobShells.woolShown(lamb, adultWool, babyWool));
    }

    @Test
    void shearedSheepShowsNoWool() {
        SheepRenderState sheared = new SheepRenderState();
        sheared.isSheared = true;

        assertNull(MobShells.woolShown(sheared, adultWool, babyWool));
    }

    @Test
    void mobThatIsNoSheepShowsNoWool() {
        assertNull(MobShells.woolShown(new LivingEntityRenderState(), adultWool, babyWool));
    }
}
