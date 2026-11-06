package com.example.fitapp.module.account.image;

import com.example.fitapp.module.nutrition.storage.PhotoUploadPolicy;
import org.springframework.core.io.InputStreamSource;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Iterator;

/**
 * 头像的图片处理（ADR 0051）：读尺寸与方向、按「摆正后的坐标」截一块并缩放。
 *
 * <p>⚠️ 原图最多 50MB、堆只有 256MB：一律只解码需要的那一块，并按整数倍降采样，
 * 不把整张原图解出来。坐标和宽高都是**按 EXIF 方向摆正之后**的——和浏览器里看到的一致。
 */
public final class AvatarImages {

    private AvatarImages() {
    }

    /** 原图信息：摆正后的宽高 ＋ EXIF 方向（1～8）。 */
    public record Info(int width, int height, int orientation) {

        /** 方向 5～8 会把宽高对调。 */
        boolean swapped() {
            return orientation >= 5;
        }

        int rawWidth() {
            return swapped() ? height : width;
        }

        int rawHeight() {
            return swapped() ? width : height;
        }
    }

    /** 读尺寸与方向；读不了（不是图、或 JDK 解不了的 WebP）返回 null。 */
    public static Info probe(InputStreamSource src) {
        int orientation;
        try (InputStream in = src.getInputStream()) {
            orientation = ExifOrientation.read(in);
        } catch (Exception e) {
            orientation = 1;
        }
        try (InputStream in = src.getInputStream();
             ImageInputStream iis = ImageIO.createImageInputStream(in)) {
            ImageReader reader = readerOf(iis);
            if (reader == null) {
                return null;
            }
            try {
                reader.setInput(iis, true, true);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                return orientation >= 5 ? new Info(h, w, orientation) : new Info(w, h, orientation);
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** 整张图摆正后等比缩到长边 ≤maxEdge 的 JPEG（缩略图、预览图用）；失败返回 null。 */
    public static byte[] whole(InputStreamSource src, Info info, int maxEdge) {
        BufferedImage img = render(src, info, new Rectangle(0, 0, info.width(), info.height()), maxEdge);
        return img == null ? null : PhotoUploadPolicy.toJpeg(PhotoUploadPolicy.fit(img, maxEdge));
    }

    /** 截一块正方形（摆正后的坐标）缩成 edge×edge 的 JPEG（头像本身）；失败返回 null。 */
    public static byte[] square(InputStreamSource src, Info info, int x, int y, int size, int edge) {
        BufferedImage img = render(src, info, new Rectangle(x, y, size, size), edge);
        return img == null ? null : PhotoUploadPolicy.toJpeg(scaleTo(img, edge, edge));
    }

    /** 把裁剪框收进图内：边长至少 1、不超过短边，位置不出界。 */
    public static Rectangle clampSquare(Info info, int x, int y, int size) {
        int s = Math.max(1, Math.min(size, Math.min(info.width(), info.height())));
        int cx = Math.max(0, Math.min(x, info.width() - s));
        int cy = Math.max(0, Math.min(y, info.height() - s));
        return new Rectangle(cx, cy, s, s);
    }

    /** 默认裁剪框：居中、取短边。 */
    public static Rectangle centerSquare(Info info) {
        int s = Math.min(info.width(), info.height());
        return new Rectangle((info.width() - s) / 2, (info.height() - s) / 2, s, s);
    }

    /**
     * 解码摆正后的一块区域并摆正，结果长边约为 maxEdge 的 1～2 倍（留给后面平滑缩放）。
     * 先把区域换算回原始像素坐标，只读那一块，再按方向旋转或翻转。
     */
    static BufferedImage render(InputStreamSource src, Info info, Rectangle oriented, int maxEdge) {
        Rectangle raw = toRaw(oriented, info);
        try (InputStream in = src.getInputStream();
             ImageInputStream iis = ImageIO.createImageInputStream(in)) {
            ImageReader reader = readerOf(iis);
            if (reader == null) {
                return null;
            }
            try {
                reader.setInput(iis, true, true);
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceRegion(raw);
                int step = Math.max(1, Math.max(raw.width, raw.height) / maxEdge / 2);
                param.setSourceSubsampling(step, step, 0, 0);
                return orient(reader.read(0, param), info.orientation());
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** 摆正后的矩形 → 原始像素里的矩形（四个角映射回去取外接框）。 */
    static Rectangle toRaw(Rectangle r, Info info) {
        int w = info.rawWidth();
        int h = info.rawHeight();
        int[] a = toRawPoint(r.x, r.y, w, h, info.orientation());
        int[] b = toRawPoint(r.x + r.width, r.y + r.height, w, h, info.orientation());
        int x0 = Math.min(a[0], b[0]);
        int y0 = Math.min(a[1], b[1]);
        int x1 = Math.max(a[0], b[0]);
        int y1 = Math.max(a[1], b[1]);
        x0 = Math.max(0, Math.min(x0, w - 1));
        y0 = Math.max(0, Math.min(y0, h - 1));
        return new Rectangle(x0, y0, Math.max(1, Math.min(x1, w) - x0), Math.max(1, Math.min(y1, h) - y0));
    }

    /** 摆正后的点 (x', y') 在原始像素里的位置；w、h 是原始宽高。 */
    static int[] toRawPoint(int x, int y, int w, int h, int o) {
        return switch (o) {
            case 2 -> new int[]{w - x, y};
            case 3 -> new int[]{w - x, h - y};
            case 4 -> new int[]{x, h - y};
            case 5 -> new int[]{y, x};
            case 6 -> new int[]{y, h - x};
            case 7 -> new int[]{w - y, h - x};
            case 8 -> new int[]{w - y, x};
            default -> new int[]{x, y};
        };
    }

    /** 按 EXIF 方向把解出来的像素摆正。 */
    static BufferedImage orient(BufferedImage img, int o) {
        if (o <= 1 || o > 8) {
            return img;
        }
        int w = img.getWidth();
        int h = img.getHeight();
        boolean swap = o >= 5;
        // 原始 (x, y) → 摆正后 (x', y')；构造参数顺序是 (m00, m10, m01, m11, m02, m12)
        AffineTransform t = switch (o) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);
            default -> new AffineTransform(0, -1, 1, 0, 0, w);
        };
        BufferedImage out = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.drawImage(img, t, null);
        g.dispose();
        return out;
    }

    /** 缩放到固定尺寸（小图会放大：头像一律 512×512）。 */
    static BufferedImage scaleTo(BufferedImage img, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.drawImage(img, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static ImageReader readerOf(ImageInputStream iis) {
        if (iis == null) {
            return null;
        }
        Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
        return readers.hasNext() ? readers.next() : null;
    }
}
