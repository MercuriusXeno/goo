package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforgespi.language.ModFileScanData;
import java.lang.annotation.ElementType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Consumer;

/**
 * Registers goo gametest functions via NeoForge's RegisterEvent.
 * TestFunctionLoader.runLoaders() fires during Bootstrap.bootStrap(),
 * before mod loading, so we use RegisterEvent instead to register
 * into the TEST_FUNCTION registry at the correct time. The class lives in
 * the gametest source set, which only the gameTestServer run loads, so a
 * shipped jar registers nothing (decision gametest-and-tools-source-sets).
 * Each gametest class registers its own functions; this class finds them
 * through the annotation scan of the loaded mod classes rather than a list
 * (decision gametests-discovered-not-listed).
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class GooTestFunctions {

    private static final String SMOKE = "smoke";
    private static final String REGISTER_METHOD = "register";

    private GooTestFunctions() {
    }

    /**
     * Registers all gametest functions into the TEST_FUNCTION registry.
     *
     * @param event the register event
     */
    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, registrar -> {
            registerTest(registrar, SMOKE, GameTestHelper::succeed);
            for (String className : registeringClassNames()) {
                callRegister(className, registrar);
            }
        });
    }

    /**
     * The names of every scanned class marked {@link RegistersGameTests}, sorted so
     * registration runs in one order on every boot.
     */
    private static List<String> registeringClassNames() {
        return ModList.get().getAllScanData().stream()
                .flatMap(scan -> scan.getAnnotatedBy(RegistersGameTests.class, ElementType.TYPE))
                .map(ModFileScanData.AnnotationData::clazz)
                .map(type -> type.getClassName())
                .distinct()
                .sorted()
                .toList();
    }

    private static void callRegister(String className,
                                     RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        try {
            Class<?> holder = Class.forName(className, true, GooTestFunctions.class.getClassLoader());
            Method register = holder.getMethod(REGISTER_METHOD, RegisterEvent.RegisterHelper.class);
            register.invoke(null, registrar);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
            throw new IllegalStateException(className + " is marked RegistersGameTests but holds no public static "
                    + REGISTER_METHOD + "(RegisterEvent.RegisterHelper)", e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(className + " failed registering its gametests", e.getCause());
        }
    }

    /**
     * Registers a single test function in the goo namespace.
     *
     * @param registrar the registry registrar
     * @param name      the function name
     * @param fn        the test function
     */
    public static void registerTest(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar,
                                    String name, Consumer<GameTestHelper> fn) {
        registrar.register(Identifier.fromNamespaceAndPath(Goo.MODID, name), fn);
    }
}
