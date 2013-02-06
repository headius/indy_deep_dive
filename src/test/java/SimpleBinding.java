import com.headius.invokebinder.Binder;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import static java.lang.invoke.MethodHandles.*;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import static java.lang.invoke.MethodType.*;
import java.lang.invoke.MutableCallSite;
import me.qmx.jitescript.CodeBlock;
import me.qmx.jitescript.JDKVersion;
import me.qmx.jitescript.JiteClass;
import static me.qmx.jitescript.util.CodegenUtils.*;
import org.junit.Test;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;

public class SimpleBinding {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    @Test
    public void simpleIndy() throws Throwable {

        final Handle asmHandle = new Handle(
                Opcodes.H_INVOKESTATIC,
                p(SimpleBinding.class),
                "simpleBootstrap",
                sig(CallSite.class, MethodHandles.Lookup.class, String.class, MethodType.class));

        Runnable runnable = newRunnable("SimpleIndy", new CodeBlock() {
            {
                invokedynamic("foo", sig(String.class), asmHandle); // invokedynamic foo:()String
                aprintln(); // print out string
                voidreturn(); // return;
            }
        });

        runnable.run();
        runnable.run();
        runnable.run();
    }

    public static CallSite simpleBootstrap(
            MethodHandles.Lookup lookup, String name, MethodType type) throws Exception {

        // Create and bind a constant site, pointing at the named method
        return new ConstantCallSite(
                lookup.findStatic(SimpleBinding.class, name, type));

    }
    
    static int counter = 1;
    
    public static String foo() {
        String result = "Hello, world #" + counter++;

        return result;
    }

    @Test
    public void mutableCallSite() throws Throwable {

        final Handle asmHandle = new Handle(
                Opcodes.H_INVOKESTATIC,
                p(SimpleBinding.class),
                "mutableCallSiteBootstrap",
                sig(CallSite.class, MethodHandles.Lookup.class, String.class, MethodType.class));

        Runnable runnable = newRunnable("MutateSite", new CodeBlock() {
            {
                invokedynamic("first", sig(void.class), asmHandle);
                voidreturn();
            }
        });

        runnable.run(); // => "first!"
        runnable.run(); // => "second!"
        runnable.run(); // => "first!"
    }

    public static CallSite mutableCallSiteBootstrap(
            MethodHandles.Lookup lookup, String name, MethodType type) throws Exception {
        MutableCallSite mcs = new MutableCallSite(type);

        // look up the first method to call
        MethodHandle target = lookup.findStatic(
                SimpleBinding.class,
                name,
                methodType(void.class, Lookup.class, MutableCallSite.class));

        // insert our Lookup and MutableCallSite into args
        target = insertArguments(target, 0, lookup, mcs);

        // The same thing with InvokeBinder
        target = Binder.from(void.class)
                .insert(0, lookup, mcs)
                .invokeStatic(lookup, SimpleBinding.class, name);

        mcs.setTarget(target);

        return mcs;
    }

    public static void first(MethodHandles.Lookup lookup, MutableCallSite mcs) throws Exception {
        // Look up "second" method and add Lookup and MutableCallSite
        MethodHandle second = Binder.from(void.class)
                .insert(0, lookup, mcs)
                .invokeStatic(lookup, SimpleBinding.class, "second");

        mcs.setTarget(second);

        System.out.println("first!");
    }

    public static void second(MethodHandles.Lookup lookup, MutableCallSite mcs) throws Exception {
        MethodHandle second = Binder.from(void.class)
                .insert(0, LOOKUP, mcs)
                .invokeStatic(LOOKUP, SimpleBinding.class, "first");

        mcs.setTarget(second);

        System.out.println("second!");
    }

    private Runnable newRunnable(String name, final CodeBlock codeBlock) throws Exception {
        byte[] bytes = new JiteClass("SimpleIndy", p(Object.class), new String[]{p(Runnable.class)}) {
            {
                defineDefaultConstructor();

                defineMethod("run", ACC_PUBLIC, sig(void.class), codeBlock);

            }
        }.toBytes(JDKVersion.V1_7);

        Class cls = new ClassLoader(getClass().getClassLoader()) {
            public Class defineClass(String name, byte[] bytes) {
                return super.defineClass(name, bytes, 0, bytes.length);
            }
        }.defineClass("SimpleIndy", bytes);

        return (Runnable) cls.newInstance();
    }
}
