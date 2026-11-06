package com.example.fitapp.module.account.image;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 从 JPEG 里读出 EXIF 方向（1～8，读不到就是 1）。手机拍的照片常常是「像素横着存、靠这个标记竖过来」，
 * 浏览器显示时会按它摆正，ImageIO 却不管——不读它，裁剪框框的位置和服务端裁出来的就对不上（ADR 0051）。
 *
 * <p>只看文件开头的 APP1 段，不引第三方库。PNG、WebP 以及任何读不懂的情况都按 1（不旋转）处理。
 */
public final class ExifOrientation {

    private ExifOrientation() {
    }

    public static int read(InputStream raw) {
        try {
            DataInputStream in = new DataInputStream(raw);
            if (in.readUnsignedShort() != 0xFFD8) {
                return 1;
            }
            while (true) {
                int marker = in.readUnsignedShort();
                if ((marker & 0xFF00) != 0xFF00 || marker == 0xFFDA || marker == 0xFFD9) {
                    return 1;
                }
                int len = in.readUnsignedShort() - 2;
                if (len < 0) {
                    return 1;
                }
                if (marker != 0xFFE1) {
                    in.skipNBytes(len);
                    continue;
                }
                byte[] seg = new byte[len];
                in.readFully(seg);
                int o = parseApp1(seg);
                if (o > 0) {
                    return o;
                }
            }
        } catch (IOException | RuntimeException e) {
            return 1;
        }
    }

    /** APP1 段：「Exif\0\0」＋ TIFF 头 ＋ IFD0；在 IFD0 里找 0x0112。不是 EXIF 的 APP1（如 XMP）返回 0。 */
    static int parseApp1(byte[] s) {
        if (s.length < 14 || s[0] != 'E' || s[1] != 'x' || s[2] != 'i' || s[3] != 'f' || s[4] != 0 || s[5] != 0) {
            return 0;
        }
        int t = 6;
        boolean le;
        if (s[t] == 'I' && s[t + 1] == 'I') {
            le = true;
        } else if (s[t] == 'M' && s[t + 1] == 'M') {
            le = false;
        } else {
            return 1;
        }
        int ifd = t + (int) u32(s, t + 4, le);
        int n = u16(s, ifd, le);
        for (int i = 0; i < n; i++) {
            int e = ifd + 2 + i * 12;
            if (e + 12 > s.length) {
                break;
            }
            if (u16(s, e, le) == 0x0112) {
                int v = u16(s, e + 8, le);
                return v >= 1 && v <= 8 ? v : 1;
            }
        }
        return 1;
    }

    private static int u16(byte[] b, int i, boolean le) {
        int a = b[i] & 0xFF;
        int c = b[i + 1] & 0xFF;
        return le ? (c << 8) | a : (a << 8) | c;
    }

    private static long u32(byte[] b, int i, boolean le) {
        long hi = u16(b, le ? i + 2 : i, le);
        long lo = u16(b, le ? i : i + 2, le);
        return (hi << 16) | lo;
    }
}
