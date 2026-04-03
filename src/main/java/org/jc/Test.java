package org.jc;

import java.util.Scanner;

public class Test {
    //2615371
    //4
    //131
    public static void main(String[] args) {
//        Scanner scanner = new Scanner(System.in);
//        String num = scanner.nextLine();
//        Integer size = scanner.nextInt();
//        System.out.println((int) '0');
//        System.out.println((int) '1');
        System.out.println(calc("2615371", 4));
    }

    public static int calc(String num, int size) {
        char[] chars = num.toCharArray();
        //要删除size位
        //至少x位
        int x = chars.length - size;
        char[] newChars = new char[x];
        int newCharI = 0;
        for (int i = 0; i < chars.length; ) {
            if (newCharI >= newChars.length) {
                break;
            }
            int min = 0;
            int tmp;
            for (int j = i; j <= chars.length - x; j++) {
                tmp = (int) chars[j] - 48;
                if (min == 0) {
                    min = tmp;
                    i = j + 1;
                    continue;
                }
                if (tmp < min && tmp > 0) {
                    min = tmp;
                    i = j + 1;
                }
            }

            newChars[newCharI++] = (char) (min + 48);
            x--;
        }
        return Integer.parseInt(new String(newChars));
    }
}
