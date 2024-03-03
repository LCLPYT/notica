package work.lclpnet.kibu.nbs.api;

import java.io.DataInputStream;
import java.io.IOException;

class IoHelper {
    static short readShortLE(DataInputStream in) throws IOException {
        int lsb = in.readUnsignedByte();
        int msb = in.readUnsignedByte();
        return (short) ((msb << 8) + lsb);
    }

    static int readIntLE(DataInputStream in) throws IOException {
        int a = in.readUnsignedByte();
        int b = in.readUnsignedByte();
        int c = in.readUnsignedByte();
        int d = in.readUnsignedByte();
        return (d << 24) + (c << 16) + (b << 8) + a;
    }

    static String readString(DataInputStream in) throws IOException {
        final int length = readIntLE(in);
        StringBuilder builder = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            char c = (char) in.readByte();

            if (c == '\r') {
                c = ' ';
            }

            builder.append(c);
        }

        return builder.toString();
    }
}
