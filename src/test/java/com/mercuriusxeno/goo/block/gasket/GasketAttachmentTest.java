package com.mercuriusxeno.goo.block.gasket;

import com.mercuriusxeno.goo.item.gasket.GasketRole;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The attachment disposes the block-level pusher it stood when the machine releases it
 * (decision diagnose-then-fix-capability-lifetimes). The pusher is a constructed mock, so
 * only the attachment's own bookkeeping runs.
 */
class GasketAttachmentTest {

    /**
     * Mocking a block entity initializes NeoForge's AttachmentHolder, which asks FML whether
     * it runs in production; a stubbed FML loader answers it.
     *
     * @throws ClassNotFoundException never, the class is on the test classpath
     */
    @BeforeAll
    static void initializeBlockEntitySupertypes() throws ClassNotFoundException {
        try (MockedStatic<FMLLoader> loader = mockStatic(FMLLoader.class, RETURNS_DEEP_STUBS)) {
            Class.forName(AttachmentHolder.class.getName(), true, AttachmentHolder.class.getClassLoader());
        }
    }

    @SuppressWarnings("unchecked")
    private static ResourceHandler<FluidResource> fluidSource() {
        return mock(ResourceHandler.class);
    }

    @Nested
    class ReleasePusher {

        @Test
        void disposesThePusherSinglePusherStoodOnce() {
            try (MockedConstruction<GasketPusher> pushers = mockConstruction(GasketPusher.class)) {
                GasketAttachment attachment =
                        GasketAttachment.single(mock(BlockEntity.class), GasketRole.TRANSMITTER, "base");
                attachment.singlePusher(fluidSource());

                attachment.releasePusher();

                verify(pushers.constructed().getFirst(), times(1)).dispose();
            }
        }
    }
}
