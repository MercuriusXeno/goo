package com.mercuriusxeno.goo.command;

import com.mercuriusxeno.goo.Goo;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.ModFileScanData;
import org.objectweb.asm.Type;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.reflect.InvocationTargetException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Finds every {@link AuditRule} class, so the audit walks whatever rules stand and a new concern is one class
 * (decision each-check-is-one-rule-added-alone). A running game reads FML's annotation scan, as
 * GooTestFunctions finds its RegistersGameTests classes; with no mod list loaded, as in a unit test, the rule
 * package's classes are read off the classpath.
 */
public final class GooAuditRules {
    private static final String RULE_PACKAGE = GooAuditRules.class.getPackageName();
    private static final String PACKAGE_PATH = RULE_PACKAGE.replace('.', '/');
    private static final String CLASS_SUFFIX = ".class";
    private static final String FILE_PROTOCOL = "file";
    private static final String JAR_PROTOCOL = "jar";
    private static final String PACKAGE_PREFIX = PACKAGE_PATH + "/";
    private static final String LOG_UNREADABLE = "The audit rules could not be read off the classpath: {}";
    private static final String LOG_NOT_A_RULE = "{} is marked AuditRule but is no GooAuditRule; the audit leaves it out";
    private static final String LOG_NO_CONSTRUCTOR =
            "{} is marked AuditRule but holds no public no-argument constructor; the audit leaves it out: {}";

    private GooAuditRules() {
    }

    /**
     * @return every discovered rule, in its declared order, then by class name
     */
    public static List<GooAuditRule> discovered() {
        List<Class<?>> marked = new ArrayList<>();
        for (String className : ruleClassNames()) {
            Class<?> type = load(className);
            if (type != null && type.isAnnotationPresent(AuditRule.class)) {
                marked.add(type);
            }
        }
        marked.sort(Comparator.comparingInt((Class<?> type) -> type.getAnnotation(AuditRule.class).order())
                .thenComparing(Class::getName));
        return marked.stream().map(GooAuditRules::instantiate).flatMap(Optional::stream).toList();
    }

    private static List<String> ruleClassNames() {
        ModList mods = ModList.get();
        if (mods != null) {
            return mods.getAllScanData().stream()
                    .flatMap(scan -> scan.getAnnotatedBy(AuditRule.class, ElementType.TYPE))
                    .map(ModFileScanData.AnnotationData::clazz)
                    .map(Type::getClassName)
                    .distinct()
                    .toList();
        }
        return classpathClassNames();
    }

    private static List<String> classpathClassNames() {
        List<String> names = new ArrayList<>();
        try {
            for (URL root : Collections.list(GooAuditRules.class.getClassLoader().getResources(PACKAGE_PATH))) {
                if (FILE_PROTOCOL.equals(root.getProtocol())) {
                    names.addAll(directoryClassNames(Path.of(root.toURI())));
                } else if (JAR_PROTOCOL.equals(root.getProtocol())) {
                    names.addAll(jarClassNames(root));
                }
            }
        } catch (IOException | URISyntaxException e) {
            Goo.LOGGER.error(LOG_UNREADABLE, e.getMessage());
        }
        return names;
    }

    private static List<String> directoryClassNames(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.map(file -> file.getFileName().toString()).filter(name -> name.endsWith(CLASS_SUFFIX))
                    .map(name -> RULE_PACKAGE + '.' + name.substring(0, name.length() - CLASS_SUFFIX.length()))
                    .toList();
        }
    }

    private static List<String> jarClassNames(URL root) throws IOException {
        JarURLConnection connection = (JarURLConnection) root.openConnection();
        try (JarFile jar = connection.getJarFile()) {
            return jar.stream().map(JarEntry::getName)
                    .filter(name -> name.startsWith(PACKAGE_PREFIX) && name.endsWith(CLASS_SUFFIX)
                            && name.indexOf('/', PACKAGE_PREFIX.length()) < 0)
                    .map(name -> name.substring(0, name.length() - CLASS_SUFFIX.length()).replace('/', '.'))
                    .toList();
        }
    }

    private static Class<?> load(String className) {
        try {
            return Class.forName(className, false, GooAuditRules.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    /**
     * A marked class that is no rule, or that holds no public no-argument constructor, is logged and left out,
     * so one broken rule leaves the rest of the audit standing.
     *
     * @param type the marked class
     * @return the rule, or empty where the class makes none
     */
    private static Optional<GooAuditRule> instantiate(Class<?> type) {
        if (!GooAuditRule.class.isAssignableFrom(type)) {
            Goo.LOGGER.error(LOG_NOT_A_RULE, type.getName());
            return Optional.empty();
        }
        try {
            return Optional.of((GooAuditRule) type.getConstructor().newInstance());
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException
                 | InvocationTargetException e) {
            Goo.LOGGER.error(LOG_NO_CONSTRUCTOR, type.getName(), e.getMessage());
            return Optional.empty();
        }
    }
}
