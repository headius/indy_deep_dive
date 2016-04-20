import com.headius.invokebinder.Binder;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import me.qmx.jitescript.CodeBlock;
import me.qmx.jitescript.JDKVersion;
import me.qmx.jitescript.JiteClass;
import static me.qmx.jitescript.util.CodegenUtils.*;
import me.qmx.jitescript.internal.org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;

public class StupidScript {
    public static void main(String[] args) {
        // our source code in the file "src/stupidscript"
        String script = readFile("src/stupidscript");

        // parse the script
        List<Node> ast = new Parser().parse(script);

        // invoke the compiler
        Runnable runnable = new Compiler().compile(ast);

        runnable.run();
    }
    
    // builtin function print (no args)
    public static void print() {
        System.out.println();
    }

    // builtin function print (one arg)
    public static void print(String arg0) {
        System.out.println(arg0);
    }

    public static CallSite dynlang(
            MethodHandles.Lookup lookup, String name, MethodType type) throws Exception {
        MutableCallSite mcs = new MutableCallSite(type);

        MethodHandle send = Binder.from(type)
                .insert(0, lookup, mcs)
                .invokeStatic(lookup, StupidScript.class, "sendImpl");

        mcs.setTarget(send);

        return mcs;
    }

    public static void sendImpl(MethodHandles.Lookup lookup, MutableCallSite mcs, String name, Object self) throws Throwable {
        MethodHandle target = null;

        switch (name) {

            case "print":
                // no-arg builtins
                target = Binder.from(void.class, String.class, Object.class)
                        .drop(0, 2)
                        .invokeStatic(lookup, StupidScript.class, name);
                break;

            default:
                // user-defined function
                target = Binder.from(void.class, String.class, Object.class)
                        .drop(0)
                        .cast(void.class, self.getClass())
                        .invokeVirtual(lookup, name);
                break;
        }

        mcs.setTarget(target);

        target.invoke(name, self);
    }

    public static void sendImpl(MethodHandles.Lookup lookup, MutableCallSite mcs, String name, String arg0, Object self) throws Throwable {
        MethodHandle target = null;

        switch (name) {

            case "print":
                // no-arg builtins
                target = Binder.from(void.class, String.class, String.class, Object.class)
                        .permute(1)
                        .invokeStatic(lookup, StupidScript.class, name);
                break;

            default:
                // user-defined function
                target = Binder.from(void.class, String.class, String.class, Object.class)
                        .permute(2, 1)
                        .cast(void.class, self.getClass(), String.class)
                        .invokeVirtual(lookup, name);
                break;
        }

        mcs.setTarget(target);

        target.invoke(name, arg0, self);
    }
    
    static class Node {

        public final String op;
        public final String arg;
        public final List<Node> children;

        public Node(String op) {
            this(op, null, Collections.EMPTY_LIST);
        }

        public Node(String op, String arg) {
            this(op, arg, Collections.EMPTY_LIST);
        }

        public Node(String op, String arg, List<Node> children) {
            this.op = op;
            this.arg = arg;
            this.children = children;
        }
    }

    static class Parser {

        List<Node> parse(String script) {
            String[] lines = script.split("\n");

            return parse(lines, 0);
        }

        List<Node> parse(String[] lines, int offset) {
            List<Node> ast = new ArrayList<>(lines.length);

            for (; offset < lines.length; offset++) {
                String line = lines[offset];

                // blank lines an comments become nops
                if (line.length() == 0 || line.startsWith("#")) {
                    ast.add(new Node("nop"));
                    continue;
                }

                // check for an argument
                int space = line.indexOf(' ');

                if (space == -1) {
                    // no argument

                    if (line.equals("end")) {
                        // end of function, return ast
                        return ast;
                    } else {
                        ast.add(new Node(line));
                    }
                } else {
                    // one argument
                    String op = line.substring(0, space);
                    String arg = line.substring(space + 1);

                    if (op.startsWith("def")) {
                        // recurse to get children, advance offset past end
                        List<Node> children = parse(lines, ++offset);
                        offset += children.size();
                        ast.add(new Node(op, arg, children));
                    } else {
                        // normal operation
                        ast.add(new Node(op, arg));
                    }
                }
            }

            return ast;
        }
    }

    static class Compiler {

        Runnable compile(final List<Node> ast) {
            byte[] bytes = new JiteClass("DynLang", p(Object.class), new String[]{p(Runnable.class)}) {
                {
                    final JiteClass jiteClass = this;

                    defineDefaultConstructor();

                    // main compiler
                    compile(jiteClass, 0, "run", ast);

                }
            }.toBytes(JDKVersion.V1_7);

            try {
                
                // save class bytes to disk
                try (FileOutputStream output = new FileOutputStream("DynLang.class")) {
                    output.write(bytes);
                }
            
                Class cls = new ClassLoader(getClass().getClassLoader()) {
                    public Class defineClass(String name, byte[] bytes) {
                        return super.defineClass(name, bytes, 0, bytes.length);
                    }
                }.defineClass("DynLang", bytes);

                return (Runnable) cls.newInstance();
                
            } catch (Exception e) { throw new RuntimeException(e); }
        }

        void compile(final JiteClass jiteClass, final int arity, final String name, final List<Node> ast) {
            String signature = sig(void.class, params(String.class, arity));

            jiteClass.defineMethod(name, Opcodes.ACC_PUBLIC, signature,
                    new CodeBlock() {
                        {
                            // push all args on stack
                            for (int i = 0; i < arity; i++) {
                                aload(i + 1);
                            }

                            // emit body of function
                            for (Node node : ast) {
                                emit(jiteClass, this, node);
                            }
                            voidreturn();
                        }
                    });
        }
        
        final Handle DYNLANG_BOOTSTRAP = new Handle(Opcodes.H_INVOKESTATIC,
                p(StupidScript.class),
                "dynlang",
                sig(CallSite.class, MethodHandles.Lookup.class, String.class, MethodType.class));

        void emit(JiteClass jiteClass, CodeBlock body, Node node) {
            switch (node.op) {

                case "push":
                    body.ldc(node.arg);
                    break;

                case "send":
                    int arity = Integer.parseInt(node.arg);

                    // load self, since we may need it for the function
                    body.aload(0);

                    // method name and args are on stack

                    // invokedynamic given a method name and arity args on stack
                    switch (arity) {
                        case 0:
                            body.invokedynamic("send", sig(void.class, String.class, Object.class), DYNLANG_BOOTSTRAP);
                            break;
                        case 1:
                            body.invokedynamic("send", sig(void.class, String.class, String.class, Object.class), DYNLANG_BOOTSTRAP);
                            break;
                    }
                    break;

                case "def0":
                    compile(jiteClass, 0, node.arg, node.children);
                    break;

                case "def1":
                    compile(jiteClass, 1, node.arg, node.children);
                    break;

            }
        }
    }

    private static String readFile(String name) {
        try (FileReader fr = new FileReader(name)) {
            BufferedReader br = new BufferedReader(fr);
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();

            while (line != null) {
                sb.append(line);
                sb.append("\n");
                line = br.readLine();
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
