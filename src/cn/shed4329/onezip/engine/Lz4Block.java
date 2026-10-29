package cn.shed4329.onezip.engine;

import java.io.*;
import java.util.Arrays;

/** Independent greedy LZ77 matcher and bounded literal/match decoder for standard LZ4 blocks. */
final class Lz4Block {
    private static int hash(byte[] b, int p) {
        return (XxHash32.little(b, p) * 0x9e3779b1) >>> 16;
    }

    private static void length(ByteArrayOutputStream out, int n) {
        while (n >= 255) {
            out.write(255);
            n -= 255;
        }
        out.write(n);
    }

    static byte[] encode(byte[] b, int n, int[] table) throws IOException {
        Arrays.fill(table, -1);
        ByteArrayOutputStream out = new ByteArrayOutputStream(n + n / 255 + 16);
        int p = 0, anchor = 0;
        while (p <= n - 12) {
            if ((p & 4095) == 0) ReadSupport.check(null);
            int h = hash(b, p), match = table[h];
            table[h] = p;
            if (match < 0
                    || p - match > 65535
                    || XxHash32.little(b, match) != XxHash32.little(b, p)) {
                p++;
                continue;
            }
            int literals = p - anchor, len = 4;
            while (p + len < n - 5 && b[match + len] == b[p + len]) len++;
            out.write((Math.min(15, literals) << 4) | Math.min(15, len - 4));
            if (literals >= 15) length(out, literals - 15);
            out.write(b, anchor, literals);
            int distance = p - match;
            out.write(distance & 255);
            out.write(distance >>> 8);
            if (len >= 19) length(out, len - 19);
            p += len;
            anchor = p;
            if (p >= 2 && p <= n - 12) table[hash(b, p - 2)] = p - 2;
        }
        int literals = n - anchor;
        out.write(Math.min(15, literals) << 4);
        if (literals >= 15) length(out, literals - 15);
        out.write(b, anchor, literals);
        return out.toByteArray();
    }

    static int decode(byte[] packed, int n, byte[] decoded, int start, int max, int history)
            throws IOException {
        int p = 0, out = start, lastMatch = -1;
        while (p < n) {
            ReadSupport.check(null);
            int token = packed[p++] & 255, literals = token >>> 4;
            if (literals == 15) {
                int x;
                do {
                    if (p == n) throw new EOFException("LZ4 literal length truncated");
                    x = packed[p++] & 255;
                    literals += x;
                    if (literals > max) throw new IOException("LZ4 literal length overflow");
                } while (x == 255);
            }
            if (literals > n - p || literals > max - (out - start))
                throw new IOException("LZ4 literal range invalid");
            System.arraycopy(packed, p, decoded, out, literals);
            p += literals;
            out += literals;
            if (p == n) {
                if (lastMatch >= 0 && (literals < 5 || out - lastMatch < 12))
                    throw new IOException("LZ4 invalid final literals");
                return out - start;
            }
            if (n - p < 2) throw new EOFException("LZ4 offset truncated");
            int distance = (packed[p] & 255) | ((packed[p + 1] & 255) << 8);
            p += 2;
            if (distance == 0 || distance > history + out - start)
                throw new IOException("LZ4 offset exceeds dictionary");
            int len = (token & 15) + 4;
            if ((token & 15) == 15) {
                int x;
                do {
                    if (p == n) throw new EOFException("LZ4 match length truncated");
                    x = packed[p++] & 255;
                    len += x;
                    if (len > max) throw new IOException("LZ4 match length overflow");
                } while (x == 255);
            }
            if (len > max - (out - start))
                throw new IOException("LZ4 block output exceeds maximum");
            lastMatch = out;
            int copied = Math.min(len, distance);
            System.arraycopy(decoded, out - distance, decoded, out, copied);
            while (copied < len) {
                int k = Math.min(copied, len - copied);
                System.arraycopy(decoded, out, decoded, out + copied, k);
                copied += k;
            }
            out += len;
        }
        throw new IOException("LZ4 block missing final literal sequence");
    }
}
