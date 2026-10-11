package com.mercuriusxeno.goo;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.FMLLoader;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mockStatic;

/**
 * Bootstraps Minecraft once per test JVM, before any test class runs, under the
 * FML loader mock the bootstrapping test classes use. NeoForge's FeatureFlags
 * initializer asks FMLLoader for the current loader, and a class initializer
 * that fails stays failed for the JVM: a test class mocking Level or
 * MinecraftServer before a bootstrapping class ran poisoned every later class,
 * and Gradle runs previously failed classes first, so one red test cascaded.
 */
public final class MinecraftBootstrapSession implements LauncherSessionListener {

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        try (MockedStatic<FMLLoader> loader = mockStatic(FMLLoader.class, RETURNS_DEEP_STUBS)) {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
        }
    }
}
