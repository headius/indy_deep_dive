import java.io.PrintStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import static java.lang.invoke.MethodHandles.*;
import java.lang.invoke.MethodType;
import static java.lang.invoke.MethodType.*;
import java.lang.invoke.SwitchPoint;
import java.util.Random;

public class BasicHandles {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final MethodHandles.Lookup PUBLOOKUP = MethodHandles.publicLookup();

    public static void main(String[] args) throws Throwable {

        // method invocation

        // example Java
        // String value1 = System.getProperty("java.home");
        // System.out.println("Hello, world");

        // getProperty signature
        MethodType type1 = MethodType.methodType(String.class, String.class);
        // println signature
        MethodType type2 = MethodType.methodType(void.class, Object.class);

        MethodHandle getPropertyMH = LOOKUP
                .findStatic(System.class, "getProperty", type1);
        MethodHandle printlnMH = LOOKUP
                .findVirtual(PrintStream.class, "println", type2);

        
        String value1 = (String) getPropertyMH.invoke("java.home");
        printlnMH.invoke(System.out, (Object) "Hello, world");

        // field get

        // example Java
        // PrintStream out1 = System.out;

        MethodHandle systemOutMH = LOOKUP
                .findStaticGetter(System.class, "out", PrintStream.class);

        PrintStream out3 = (PrintStream) systemOutMH.invoke();

        // field set

        class MyStruct {
            public String name;
        }

        // example Java
        // MyStruct ms = new MyStruct();
        // ms.name = "Mattias";

        MethodHandle nameSetter = LOOKUP
                .findSetter(MyStruct.class, "name", String.class);

//        nameSetter.invoke(ms, "Charles");

        // insert

        MethodHandle getJavaHomeMH =
                MethodHandles.insertArguments(getPropertyMH, 0, "java.home");
        MethodHandle systemOutPrintlnMH =
                MethodHandles.insertArguments(printlnMH, 0, System.out);

//        // same as getProperty("java.home")
        getJavaHomeMH.invokeWithArguments();
//        // same as System.out.println(...
        systemOutPrintlnMH.invokeWithArguments("Hello, world");

        // filter

        // example Java
        // class UpperCasifier {
        //     public String call(String inputString) {
        //         return inputString.toUpperCase();
        //     }
        // }

        // pointer to String.toUpperCase
        MethodHandle toUpperCaseMH = LOOKUP.findVirtual(
                String.class,
                "toUpperCase",
                methodType(String.class));

        // Change its type to Object ...(Object)
        MethodHandle objectToUpperCaseMH =
                toUpperCaseMH.asType(methodType(Object.class, Object.class));

        // Make a println that always upcases
        MethodHandle upcasePrintlnMH =
                filterArguments(systemOutPrintlnMH, 0, objectToUpperCaseMH);

        // prints out "THIS WILL BE UPCASED
        upcasePrintlnMH.invokeWithArguments("this will be upcased");

        // boolean branch

        // example Java
//         class UpperDowner {
//             private static final Random RANDOM = ...
//             public String call(String inputString) {
//                 if (RANDOM.nextBoolean()) {
//                     return inputString.toUpperCase();
//                 } else {
//                     return inputString.toLowerCase();
//                 }
//             }
//         }

        // pointer to String.toLowerCase
        MethodHandle toLowerCaseMH = LOOKUP.findVirtual(
                String.class,
                "toLowerCase",
                methodType(String.class));

        // randomly return true or false
        MethodHandle upOrDown = LOOKUP.findStatic(
                BasicHandles.class,
                "randomBoolean",
                methodType(boolean.class));

        // ignore the incoming String by dropping it
        upOrDown = dropArguments(upOrDown, 0, String.class);

        MethodHandle upperDowner = guardWithTest(
                upOrDown,
                toUpperCaseMH,
                toLowerCaseMH);

        // print out the result
        MethodHandle upperDownerPrinter = filterArguments(
                systemOutPrintlnMH,
                0,
                upperDowner.asType(methodType(Object.class, Object.class)));

        // depending on random boolean, upcases or downcases "Hello, world"
//        upperDownerPrinter.invoke("Hello, world");
//        upperDownerPrinter.invoke("Hello, world");
//        upperDownerPrinter.invoke("Hello, world");
//        upperDownerPrinter.invoke("Hello, world");
//        upperDownerPrinter.invoke("Hello, world");

        // switch branch
        
        // example Java
//         class UpperDownerSwitch {
//             private volatile boolean on = true;
//             public String call(String inputString) {
//                 if (on) {
//                     return inputString.toUpperCase();
//                 } else {
//                     return inputString.toLowerCase();
//                 }
//             }
//        
//             public void turnOff() {
//                 on = false;
//             }
//         }

        SwitchPoint upperLowerSwitch = new SwitchPoint();

        MethodHandle upperLower =
                upperLowerSwitch.guardWithTest(toUpperCaseMH, toLowerCaseMH);
        upperLower.invoke("MyString"); // => "MYSTRING"
        upperLower.invoke("MyOtherString"); // => "MYOTHERSTRING"
        SwitchPoint.invalidateAll(new SwitchPoint[]{upperLowerSwitch});
        upperLower.invoke("MyString"); // => "mystring"
    }
    private static final Random RANDOM = new Random(System.currentTimeMillis());    
    
    public static boolean randomBoolean() {
        return RANDOM.nextBoolean();
    }
}
