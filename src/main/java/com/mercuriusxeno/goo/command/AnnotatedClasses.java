package com.mercuriusxeno.goo.command;

import com.mercuriusxeno.goo.Goo;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.ModFileScanData;
import org.objectweb.asm.Type;
import java.io.IOException;
import java.lang.annotation.Annotation;
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
import java.util.function.ToIntFunction;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Finds and builds every class a marker annotation marks, so a seam such as the audit's rules or the
 * workbench's checks walks whatever classes stand and a new one is one class (decision
 * each-check-is-one-rule-added-alone). A running game reads FML's annotation scan, as GooTestFunctions finds
 * its RegistersGameTests classes; with no mod list loaded, as in a unit test or the gradle-run workbench, the
 * seam's package is read off the classpath.
 */
public final class AnnotatedClasses {
    private static final String CLASS_SUFFIX = ".class";
    private static final String FILE_PROTOCOL = "file";
    private static final String JAR_PROTOCOL = "jar";
    private static final String LOG_UNREADABLE = "The {} classes could not be read off the classpath: {}";
    private static final String LOG_NOT_AN_INSTANCE = "{} is marked {} but is no {}; it is left out";
    private static final String LOG_NO_CONSTRUCTOR =
            "{} is marked {} but holds no public no-argument constructor; it is left out: {}";

    private AnnotatedClasses() {
    }

    /**
     * @param marker      the annotation marking each class
     * @param type        the type each marked class implements
     * @param packageName the package the classpath read walks, where no mod list is loaded
     * @param order       each marked class's place, lowest first, ties broken by class name
     * @param <T>         the type built
     * @return one instance of every marked class, in order
     */
    public static <T> List<T> instances(Class<? extends Annotation> marker, Class<T> type, String packageName,
                                        ToIntFunction<Class<?>> order) {
        List<Class<?>> marked = new ArrayList<>();
        for (String className : classNames(marker, packageName)) {
            Class<?> candidate = load(className);
            if (candidate != null && candidate.isAnnotationPresent(marker)) {
                marked.add(candidate);
            }
        }
        marked.sort(Comparator.comparingInt(order).thenComparing(Class::getName));
        return marked.stream().map(candidate -> instantiate(candidate, marker, type)).flatMap(Optional::stream)
                .toList();
    }

    private static List<String> classNames(Class<? extends Annotation> marker, String packageName) {
        ModList mods = ModList.get();
        if (mods != null) {
            return mods.getAllScanData().stream()
                    .flatMap(scan -> scan.getAnnotatedBy(marker, ElementType.TYPE))
                    .map(ModFileScanData.AnnotationData::clazz)
                    .map(Type::getClassName)
                    .distinct()
                    .toList();
        }
        return classpathClassNames(packageName);
    }

    private static List<String> classpathClassNames(String packageName) {
        String packagePath = packageName.replace('.', '/');
        List<String> names = new ArrayList<>();
        try {
            for (URL root : Collections.list(AnnotatedClasses.class.getClassLoader().getResources(packagePath))) {
                if (FILE_PROTOCOL.equals(root.getProtocol())) {
                    names.addAll(directoryClassNames(Path.of(root.toURI()), packageName));
                } else if (JAR_PROTOCOL.equals(root.getProtocol())) {
                    names.addAll(jarClassNames(root, packagePath + '/'));
                }
            }
        } catch (IOException | URISyntaxException e) {
            Goo.LOGGER.error(LOG_UNREADABLE, packageName, e.getMessage());
        }
        return names;
    }

    private static List<String> directoryClassNames(Path directory, String packageName) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.map(file -> file.getFileName().toString()).filter(name -> name.endsWith(CLASS_SUFFIX))
                    .map(name -> packageName + '.' + name.substring(0, name.length() - CLASS_SUFFIX.length()))
                    .toList();
        }
    }

    private static List<String> jarClassNames(URL root, String packagePrefix) throws IOException {
        JarURLConnection connection = (JarURLConnection) root.openConnection();
        try (JarFile jar = connection.getJarFile()) {
            return jar.stream().map(JarEntry::getName)
                    .filter(name -> name.startsWith(packagePrefix) && name.endsWith(CLASS_SUFFIX)
                            && name.indexOf('/', packagePrefix.length()) < 0)
                    .map(name -> name.substring(0, name.length() - CLASS_SUFFIX.length()).replace('/', '.'))
                    .toList();
        }
    }

    private static Class<?> load(String className) {
        try {
            return Class.forName(className, false, AnnotatedClasses.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    /**
     * A marked class of the wrong type, or holding no public no-argument constructor, is logged and left out,
     * so one broken class leaves the rest of the seam standing.
     *
     * @param candidate the marked class
     * @param marker    the annotation marking it
     * @param type      the type it must implement
     * @param <T>       the type built
     * @return the instance, or empty where the class makes none
     */
    private static <T> Optional<T> instantiate(Class<?> candidate, Class<? extends Annotation> marker,
                                               Class<T> type) {
        if (!type.isAssignableFrom(candidate)) {
            Goo.LOGGER.error(LOG_NOT_AN_INSTANCE, candidate.getName(), marker.getSimpleName(), type.getSimpleName());
            return Optional.empty();
        }
        try {
            return Optional.of(type.cast(candidate.getConstructor().newInstance()));
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException
                 | InvocationTargetException e) {
            Goo.LOGGER.error(LOG_NO_CONSTRUCTOR, candidate.getName(), marker.getSimpleName(), e.getMessage());
            return Optional.empty();
        }
    }
}
