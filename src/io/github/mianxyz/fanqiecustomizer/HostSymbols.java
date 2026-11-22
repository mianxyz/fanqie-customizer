package io.github.mianxyz.fanqiecustomizer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Central, evidence-fed symbol resolver. No Android or Xposed dependency.
 * Register generated, DEX-reviewed maps before initialize. Identity entries are
 * meaningful evidence; absence is never an obfuscated-name fallback.
 */
public final class HostSymbols {
    private static final String SKIP = "io.github.mianxyz.fanqiecustomizer.__missing_host_symbol__.";
    private static final Map<String, Profile> PROFILES = new HashMap<>();
    private static final Map<String, Map<String, Boolean>> STATIC_SPECS = new HashMap<>();
    private static final Map<String, Map<String, String[]>> CLASS_SPECS = new HashMap<>();
    private static volatile State state;
    private HostSymbols() {}

    /** Map keys: classes/resources = canonical name; members = canonicalOwner#oldName.
     * Exact method keys may append JVM parameter descriptors, e.g. #a(I), or
     * a full JVM descriptor, e.g. #a(I)V. Fields may append :descriptor.
     * Values are actual names (member values contain the name only).
     */
    public static synchronized void register(String packageName, long code,
            Map<String, String> classes, Map<String, String> methods,
            Map<String, String> fields, Map<String, String> resources) {
        String key = profileKey(packageName, code);
        if (state != null) throw new IllegalStateException("HostSymbols already initialized");
        if (PROFILES.containsKey(key)) throw new IllegalArgumentException("Duplicate profile " + key);
        PROFILES.put(key, new Profile(classes, methods, fields, resources));
    }

    /** Optional generated validation rows: M/F, canonicalOwner, oldName,
     * canonicalDescriptor, actualOwner, actualName, actualDescriptor, static.
     * Class rows may coexist and are reserved for producer-side shape checks.
     */
    public static synchronized void registerValidation(String packageName, long code, String[][] rows) {
        if (state != null) throw new IllegalStateException("HostSymbols already initialized");
        Map<String, Boolean> specs = new HashMap<>();
        Map<String, String[]> classSpecs = new HashMap<>();
        for (String[] row : rows) {
            if (row.length >= 5 && "C".equals(row[0])) { classSpecs.put(row[1], row.clone()); continue; }
            if (row.length == 0 || !("M".equals(row[0]) || "F".equals(row[0]))) continue;
            if (row.length < 8 || !("true".equals(row[7]) || "false".equals(row[7])))
                throw new IllegalArgumentException("Invalid static shape row");
            String key = row[1] + "#" + row[2] + ("F".equals(row[0]) ? ":" : "") + row[3];
            Boolean prior = specs.put(key, Boolean.valueOf(row[7]));
            if (prior != null && !prior.equals(Boolean.valueOf(row[7])))
                throw new IllegalArgumentException("Conflicting static shape " + key);
        }
        STATIC_SPECS.put(profileKey(packageName, code), Collections.unmodifiableMap(specs));
        CLASS_SPECS.put(profileKey(packageName, code), Collections.unmodifiableMap(classSpecs));
    }

    /** The first call wins for this process. Baseline performs no profile work or logging. */
    public static synchronized void initialize(String packageName, long code,
            ClassLoader loader, Consumer<String> log) {
        if (state != null) return;
        boolean baseline = "com.dragon.read".equals(packageName) && code == 73932L;
        String key = profileKey(packageName, code);
        State next = new State(key, baseline, baseline ? null : PROFILES.get(key), loader, log);
        state = next;
        if (!baseline && next.profile == null) next.logOnce("profile", "unknown profile; obfuscated symbols skipped");
    }

    public static boolean isBaseline() {
        State s = state;
        return s == null || s.baseline;
    }

    /** Atomic preflight: full JVM descriptors validate member shape; simple
     * owner#name keys validate declaration presence only, leaving exact shape
     * checks to the caller. This runs before a caller installs its hooks.
     * Empty dependencies are not evidence for a nonbaseline capability.
     */
    public static boolean requireCapability(String module, String[] classes,
            String[] methods, String[] fields, String[] resources) {
        if (isBaseline()) return true;
        State s = state;
        Objects.requireNonNull(module, "module");
        try {
            if (s.profile == null) throw new IllegalArgumentException("unknown profile");
            int count = classes.length + methods.length + fields.length + resources.length;
            if (count == 0) throw new IllegalArgumentException("empty dependency inventory");
            for (String cls : classes) type(cls, s.loader);
            for (String key : methods) {
                int hash = key.indexOf('#'), start = key.indexOf('(', hash + 1), end = key.indexOf(')', start + 1);
                if (hash < 1) throw new IllegalArgumentException("method owner required " + key);
                if (start < 0) {
                    Class<?> cls = type(key.substring(0, hash), s.loader);
                    String mapped = s.profile.methods.get(key);
                    if (mapped == null) throw new IllegalArgumentException("missing method " + key);
                    boolean found = false;
                    for (Method method : cls.getDeclaredMethods()) if (method.getName().equals(mapped)) { found = true; break; }
                    if (!found) throw new NoSuchMethodException(key);
                    continue;
                }
                if (end < 0 || end == key.length() - 1)
                    throw new IllegalArgumentException("complete method descriptor required " + key);
                String owner = key.substring(0, hash), old = key.substring(hash + 1, start);
                Class<?> cls = type(owner, s.loader);
                String mapped = s.profile.methods.get(key);
                if (mapped == null) throw new IllegalArgumentException("missing method " + key);
                boolean found = false;
                for (Method method : cls.getDeclaredMethods()) {
                    if (!method.getName().equals(mapped)) continue;
                    StringBuilder desc = new StringBuilder("(");
                    for (Class<?> param : method.getParameterTypes()) desc.append(descriptor(s, param));
                    desc.append(')').append(descriptor(s, method.getReturnType()));
                    if (desc.toString().equals(key.substring(start)) && staticMatches(s, key, method.getModifiers())) { found = true; break; }
                }
                if (!found) throw new NoSuchMethodException(owner + "#" + old);
            }
            for (String key : fields) {
                int hash = key.indexOf('#'), colon = key.indexOf(':', hash + 1);
                if (hash < 1) throw new IllegalArgumentException("field owner required " + key);
                if (colon < 0) {
                    Class<?> cls = type(key.substring(0, hash), s.loader);
                    String mapped = s.profile.fields.get(key);
                    if (mapped == null) throw new IllegalArgumentException("missing field " + key);
                    cls.getDeclaredField(mapped);
                    continue;
                }
                Class<?> cls = type(key.substring(0, hash), s.loader);
                String mapped = s.profile.fields.get(key);
                if (mapped == null) throw new IllegalArgumentException("missing field " + key);
                Field field = cls.getDeclaredField(mapped);
                if (!descriptor(s, field.getType()).equals(key.substring(colon + 1)) || !staticMatches(s, key, field.getModifiers()))
                    throw new NoSuchFieldException(key);
            }
            for (String resource : resources)
                if (!s.profile.resources.containsKey(resource)) throw new IllegalArgumentException("missing resource " + resource);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            s.logOnce("capability:" + module, "capability " + module + " skip (" + error.getClass().getSimpleName() + ": " + error.getMessage() + ")");
            return false;
        }
    }

    public static String name(String canonical) {
        Objects.requireNonNull(canonical, "canonical");
        State s = state;
        if (s == null || s.baseline) return canonical;
        if ("void".equals(canonical) || "boolean".equals(canonical) || "byte".equals(canonical)
                || "short".equals(canonical) || "char".equals(canonical) || "int".equals(canonical)
                || "long".equals(canonical) || "float".equals(canonical) || "double".equals(canonical)) return canonical;
        // Class.getName() array notation, not source-language T[] notation.
        if (canonical.startsWith("[")) {
            int depth = 0;
            while (depth < canonical.length() && canonical.charAt(depth) == '[') depth++;
            String component = canonical.substring(depth);
            if (component.length() == 1 && "ZBSCIJFD".contains(component)) return canonical;
            if (component.startsWith("L") && component.endsWith(";") && component.length() > 2) {
                String mapped = name(component.substring(1, component.length() - 1));
                if (!mapped.startsWith(SKIP)) return canonical.substring(0, depth) + "L" + mapped + ";";
            }
            return skip(s, "class", canonical);
        }
        String actual = s.profile == null ? null : s.profile.classes.get(canonical);
        if (actual != null) return actual;
        if (stableClass(canonical)) return canonical;
        return skip(s, "class", canonical);
    }

    public static Class<?> type(String canonical, ClassLoader loader) throws ClassNotFoundException {
        // Preserve Class.forName(..., false, loader): never initialize the target.
        String actual = name(canonical);
        if (actual.startsWith(SKIP)) throw new ClassNotFoundException(actual);
        try {
            Class<?> found = Class.forName(actual, false, loader);
            State s = state;
            Map<String, String[]> specs = s == null || s.baseline ? null : CLASS_SPECS.get(s.key);
            String[] shape = specs == null ? null : specs.get(canonical);
            if (shape != null) {
                String parent = found.isInterface() ? "java.lang.Object"
                        : found.getSuperclass() == null ? "" : found.getSuperclass().getName();
                Set<String> interfaces = new HashSet<>();
                for (Class<?> iface : found.getInterfaces()) interfaces.add(iface.getName());
                Set<String> expectedInterfaces = new HashSet<>();
                if (!shape[4].isEmpty()) Collections.addAll(expectedInterfaces, shape[4].split(","));
                if (!found.getName().equals(shape[2]) || !parent.equals(shape[3]) || !interfaces.equals(expectedInterfaces))
                    throw new ClassNotFoundException("Class shape mismatch " + canonical);
            }
            return found;
        }
        catch (ClassNotFoundException | LinkageError error) {
            failure("class", canonical, error);
            throw error;
        }
    }

    public static Class<?> type(String canonical) throws ClassNotFoundException {
        State s = state;
        return type(canonical, s == null ? HostSymbols.class.getClassLoader() : s.loader);
    }

    public static String resourceName(String canonical) {
        Objects.requireNonNull(canonical, "canonical");
        State s = state;
        if (s == null || s.baseline) return canonical;
        String actual = s.profile == null ? null : s.profile.resources.get(canonical);
        return actual != null ? actual : skip(s, "resource", canonical);
    }

    public static String canonicalResource(String actual) {
        Objects.requireNonNull(actual, "actual");
        State s = state;
        if (s == null || s.baseline) return actual;
        String canonical = s.profile == null ? null : s.profile.reverseResources.get(actual);
        if (canonical != null) return canonical;
        s.logOnce("reverse-resource", "unmapped actual resources skipped");
        return SKIP + "reverse_resource." + actual;
    }

    /** Exact class/resource literal conversion, never substring replacement.
     * Other text (labels, enum constants) is retained. Resource-like lower-case
     * identifiers use the explicit resource map; callers can use resourceName
     * for resource names outside this conventional shape.
     */
    public static String literal(String canonical) {
        Objects.requireNonNull(canonical, "canonical");
        State s = state;
        if (s == null || s.baseline) return canonical;
        if (s.profile != null) {
            String cls = s.profile.classes.get(canonical);
            String res = s.profile.resources.get(canonical);
            if (cls != null) return cls;
            if (res != null) return res;
        }
        if (canonical.indexOf('.') >= 0 && canonical.indexOf(' ') < 0) return name(canonical);
        if (canonical.matches("[a-z][a-z0-9_]*")) return resourceName(canonical);
        return canonical;
    }

    public static String canonical(String actual) {
        Objects.requireNonNull(actual, "actual");
        State s = state;
        if (s == null || s.baseline) return actual;
        return canonicalClass(s, actual);
    }

    /** Optional diagnostics may test registration without emitting missing-symbol logs. */
    public static boolean hasMethodMapping(String owner, String original, String descriptor) {
        State s = state;
        return s == null || s.baseline || s.profile != null
                && s.profile.methods.containsKey(owner + "#" + original + descriptor);
    }

    public static String methodName(String owner, String original) {
        return namedMember(owner, original, true);
    }

    public static String fieldName(String owner, String original) {
        return namedMember(owner, original, false);
    }

    private static String namedMember(String owner, String original, boolean method) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(original, "original");
        State s = state;
        if (s == null || s.baseline) return original;
        String key = canonicalClass(s, owner) + "#" + original;
        String actual = s.profile == null ? null : (method ? s.profile.methods : s.profile.fields).get(key);
        if (actual != null) return actual;
        if (platformClass(owner)) return original;
        return skip(s, method ? "method" : "field", key);
    }

    public static String methodName(Class<?> owner, String original) {
        return memberName(owner, original, null, null, true);
    }

    public static String fieldName(Class<?> owner, String original) {
        return memberName(owner, original, null, null, false);
    }

    /** Public lookup only: inherited declarations are resolved through their real
     * declaring owner. Different mapped names across overloads are ambiguous.
     * Use the parameter overload when the caller has an exact getMethod signature.
     */
    public static String publicMethodName(Class<?> owner, String original) {
        return publicMethodName0(owner, original, null);
    }

    public static String publicMethodName(Class<?> owner, String original, Class<?>... types) {
        return publicMethodName0(owner, original, Objects.requireNonNull(types, "types"));
    }

    private static String publicMethodName0(Class<?> owner, String original, Class<?>[] types) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(original, "original");
        if (isBaseline()) return original;
        State s = state;
        Set<String> names = new HashSet<>();
        try {
            for (Method method : owner.getMethods()) {
                if (types != null && !java.util.Arrays.equals(types, method.getParameterTypes())) continue;
                boolean verified = platformClass(method.getDeclaringClass().getName())
                        ? method.getName().equals(original) : publicCandidateMatches(s, method, original);
                if (!verified) continue;
                Method visible = owner.getMethod(method.getName(), method.getParameterTypes());
                if (visible.equals(method)) names.add(method.getName());
            }
            if (names.size() == 1) return names.iterator().next();
        } catch (ReflectiveOperationException | LinkageError error) {
            failure("public-method", owner.getName() + "#" + original, error);
        }
        return skip(s, "public-method", owner.getName() + "#" + original);
    }

    private static boolean publicCandidateMatches(State s, Method method, String original) {
        if (s.profile == null) return false;
        StringBuilder desc = new StringBuilder("(");
        for (Class<?> parameter : method.getParameterTypes()) desc.append(descriptor(s, parameter));
        desc.append(')').append(descriptor(s, method.getReturnType()));
        String key = canonicalClass(s, method.getDeclaringClass().getName()) + "#" + original + desc;
        String expected = s.profile.methods.get(key);
        return expected != null && method.getName().equals(expected)
                && staticMatches(s, key, method.getModifiers());
    }

    /** Public field lookup includes real superclass/interface declarations only. */
    public static String publicFieldName(Class<?> owner, String original) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(original, "original");
        if (isBaseline()) return original;
        State s = state;
        Set<String> names = new HashSet<>();
        try {
            for (Field field : owner.getFields()) {
                boolean verified = platformClass(field.getDeclaringClass().getName())
                        ? field.getName().equals(original) : matches(field, original);
                if (verified && owner.getField(field.getName()).equals(field)) names.add(field.getName());
            }
            if (names.size() == 1) return names.iterator().next();
        } catch (ReflectiveOperationException | LinkageError error) {
            failure("public-field", owner.getName() + "#" + original, error);
        }
        return skip(s, "public-field", owner.getName() + "#" + original);
    }

    /** Full-descriptor predicate for overload iteration; missing evidence is false. */
    public static boolean matches(Method actual, String original) {
        Objects.requireNonNull(actual, "actual");
        Objects.requireNonNull(original, "original");
        State s = state;
        if (s == null || s.baseline) return actual.getName().equals(original);
        StringBuilder desc = new StringBuilder("(");
        try {
            for (Class<?> param : actual.getParameterTypes()) desc.append(descriptor(s, param));
            desc.append(')').append(descriptor(s, actual.getReturnType()));
            String key = canonicalClass(s, actual.getDeclaringClass().getName()) + "#" + original + desc;
            String expected = s.profile == null ? null : s.profile.methods.get(key);
            if (expected == null) { skip(s, "method", key); return false; }
            return actual.getName().equals(expected) && staticMatches(s, key, actual.getModifiers());
        } catch (LinkageError error) { failure("method", original, error); return false; }
    }

    public static boolean matches(Field actual, String original) {
        Objects.requireNonNull(actual, "actual");
        Objects.requireNonNull(original, "original");
        State s = state;
        if (s == null || s.baseline) return actual.getName().equals(original);
        try {
            String key = canonicalClass(s, actual.getDeclaringClass().getName()) + "#" + original + ":" + descriptor(s, actual.getType());
            String expected = s.profile == null ? null : s.profile.fields.get(key);
            if (expected == null) { skip(s, "field", key); return false; }
            return actual.getName().equals(expected) && staticMatches(s, key, actual.getModifiers());
        } catch (LinkageError error) { failure("field", original, error); return false; }
    }

    public static Method method(Class<?> owner, String original, Class<?>... types)
            throws NoSuchMethodException {
        return resolveMethod(owner, original, null, types);
    }

    public static Method methodReturning(Class<?> owner, String original,
            Class<?> expectedReturn, Class<?>... types) throws NoSuchMethodException {
        return resolveMethod(owner, original, Objects.requireNonNull(expectedReturn), types);
    }

    private static Method resolveMethod(Class<?> owner, String original,
            Class<?> expectedReturn, Class<?>[] types) throws NoSuchMethodException {
        Objects.requireNonNull(types, "types");
        String actual = memberName(owner, original, types, expectedReturn, true);
        if (actual.startsWith(SKIP)) throw new NoSuchMethodException(actual);
        try {
            Method found = owner.getDeclaredMethod(actual, types);
            if (expectedReturn != null && found.getReturnType() != expectedReturn)
                throw new NoSuchMethodException(owner.getName() + "#" + actual + " return type mismatch");
            return found;
        } catch (NoSuchMethodException | LinkageError error) {
            failure("method", owner.getName() + "#" + original, error);
            throw error;
        }
    }

    public static Field field(Class<?> owner, String original) throws NoSuchFieldException {
        return fieldOfType(owner, original, null);
    }

    public static Field fieldOfType(Class<?> owner, String original, Class<?> expectedType)
            throws NoSuchFieldException {
        String actual = memberName(owner, original, null, expectedType, false);
        if (actual.startsWith(SKIP)) throw new NoSuchFieldException(actual);
        try {
            Field found = owner.getDeclaredField(actual);
            if (expectedType != null && found.getType() != expectedType)
                throw new NoSuchFieldException(owner.getName() + "#" + actual + " field type mismatch");
            return found;
        } catch (NoSuchFieldException | LinkageError error) {
            failure("field", owner.getName() + "#" + original, error);
            throw error;
        }
    }

    private static String memberName(Class<?> owner, String original, Class<?>[] types,
            Class<?> expected, boolean method) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(original, "original");
        State s = state;
        if (s == null || s.baseline) return original;
        String actualOwner = owner.getName();
        String canonicalOwner = canonicalClass(s, actualOwner);
        String key = canonicalOwner + "#" + original;
        Map<String, String> map = s.profile == null ? Collections.emptyMap()
                : method ? s.profile.methods : s.profile.fields;
        String mapped = null;
        if (method && types != null) {
            StringBuilder parameters = new StringBuilder("(");
            for (Class<?> type : types) parameters.append(descriptor(s, type));
            parameters.append(')');
            String exact = key + parameters;
            if (expected != null) mapped = map.get(exact + descriptor(s, expected));
            if (mapped == null) mapped = map.get(exact);
            // With no return constraint, accept a unique name across full descriptors.
            if (mapped == null && expected == null) mapped = uniqueValue(map, exact);
        } else if (!method && expected != null) {
            mapped = map.get(key + ":" + descriptor(s, expected));
        }
        if (mapped == null) mapped = map.get(key);
        if (mapped != null) return mapped;
        // Platform methods are stable; host obfuscated names require a row even
        // when their declaring class itself is descriptive.
        if (platformClass(actualOwner)) return original;
        return skip(s, method ? "method" : "field", key);
    }

    private static boolean staticMatches(State s, String key, int modifiers) {
        Map<String, Boolean> specs = STATIC_SPECS.get(s.key);
        Boolean expected = specs == null ? null : specs.get(key);
        boolean matches = expected == null || expected == java.lang.reflect.Modifier.isStatic(modifiers);
        if (!matches) s.logOnce("static:" + key, "member skip " + key + " (static shape mismatch)");
        return matches;
    }

    private static String uniqueValue(Map<String, String> map, String prefix) {
        String value = null;
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (!e.getKey().startsWith(prefix) || e.getKey().length() == prefix.length()) continue;
            if (value != null && !value.equals(e.getValue())) return null;
            value = e.getValue();
        }
        return value;
    }

    private static String descriptor(State s, Class<?> type) {
        Objects.requireNonNull(type, "parameter type");
        if (type.isArray()) return "[" + descriptor(s, type.getComponentType());
        if (!type.isPrimitive()) return "L" + canonicalClass(s, type.getName()).replace('.', '/') + ";";
        if (type == void.class) return "V";
        if (type == boolean.class) return "Z";
        if (type == byte.class) return "B";
        if (type == char.class) return "C";
        if (type == short.class) return "S";
        if (type == int.class) return "I";
        if (type == long.class) return "J";
        if (type == float.class) return "F";
        return "D";
    }

    private static String canonicalClass(State s, String actual) {
        if (s.profile == null) return actual;
        String canonical = s.profile.reverseClasses.get(actual);
        return canonical == null ? actual : canonical;
    }

    private static boolean platformClass(String name) {
        return name.startsWith("java.") || name.startsWith("javax.")
                || name.startsWith("android.") || name.startsWith("dalvik.")
                || name.startsWith("org.xml.") || name.startsWith("org.w3c.");
    }

    private static boolean stableClass(String name) {
        if (platformClass(name)) return true;
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        // Descriptive packaged classes remain loadable; short/anonymous nested
        // names and lowercase R8 names require reviewed mapping entries.
        for (String part : name.substring(dot + 1).split("\\$", -1))
            if (part.length() < 4 || !Character.isUpperCase(part.charAt(0))) return false;
        return true;
    }

    private static void failure(String kind, String symbol, Throwable error) {
        State s = state;
        if (s != null && !s.baseline) s.logOnce("shape:" + kind + ":" + symbol,
                kind + " skip " + symbol + " (shape/dependency " + error.getClass().getSimpleName() + ")");
    }

    private static String skip(State s, String kind, String symbol) {
        s.logOnce(kind + ":" + symbol, kind + " skip " + symbol + " (no DEX mapping)");
        return SKIP + kind.replace('-', '_') + "." + symbol;
    }

    private static String profileKey(String packageName, long code) {
        return Objects.requireNonNull(packageName, "packageName") + "#" + code;
    }

    static synchronized void resetForTest() { state = null; PROFILES.clear(); STATIC_SPECS.clear(); CLASS_SPECS.clear(); }
    static synchronized void setForTest(String packageName, long code, ClassLoader loader,
            Consumer<String> log) { state = null; initialize(packageName, code, loader, log); }

    private static Map<String, String> copy(Map<String, String> input) {
        Objects.requireNonNull(input, "mapping");
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, String> e : input.entrySet()) {
            String key = Objects.requireNonNull(e.getKey(), "mapping key");
            String value = Objects.requireNonNull(e.getValue(), "mapping value");
            if (key.isEmpty() || value.isEmpty()) throw new IllegalArgumentException("Empty mapping");
            result.put(key, value);
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, String> reverse(Map<String, String> input) {
        Map<String, String> result = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        for (Map.Entry<String, String> e : input.entrySet()) {
            String prior = result.put(e.getValue(), e.getKey());
            if (prior != null && !prior.equals(e.getKey())) ambiguous.add(e.getValue());
        }
        if (!ambiguous.isEmpty()) throw new IllegalArgumentException("Ambiguous reverse mapping " + ambiguous);
        return Collections.unmodifiableMap(result);
    }

    private static final class Profile {
        final Map<String, String> classes, methods, fields, resources, reverseClasses, reverseResources;
        Profile(Map<String, String> c, Map<String, String> m, Map<String, String> f, Map<String, String> r) {
            classes = copy(c); methods = copy(m); fields = copy(f); resources = copy(r);
            reverseClasses = reverse(classes); reverseResources = reverse(resources);
        }
    }

    private static final class State {
        final String key;
        final boolean baseline;
        final Profile profile;
        final ClassLoader loader;
        final Consumer<String> log;
        final Set<String> logged = ConcurrentHashMap.newKeySet();
        State(String key, boolean baseline, Profile profile, ClassLoader loader, Consumer<String> log) {
            this.key = key; this.baseline = baseline; this.profile = profile;
            this.loader = loader; this.log = log;
        }
        void logOnce(String id, String message) {
            if (log != null && logged.add(id)) {
                try { log.accept("HostSymbols[" + key + "] " + message); }
                catch (RuntimeException ignored) { /* diagnostics never break resolution */ }
            }
        }
    }
}
