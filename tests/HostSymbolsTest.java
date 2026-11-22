package io.github.mianxyz.fanqiecustomizer;

import java.util.*;
import java.util.concurrent.*;

/** Standalone: javac --release 17 -d OUT HostSymbols.java HostSymbolsTest.java. */
public final class HostSymbolsTest {
    private static int checks;
    private static final ClassLoader LOADER = HostSymbolsTest.class.getClassLoader();
    public static class Parent { public void inherited() {} public int parentField; }
    public static class Actual extends Parent {
        public int renamedField;
        public String renamed(int n) { return Integer.toString(n); }
        public long other(String s) { return s.length(); }
        public void array(Actual[] values) {}
    }
    public static class SignatureFixture {
        public void actualVoid(boolean flag, int value) {}
        public boolean actualBoolean(int value) { return value != 0; }
        public int actualInt(boolean flag) { return flag ? 1 : 0; }
    }
    public static class UpdateFixture {
        public void actualUpdate(int mode, Actual payload, boolean enabled) {}
        public boolean actualCheck(Actual payload, long timestamp) { return payload != null; }
    }
    private static int updateHits(String name, String returns, String... types) {
        int installed = 0;
        for (java.lang.reflect.Method method : UpdateFixture.class.getDeclaredMethods()) {
            if (!HostSymbols.matches(method,name) || !HostSymbols.name(returns).equals(method.getReturnType().getName()) || method.getParameterCount()!=types.length) continue;
            boolean match = true;
            Class<?>[] args = method.getParameterTypes();
            for (int i=0;i<types.length;i++) match &= HostSymbols.name(types[i]).equals(args[i].getName());
            if (match) installed++;
        }
        return installed;
    }
    private static void primitiveNames() {
        Class<?>[] primitives = {void.class,boolean.class,byte.class,short.class,char.class,int.class,long.class,float.class,double.class};
        for (Class<?> primitive : primitives) check(HostSymbols.name(primitive.getName()).equals(primitive.getName()), "primitive name " + primitive);
        check(HostSymbols.name(int[].class.getName()).equals(int[].class.getName()), "primitive array reflection name");
        check(HostSymbols.name(boolean[][].class.getName()).equals(boolean[][].class.getName()), "nested primitive array name");
        check(HostSymbols.name("[[I").equals("[[I"), "nested int array identity");
        check(HostSymbols.name(String[].class.getName()).equals(String[].class.getName()), "platform array reflection name");
    }
    public interface PublicContract { default void ifaceActual() {} int interfaceActualField = 7; }
    public static class PublicParent implements PublicContract {
        public void parentActual(int n) {}
        public void dismissAllowingStateLoss() {}
        public void overloadActual(String s) {}
        public static void staticActual() {}
        public int parentActualField;
        public static int staticActualField;
        private void hiddenActual() {}
    }
    public static class PublicChild extends PublicParent {}
    public static class Lazy { static { System.setProperty("HostSymbolsTest.lazy", "yes"); } }
    interface Action { void run() throws Exception; }
    private static void check(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label);
    }
    private static void fails(Class<? extends Throwable> expected, Action action) throws Exception {
        try { action.run(); } catch (Throwable failure) {
            check(expected.isInstance(failure), "expected " + expected + ", got " + failure);
            return;
        }
        throw new AssertionError("expected " + expected);
    }
    private static void setup(List<String> logs) {
        HostSymbols.resetForTest();
        Map<String,String> classes = new HashMap<>();
        classes.put("old.a", Actual.class.getName());
        Map<String,String> methods = new HashMap<>();
        methods.put("old.a#a(I)Ljava/lang/String;", "renamed");
        methods.put("old.a#b", "other");
        methods.put("old.a#v([Lold/a;)V", "array");
        methods.put("old.a#bad", "renamed");
        methods.put("old.a#staticBad(I)Ljava/lang/String;", "renamed");
        methods.put("old.a#inherited", "inherited");
        Map<String,String> fields = new HashMap<>();
        fields.put("old.a#f", "renamedField");
        fields.put("old.a#f:I", "renamedField");
        fields.put("old.a#parentField", "parentField");
        HostSymbols.register("fixture", 2, classes, methods, fields, Map.of("abc", "xyz"));
        classes.put("old.a", "wrong.Mutation");
        methods.put("old.a#b", "wrongMutation");
        HostSymbols.registerValidation("fixture",2,new String[][]{new String[]{"M","old.a","staticBad","(I)Ljava/lang/String;",Actual.class.getName(),"renamed","(I)Ljava/lang/String;","true"}});
        HostSymbols.initialize("fixture", 2, LOADER, logs::add);
    }
    public static void main(String[] args) throws Exception {
        List<String> logs = new CopyOnWriteArrayList<>();
        HostSymbols.resetForTest();
        check(HostSymbols.name("x.a").equals("x.a"), "uninitialized identity");
        check(HostSymbols.literal("abc").equals("abc"), "uninitialized literal identity");
        HostSymbols.initialize("com.dragon.read",73932,LOADER,logs::add);
        check(HostSymbols.name("x.a").equals("x.a"), "baseline class identity");
        check(HostSymbols.method(Actual.class,"renamed",int.class).getName().equals("renamed"), "baseline exact method");
        check(HostSymbols.field(Actual.class,"renamedField").getType()==int.class, "baseline field");
        check(HostSymbols.canonicalResource("abc").equals("abc"), "baseline reverse resource");
        check(HostSymbols.literal("x.a").equals("x.a"), "baseline literal");
        HostSymbols.initialize("other",1,LOADER,logs::add);
        check(HostSymbols.name("x.a").equals("x.a"), "first initialize wins");
        fails(ClassNotFoundException.class, () -> HostSymbols.type("nonexistent.baseline.Class",LOADER));
        check(logs.isEmpty(), "baseline adds no logs including failures");
        System.clearProperty("HostSymbolsTest.lazy");
        HostSymbols.type(HostSymbolsTest.class.getName()+"$Lazy", LOADER);
        check(System.getProperty("HostSymbolsTest.lazy")==null, "loading does not initialize class");

        check(HostSymbols.isBaseline(), "baseline status");
        check(HostSymbols.requireCapability("baseline",new String[0],new String[0],new String[0],new String[0]), "baseline gate identity");
        setup(logs);
        check(!HostSymbols.isBaseline(), "mapped status");
        check(HostSymbols.requireCapability("exact",new String[]{"old.a"},new String[]{"old.a#a(I)Ljava/lang/String;"},new String[0],new String[]{"abc"}), "exact gate passes");
        check(HostSymbols.requireCapability("simple",new String[]{"old.a"},new String[]{"old.a#b"},new String[]{"old.a#f"},new String[]{"abc"}), "simple gate passes");
        check(!HostSymbols.requireCapability("missing",new String[]{"old.a"},new String[]{"old.a#absent"},new String[0],new String[0]), "gate missing map");
        check(!HostSymbols.requireCapability("empty",new String[0],new String[0],new String[0],new String[0]), "empty gate fails closed");
        check(HostSymbols.matches(Actual.class.getDeclaredMethod("renamed",int.class),"a"), "full method predicate");
        check(!HostSymbols.matches(Actual.class.getDeclaredMethod("renamed",int.class),"bad"), "simple key is not overload evidence");
        check(!HostSymbols.matches(Actual.class.getDeclaredMethod("renamed",int.class),"staticBad"), "static mismatch predicate");
        check(!HostSymbols.requireCapability("static",new String[]{"old.a"},new String[]{"old.a#staticBad(I)Ljava/lang/String;"},new String[0],new String[0]), "static mismatch gate");
        check(HostSymbols.matches(Actual.class.getDeclaredField("renamedField"),"f"), "typed field predicate");
        check(HostSymbols.type("old.a",LOADER)==Actual.class, "class map copied");
        check(HostSymbols.type("old.a")==Actual.class, "stored loader");
        check(HostSymbols.canonical(Actual.class.getName()).equals("old.a"), "reverse class");
        check(HostSymbols.literal("old.a").equals(Actual.class.getName()), "class literal");
        check(HostSymbols.literal("abc").equals("xyz"), "resource literal");
        check(HostSymbols.resourceName("abc").equals("xyz"), "resource map");
        check(HostSymbols.canonicalResource("xyz").equals("abc"), "reverse resource");
        check(HostSymbols.literal("BookStore").equals("BookStore"), "enum literal preserved");
        check(HostSymbols.methodReturning(Actual.class,"a",String.class,int.class).getName().equals("renamed"), "full descriptor mapping");
        check(HostSymbols.method(Actual.class,"a",int.class).getReturnType()==String.class, "unique full descriptor no-return lookup");
        check(HostSymbols.method(Actual.class,"b",String.class).getName().equals("other"), "simple key copied");
        check(HostSymbols.methodName(Actual.class,"b").equals("other"), "class member name");
        check(HostSymbols.methodName("old.a","b").equals("other"), "string member name");
        check(HostSymbols.method(Actual.class,"v",Actual[].class).getName().equals("array"), "canonical array descriptor");
        check(HostSymbols.fieldOfType(Actual.class,"f",int.class).getName().equals("renamedField"), "typed field");
        check(HostSymbols.fieldName("old.a","f").equals("renamedField"), "field name string");
        fails(NoSuchMethodException.class, () -> HostSymbols.methodReturning(Actual.class,"bad",void.class,int.class));
        fails(NoSuchMethodException.class, () -> HostSymbols.method(Actual.class,"b",int.class));
        fails(NoSuchMethodException.class, () -> HostSymbols.method(Actual.class,"inherited"));
        fails(NoSuchFieldException.class, () -> HostSymbols.fieldOfType(Actual.class,"f",String.class));
        fails(NoSuchFieldException.class, () -> HostSymbols.field(Actual.class,"parentField"));
        fails(IllegalStateException.class, () -> HostSymbols.register("late",1,Map.of(),Map.of(),Map.of(),Map.of()));

        HostSymbols.resetForTest(); logs.clear();
        HostSymbols.initialize("unknown",73932,LOADER,logs::add);
        check(!HostSymbols.name("x.a").equals("x.a"), "package part of baseline identity");
        ClassLoader rejecting = new ClassLoader(null) {
            @Override protected Class<?> loadClass(String name, boolean resolve) { throw new AssertionError("loader attempted " + name); }
        };
        fails(ClassNotFoundException.class, () -> HostSymbols.type("x.a",rejecting));
        check(HostSymbols.type("java.lang.String",LOADER)==String.class, "platform class retained");
        check(HostSymbols.name("com.dragon.read.NamedHost").equals("com.dragon.read.NamedHost"), "descriptive class retained");
        check(!HostSymbols.name("androidx.compose.ui.a").equals("androidx.compose.ui.a"), "androidx obfuscation skipped");
        check(HostSymbols.method(String.class,"length").getReturnType()==int.class, "platform member retained");
        fails(NoSuchMethodException.class, () -> HostSymbols.method(Actual.class,"renamed",int.class));
        check(!HostSymbols.resourceName("abc").equals("abc"), "unknown resource skipped");
        check(!HostSymbols.canonicalResource("abc").equals("abc"), "unknown reverse resource skipped");
        int prior = logs.size();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> pending = new ArrayList<>();
            for (int i=0;i<20;i++) pending.add(pool.submit(() -> HostSymbols.name("concurrent.a")));
            for (Future<?> future : pending) future.get();
        } finally { pool.shutdownNow(); }
        check(logs.size()==prior+1,"concurrent log once");
        HostSymbols.resetForTest();
        fails(IllegalArgumentException.class, () -> HostSymbols.register("fixture",1,Map.of("a","same","b","same"),Map.of(),Map.of(),Map.of()));
        HostSymbols.register("fixture",1,Map.of(),Map.of(),Map.of(),Map.of());
        fails(IllegalArgumentException.class, () -> HostSymbols.register("fixture",1,Map.of(),Map.of(),Map.of(),Map.of()));
        HostSymbols.resetForTest();
        check(HostSymbols.publicMethodName(PublicChild.class,"unknown").equals("unknown"), "public uninitialized identity");
        check(HostSymbols.publicFieldName(PublicChild.class,"unknown").equals("unknown"), "public field uninitialized identity");
        HostSymbols.register("inherit",3,
                Map.of("old.parent",PublicParent.class.getName(),"old.iface",PublicContract.class.getName()),
                Map.of("old.parent#close(I)V","parentActual", "old.parent#close(Ljava/lang/String;)V","overloadActual",
                       "old.parent#static()V","staticActual", "old.parent#hidden()V","hiddenActual",
                       "old.iface#iface()V","ifaceActual", "old.parent#wrongStatic()V","staticActual"),
                Map.of("old.parent#f:I","parentActualField", "old.parent#s:I","staticActualField",
                       "old.iface#i:I","interfaceActualField"), Map.of());
        HostSymbols.registerValidation("inherit",3,new String[][]{
                {"M","old.parent","static","()V",PublicParent.class.getName(),"staticActual","()V","true"},
                {"M","old.parent","wrongStatic","()V",PublicParent.class.getName(),"staticActual","()V","false"}});
        HostSymbols.initialize("inherit",3,LOADER,logs::add);
        check(HostSymbols.publicMethodName(PublicChild.class,"close",int.class).equals("parentActual"), "public inherited exact overload");
        check(PublicChild.class.getMethod(HostSymbols.publicMethodName(PublicChild.class,"close",int.class),int.class).getDeclaringClass()==PublicParent.class, "public inherited getMethod succeeds");
        check(HostSymbols.publicMethodName(PublicChild.class,"close",String.class).equals("overloadActual"), "public second overload exact");
        check(!HostSymbols.publicMethodName(PublicChild.class,"close").equals("parentActual"), "different overload names ambiguous");
        check(HostSymbols.publicMethodName(PublicChild.class,"static").equals("staticActual"), "inherited static method");
        check(!HostSymbols.publicMethodName(PublicChild.class,"wrongStatic").equals("staticActual"), "inherited static shape rejected");
        check(HostSymbols.publicMethodName(PublicChild.class,"iface").equals("ifaceActual"), "interface default inherited");
        check(!HostSymbols.publicMethodName(PublicChild.class,"hidden").equals("hiddenActual"), "private ancestor excluded");
        check(!HostSymbols.publicMethodName(PublicChild.class,"parentActual").equals("parentActual"), "no arbitrary same-name fallback");
        check(!HostSymbols.publicMethodName(PublicChild.class,"dismissAllowingStateLoss").equals("dismissAllowingStateLoss"), "APK named ancestor method still requires evidence");
        check(!HostSymbols.publicMethodName(PublicChild.class,"dismissAllowingStateLoss",new Class<?>[0]).equals("dismissAllowingStateLoss"), "exact public lookup has no named ancestor fallback");
        check(HostSymbols.publicMethodName(PublicChild.class,"toString").equals("toString"), "platform public parent method retained");
        check(HostSymbols.publicFieldName(PublicChild.class,"f").equals("parentActualField"), "inherited public field");
        check(PublicChild.class.getField(HostSymbols.publicFieldName(PublicChild.class,"f")).getDeclaringClass()==PublicParent.class, "public inherited getField succeeds");
        check(HostSymbols.publicFieldName(PublicChild.class,"s").equals("staticActualField"), "inherited static field");
        check(HostSymbols.publicFieldName(PublicChild.class,"i").equals("interfaceActualField"), "interface public constant");
        check(!HostSymbols.fieldName(PublicChild.class,"f").equals("parentActualField"), "declared field mapping never walks parent");
        fails(NoSuchMethodException.class, () -> HostSymbols.method(PublicChild.class,"close",int.class));
        HostSymbols.resetForTest();
        HostSymbols.register("signature",4,Map.of("old.signature",SignatureFixture.class.getName(),"old.array",Actual.class.getName()),
                Map.of("old.signature#v(ZI)V","actualVoid", "old.signature#b(I)Z","actualBoolean", "old.signature#i(Z)I","actualInt"),Map.of(),Map.of());
        HostSymbols.initialize("signature",4,LOADER,logs::add);
        primitiveNames();
        check(HostSymbols.name("[Lold.array;").equals(Actual[].class.getName()), "mapped host array component");
        check(HostSymbols.type("[Lold.array;",LOADER)==Actual[].class, "mapped array class loading");
        check(HostSymbols.canonical(Actual[].class.getComponentType().getName()).equals("old.array"), "actual mapped array component reverse");
        java.lang.reflect.Method actualBoolean = SignatureFixture.class.getDeclaredMethod("actualBoolean",int.class);
        check(!actualBoolean.getReturnType().getName().equals(HostSymbols.name("int")), "helper wrong return signature excluded");
        check(!actualBoolean.getParameterTypes()[0].getName().equals(HostSymbols.name("boolean")), "helper wrong primitive parameter excluded");
        check(!HostSymbols.matches(actualBoolean,"v"), "helper wrong mapped method signature excluded");
        String[] originalNames = {"v","b","i"};
        String[] originalReturns = {"void","boolean","int"};
        String[][] originalParameters = {{"boolean","int"},{"int"},{"boolean"}};
        for (int i=0;i<originalNames.length;i++) {
            int hits = 0;
            for (java.lang.reflect.Method method : SignatureFixture.class.getDeclaredMethods()) {
                if (!HostSymbols.matches(method,originalNames[i]) || !method.getReturnType().getName().equals(HostSymbols.name(originalReturns[i]))) continue;
                Class<?>[] actualParams = method.getParameterTypes();
                if (actualParams.length != originalParameters[i].length) continue;
                boolean exact = true;
                for (int j=0;j<actualParams.length;j++) exact &= actualParams[j].getName().equals(HostSymbols.name(originalParameters[i][j]));
                if (exact) hits++;
            }
            check(hits==1,"real helper primitive signature " + originalNames[i]);
        }
        HostSymbols.resetForTest();
        HostSymbols.register("update",6,Map.of("old.update",UpdateFixture.class.getName(),"old.payload",Actual.class.getName()),
                Map.of("old.update#update(ILold/payload;Z)V","actualUpdate", "old.update#check(Lold/payload;J)Z","actualCheck"),Map.of(),Map.of());
        HostSymbols.initialize("update",6,LOADER,logs::add);
        check(updateHits("update","void","int","old.payload","boolean")==1, "Update helper void with primitive/object parameters");
        check(updateHits("check","boolean","old.payload","long")==1, "Update helper boolean with object/primitive parameters");
        check(updateHits("update","boolean","int","old.payload","boolean")==0, "Update helper wrong return excluded");
        check(updateHits("check","void","old.payload","long")==0, "Update helper wrong boolean return excluded");
        check(updateHits("update","void","boolean","old.payload","boolean")==0, "Update helper wrong primitive argument excluded");
        check(updateHits("update","void","int","java.lang.String","boolean")==0, "Update helper wrong object argument excluded");
        check(updateHits("check","boolean","old.payload")==0, "Update helper wrong argument count excluded");
        check(updateHits("check","boolean","long","old.payload")==0, "Update helper wrong argument order excluded");
        HostSymbols.resetForTest();
        HostSymbols.initialize("unknown-primitives",5,LOADER,logs::add);
        primitiveNames();
        check(!HostSymbols.name("[Lunknown.a;").equals("[Lunknown.a;"), "unknown host array still skipped");
        HostSymbols.resetForTest();
        System.out.println("HostSymbolsTest passed (" + checks + " checks)");
    }
}
