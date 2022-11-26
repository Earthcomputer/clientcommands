package net.earthcomputer.clientcommands.c2c;

import javax.crypto.Cipher;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class ConversionHelper {

    /**
     * @author Wagyourtail
     */
    public static class BaseUTF8 {
        private static final int UNICODE_LOWER_BOUND = 34;
        private static final int UNICODE_UPPER_BOUND = 0x10ffff - 0x800;

        public static String toUnicode(byte[] b) {
            StringBuilder sb = new StringBuilder();
            int data = 0;
            int bitPtr = 0;
            for (byte b1 : b) {
                data |= Byte.toUnsignedInt(b1) << bitPtr;
                bitPtr += 8;
                if (bitPtr > 19) {
                    int codePoint = mapCodepoint((data & 0x0FFFFF) + UNICODE_LOWER_BOUND);
                    if (codePoint >= UNICODE_UPPER_BOUND) {
                        throw new RuntimeException("WHAT?");
                    }
                    data >>= 20;
                    sb.appendCodePoint(codePoint);
                }
                bitPtr %= 20;
            }
            if (bitPtr != 0) {
                int codePoint = mapCodepoint((data & 0x0FFFFF) + UNICODE_LOWER_BOUND);
                if (codePoint >= UNICODE_UPPER_BOUND) {
                    throw new RuntimeException("WHAT?");
                }
                sb.appendCodePoint(codePoint);
            }
            return sb.toString();
        }

        public static byte[] fromUnicode(String s) {
            int bitLength = s.codePointCount(0, s.length()) * 20;
            int dataLength = (bitLength + 7) / 8;
            byte[] data = new byte[dataLength];
            int dataPtr = 0;
            for (int i = 0; i < s.length();) {
                int codepoint = s.codePointAt(i);
                i += Character.charCount(codepoint);
                if (codepoint >= UNICODE_UPPER_BOUND) {
                    throw new RuntimeException("WHAT?");
                }
                codepoint = unmapCodepoint(codepoint);
                codepoint -= UNICODE_LOWER_BOUND;
                data[dataPtr / 8] |= codepoint << (dataPtr % 8);
                codepoint >>= 8 - (dataPtr % 8);
                data[dataPtr / 8 + 1] |= codepoint;
                data[dataPtr / 8 + 2] |= codepoint >> 8;
                dataPtr += 20;
            }
            return Arrays.copyOf(data, dataLength);
        }

        private static int mapCodepoint(int codepoint) {
            if (codepoint < 0xd800) {
                return codepoint == 167 ? 32 : codepoint == 127 ? 33 : codepoint;
            } else {
                return codepoint + 0x800;
            }
        }

        private static int unmapCodepoint(int codepoint) {
            if (codepoint < 0xd800) {
                return codepoint == 32 ? 167 : codepoint == 33 ? 127 : codepoint;
            } else {
                return codepoint - 0x800;
            }
        }

    }

    /**
     * @author Wagyourtail
     */
    public static class Gzip {
        public static byte[] compress(byte[] bytes) {
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
                gzip.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return out.toByteArray();
        }

        public static byte[] uncompress(byte[] bytes) {
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayInputStream in = new ByteArrayInputStream(bytes);
            try (GZIPInputStream ungzip = new GZIPInputStream(in)) {
                byte[] buffer = new byte[256];
                int n;
                while ((n = ungzip.read(buffer)) >= 0) {
                    out.write(buffer, 0, n);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return out.toByteArray();
        }
    }

    public static class RsaEcb {
        public static byte[] encrypt(byte[] bytes, PublicKey key) {
            try {
                Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                cipher.init(Cipher.ENCRYPT_MODE, key);
                return cipher.doFinal(bytes);
            } catch (GeneralSecurityException e) {
                e.printStackTrace();
                return null;
            }
        }

        public static byte[] decrypt(byte[] bytes, PrivateKey key) {
            try {
                Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                cipher.init(Cipher.DECRYPT_MODE, key);
                return cipher.doFinal(bytes);
            } catch (GeneralSecurityException e) {
                e.printStackTrace();
                return null;
            }
        }
    }
}
