package work.lclpnet.notica.util;

public class ByteHelper {

    public static String toHexString(byte[] byteArray, int maxLength) {
        StringBuilder builder = new StringBuilder();

        int len = Math.min(byteArray.length, maxLength);

        for (int i = 0; i < len; i++) {
            builder.append(toHexString(byteArray[i]));
        }

        return builder.toString();
    }

    public static String toHexString(byte num) {
        char[] chars = new char[2];

        int msb = (num >> 4) & 0xF;
        chars[0] = Character.forDigit(msb, 16);

        int lsb = num & 0xF;
        chars[1] = Character.forDigit(lsb, 16);

        return new String(chars);
    }
}
