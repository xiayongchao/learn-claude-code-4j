public class greet {
    /**
     * 打印问候语
     * @param name 要问候的人名
     */
    public static void greet(String name) {
        System.out.println("Hello, " + name + "!");
    }

    public static void main(String[] args) {
        greet("World");
    }
}
