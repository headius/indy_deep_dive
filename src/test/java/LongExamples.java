import com.headius.invokebinder.Binder;
import org.objectweb.asm.Handle;
import me.qmx.jitescript.CodeBlock;
import me.qmx.jitescript.JDKVersion;
import me.qmx.jitescript.JiteClass;
import org.junit.Test;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.lang.invoke.SwitchPoint;
import java.util.Arrays;

import static me.qmx.jitescript.util.CodegenUtils.*;

public class LongExamples {
    @Test
    public void simpleExample() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        // method invocation

        String value1 = System.getProperty("java.home");

        MethodHandle m1 = lookup
                .findStatic(System.class, "getProperty", MethodType.methodType(String.class, String.class));

        MethodHandle m2 = Binder.from(String.class, String.class)
                .invokeStatic(lookup, System.class, "getProperty");

        String value2 = (String)m2.invoke("java.home");

        // field get

        PrintStream out1 = System.out;

        MethodHandle m3 = lookup
                .findStaticGetter(System.class, "out", PrintStream.class);

        MethodHandle m4 = Binder.from(PrintStream.class)
                .getStatic(lookup, System.class, "out");

        PrintStream out2 = (PrintStream)m4.invoke();

        // field set

        class MyStruct {
            public String name;
        }

        MyStruct ms = new MyStruct();

        MethodHandle m5 = lookup
                .findSetter(MyStruct.class, "name", String.class);

        MethodHandle m6 = Binder.from(void.class, MyStruct.class, String.class)
                .setField(lookup, "name");

        m6.invoke(ms, "Charles");

        // insert

        MethodHandle m7 = lookup
                .findStatic(System.class, "setProperty",
                        MethodType.methodType(String.class, String.class, String.class));
        m7 = MethodHandles.insertArguments(m7, 1, "my value");

        MethodHandle m8 = Binder.from(String.class, String.class)
                .insert(1, "my value")
                .invokeStatic(lookup, System.class, "setProperty");

        // drop

        MethodHandle m9 = lookup
                .findStatic(LongExamples.class, "twoArgs",
                        MethodType.methodType(String.class, String.class, String.class));
        m9 = MethodHandles.dropArguments(m9, 2, String.class);

        MethodHandle m10 = Binder.from(String.class, String.class, String.class, String.class)
                .drop(2)
                .invokeStatic(lookup, LongExamples.class, "twoArgs");

        m10.invoke("one", "two", "three"); // => "[one,two]"

        // permute

        MethodHandle m11 = lookup
                .findStatic(LongExamples.class, "twoArgs",
                        MethodType.methodType(String.class, String.class, String.class));
        m11 = MethodHandles.permuteArguments(
                m11,
                MethodType.methodType(String.class, String.class, String.class, int.class),
                1, 0);

        MethodHandle m12 = Binder.from(String.class, String.class, String.class, int.class)
                .permute(1, 0)
                .invokeStatic(lookup, LongExamples.class, "initials");

        m12.invoke("one", "two", 3); // => "[two,one]"

        // fold

        MethodHandle m13 = lookup
                .findStatic(LongExamples.class, "threeArgs",
                        MethodType.methodType(String.class, String.class, String.class, String.class));
        MethodHandle combiner = lookup
                .findStatic(LongExamples.class, "initials",
                        MethodType.methodType(String.class, String.class, String.class));
        m13 = MethodHandles.foldArguments(m13, combiner);

        MethodHandle m14 = Binder.from(String.class, String.class, String.class)
                .fold(
                        Binder
                                .from(String.class, String.class, String.class)
                                .invokeStatic(lookup, LongExamples.class, "initials")
                )
                .invokeStatic(lookup, LongExamples.class, "threeArgs");

        m14.invoke("Charles", "Nutter"); // => ["CN", "Charles", "Nutter"]

        // filter

        MethodHandle m15 = lookup
                .findStatic(LongExamples.class, "twoArgs",
                        MethodType.methodType(String.class, String.class, String.class));
        MethodHandle filter = lookup
                .findStatic(LongExamples.class, "upcase",
                        MethodType.methodType(String.class, String.class));
        m15 = MethodHandles.filterArguments(m15, 0, filter, filter);

        MethodHandle m16 = Binder.from(String.class, String.class, String.class)
                .filter(0,
                        Binder.from(String.class, String.class)
                                .invokeStatic(lookup, LongExamples.class, "upcase")
                )
                .invokeStatic(lookup, LongExamples.class, "twoArgs");

        m16.invoke("hello", "world"); // => ["HELLO", "WORLD"]

        // spread

        MethodHandle m17 = lookup
                .findStatic(LongExamples.class, "threeArgs",
                        MethodType.methodType(String.class, String.class, String.class, String.class));
        m17 = m17.asSpreader(String[].class, 3);

        MethodHandle m18 = Binder.from(String.class, String[].class)
                .spread(String.class, String.class, String.class)
                .invokeStatic(lookup, LongExamples.class, "threeArgs");

        m18.invoke("a,b,c".split(",")); // => ["a", "b", "c"]

        // collect

        MethodHandle m19 = lookup
                .findStatic(LongExamples.class, "nameAndNicknames",
                        MethodType.methodType(String.class, String.class, String[].class));
        m19.asCollector(String[].class, 2);

        MethodHandle m20 = Binder.from(String.class, String.class, String.class, String.class)
                .collect(1, String[].class)
                .invokeStatic(lookup, LongExamples.class, "nameAndNicknames");

        m20.invoke("Charles", "Charlie", "headius");
            // => "Charles aka ["Charlie", "headius"]"

        // boolean branch

        MethodHandle m21 = Binder.from(String.class, int.class, String.class)
                .branch(
                        Binder.from(boolean.class, int.class, String.class)
                                .drop(1)
                                .invokeStatic(lookup, LongExamples.class, "upOrDown"),

                        Binder.from(String.class, int.class, String.class)
                                .drop(0)
                                .invokeStatic(lookup, LongExamples.class, "upcase"),

                        Binder.from(String.class, int.class, String.class)
                                .drop(0)
                                .invokeStatic(lookup, LongExamples.class, "downcase")
                );

        m21.invoke(1, "MyString"); // => "MYSTRING"
        m21.invoke(0, "MyString"); // => "mystring"

        // switch branch

        SwitchPoint sp1 = new SwitchPoint();
        MethodHandle m22 = sp1.guardWithTest(
                    Binder.from(String.class, String.class)
                            .invokeStatic(lookup, LongExamples.class, "upcase"),

                    Binder.from(String.class, String.class)
                            .invokeStatic(lookup, LongExamples.class, "downcase"));

        m22.invoke("MyString"); // => "MYSTRING"
        m22.invoke("MyOtherString"); // => "MYOTHERSTRING"
        SwitchPoint.invalidateAll(new SwitchPoint[]{sp1});
        m22.invoke("MyString"); // => "mystring"

        // exception handling

        MethodHandle m23 = Binder.from(String.class, String.class)
                .catchException(
                        NullPointerException.class,
                        Binder.from(String.class, NullPointerException.class, String.class)
                                .drop(1)
                                .invokeStatic(lookup, LongExamples.class, "handleNPE")
                ).invokeStatic(lookup, System.class, "getProperty");

        m23.invoke("java.home"); // => works as normal
        m23.invoke(null); // => exception handler fires
    }

    @Test
    public void simpleIndy() throws Throwable {
        byte[] bytes = new JiteClass("SimpleIndy", p(Object.class), new String[] {p(Runnable.class)}) {{

            defineDefaultConstructor();

            defineMethod("run", ACC_PUBLIC, sig(void.class), new CodeBlock() {{
                invokedynamic("unused", sig(String.class),
                        new Handle(H_INVOKESTATIC,
                                p(LongExamples.class),
                                "simpleBootstrap",
                                sig(CallSite.class, MethodHandles.Lookup.class, String.class, MethodType.class)));
                aprintln();
                voidreturn();
            }   });
        }}.toBytes(JDKVersion.V1_7);

        Class cls = new ClassLoader(getClass().getClassLoader()) {
            public Class defineClass(String name, byte[] bytes) {
                return super.defineClass(name, bytes, 0, bytes.length);
            }
        }.defineClass("SimpleIndy", bytes);

        Runnable runnable = (Runnable)cls.newInstance();

        runnable.run();
    }

    @Test
    public void mutableCallSite() throws Throwable {
        byte[] bytes = new JiteClass("MutableCallSite", p(Object.class), new String[] {p(Runnable.class)}) {{

            defineDefaultConstructor();

            defineMethod("run", ACC_PUBLIC, sig(void.class), new CodeBlock() {{
                invokedynamic("unused", sig(void.class),
                        new Handle(H_INVOKESTATIC,
                                p(LongExamples.class),
                                "mutableCallSite",
                                sig(CallSite.class, MethodHandles.Lookup.class, String.class, MethodType.class)));
                voidreturn();
            }   });
        }}.toBytes(JDKVersion.V1_7);

        Class cls = new ClassLoader(getClass().getClassLoader()) {
            public Class defineClass(String name, byte[] bytes) {
                return super.defineClass(name, bytes, 0, bytes.length);
            }
        }.defineClass("MutableCallSite", bytes);

        Runnable runnable = (Runnable)cls.newInstance();

        runnable.run(); // => "first!"
        runnable.run(); // => "second!"
        runnable.run(); // => "first!"
    }

    /*
     * Our dynamic language is very simple.
     *
     * There are only two methods: first and second.
     *
     * They each take one argument, the number of times to print.
     *
     * If either are passed a count >= 10, they print "Too many" instead.
     */

    @Test
    public void dynlang() throws Throwable {
        // our source code
        String script = "send yes;send no";

        // our parser
        final String[] calls = script.split(";");

        // our compiler
        byte[] bytes = new JiteClass("DynLang", p(Object.class), new String[] {p(Runnable.class)}) {{
            defineDefaultConstructor();

            defineMethod("run", ACC_PUBLIC, sig(void.class), new CodeBlock() {{
                Handle dynlangBootstrap = new Handle(H_INVOKESTATIC,
                        p(LongExamples.class),
                        "dynlang",
                        sig(CallSite.class, MethodHandles.Lookup.class, String.class, MethodType.class));

                // emit invokedynamic for every call
                for (String call : calls) {
                    String[] nameAndCount = call.split(" ");

                    // push the count
                    ldc(nameAndCount[1]);

                    // call the method
                    invokedynamic(nameAndCount[0], sig(void.class, String.class), dynlangBootstrap);
                }

                voidreturn();
            }   });
        }}.toBytes(JDKVersion.V1_7);

        Class cls = new ClassLoader(getClass().getClassLoader()) {
            public Class defineClass(String name, byte[] bytes) {
                return super.defineClass(name, bytes, 0, bytes.length);
            }
        }.defineClass("DynLang", bytes);

        Runnable runnable = (Runnable)cls.newInstance();

        runnable.run(); // => "yes\nno\nno\nyes\n50 is too many!"
    }

    public static String twoArgs(String one, String two) {
        return Arrays.toString(new String[]{one + two});
    }

    public static String threeArgs(String one, String two, String three) {
        return Arrays.toString(new String[]{one, two, three});
    }

    public static String initials(String one, String two) {
        return "" + one.charAt(0) + two.charAt(0);
    }

    public static String nameAndNicknames(String name, String... nicknames) {
        return name + " aka " + Arrays.toString(nicknames);
    }

    public static String upcase(String arg) {
        return arg.toUpperCase();
    }

    public static String downcase(String arg) {
        return arg.toLowerCase();
    }

    public static final int UP = 1;
    public static boolean upOrDown(int upOrDown) {
        if (upOrDown == UP) {
            return true;
        } else {
            return false;
        }
    }

    public static String handleNPE(NullPointerException npe) {
        StringWriter sw = new StringWriter();
        npe.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    public static CallSite simpleBootstrap(
            MethodHandles.Lookup lookup, String name, MethodType type) {
        return new ConstantCallSite(
                MethodHandles.constant(String.class, "Hello, world!")
        );
    }

    public static CallSite mutableCallSite(
            MethodHandles.Lookup lookup, String name, MethodType type) throws Exception {
        MutableCallSite mcs = new MutableCallSite(type);

        MethodHandle first = Binder.from(void.class)
                .insert(0, lookup, mcs)
                .invokeStatic(lookup, LongExamples.class, "first");

        mcs.setTarget(first);

        return mcs;
    }

    public static void first(MethodHandles.Lookup lookup, MutableCallSite mcs) throws Exception {
        MethodHandle second = Binder.from(void.class)
                .insert(0, lookup, mcs)
                .invokeStatic(lookup, LongExamples.class, "second");

        mcs.setTarget(second);

        System.out.println("first!");
    }

    public static void second(MethodHandles.Lookup lookup, MutableCallSite mcs) throws Exception {
        MethodHandle second = Binder.from(void.class)
                .insert(0, lookup, mcs)
                .invokeStatic(lookup, LongExamples.class, "first");

        mcs.setTarget(second);

        System.out.println("second!");
    }

    public static void yes() {
        for (int i = 0; i < 10; i++) {
            System.out.println("yes");
        }
    }

    public static void no() {
        for (int i = 0; i < 10; i++) {
            System.out.println("no");
        }
    }

    public static boolean lessThanTen(int count) {
        return count < 10;
    }

    public static void tooMany(int count) {
        System.out.println("" + count + " is too many!");
    }

    public static CallSite dynlang(
            MethodHandles.Lookup lookup, String name, MethodType type) throws Exception {
        MutableCallSite mcs = new MutableCallSite(type);

        MethodHandle send = Binder.from(void.class, String.class)
                .insert(0, lookup, mcs)
                .invokeStatic(lookup, LongExamples.class, "send");

        mcs.setTarget(send);

        return mcs;
    }

    public static void send(MethodHandles.Lookup lookup, MutableCallSite mcs, String name) throws Throwable {
        MethodHandle foundMethod = Binder.from(void.class)
                .drop(0)
                .invokeStatic(lookup, LongExamples.class, name);

        mcs.setTarget(foundMethod);
    }
}
