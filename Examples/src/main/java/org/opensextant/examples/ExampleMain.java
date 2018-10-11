package org.opensextant.examples;

public abstract class ExampleMain {

    public static void print(String msg) {
        System.out.println(msg);
    }

    public static void print(String msg, Object... args) {
        System.out.println(String.format(msg, args));
    }

    public static void error(Exception err, String... args) {
        System.out.println("ERROR " + err.getMessage());
        System.out.println(args);

        System.err.println("ERROR " + err.getMessage());
        err.printStackTrace(System.err);
    }

}
