package com.example.fitapp.module.account.image;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import javax.imageio.ImageIO;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 裁剪框按「摆正后的坐标」给，服务端要先换算回原始像素、只解那一块、再摆正（ADR 0051）。
 * 这里用每个像素颜色都不同的 PNG 逐个方向比对：「整张摆正后再截」和「先截再摆正」必须一致。
 */
class AvatarImagesTest {

    private static final int W = 60;
    private static final int H = 40;

    /** 每个像素的颜色编码了它的原始坐标，摆错一个方向就对不上。 */
    private static BufferedImage gradient() {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                img.setRGB(x, y, (x * 4) << 16 | (y * 6) << 8 | 0x80);
            }
        }
        return img;
    }

    private static ByteArrayResource png(BufferedImage img) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return new ByteArrayResource(bos.toByteArray());
    }

    @Test
    void 八个方向_先截再摆正等于摆正后再截() throws Exception {
        BufferedImage raw = gradient();
        ByteArrayResource src = png(raw);
        for (int o = 1; o <= 8; o++) {
            BufferedImage full = AvatarImages.orient(raw, o);
            AvatarImages.Info info = o >= 5 ? new AvatarImages.Info(H, W, o) : new AvatarImages.Info(W, H, o);
            assertEquals(info.width(), full.getWidth(), "方向 " + o + " 的宽");
            Rectangle box = new Rectangle(5, 7, 20, 20);
            // maxEdge 给大：不降采样，逐像素可比
            BufferedImage part = AvatarImages.render(src, info, box, 10_000);
            assertNotNull(part, "方向 " + o);
            assertEquals(20, part.getWidth(), "方向 " + o);
            assertEquals(20, part.getHeight(), "方向 " + o);
            for (int dx = 0; dx < 20; dx += 3) {
                for (int dy = 0; dy < 20; dy += 3) {
                    assertEquals(full.getRGB(box.x + dx, box.y + dy), part.getRGB(dx, dy),
                            "方向 " + o + " 在 (" + dx + "," + dy + ")");
                }
            }
        }
    }

    @Test
    void 裁出来的头像固定_512() throws Exception {
        ByteArrayResource src = png(gradient());
        AvatarImages.Info info = AvatarImages.probe(src);
        assertNotNull(info);
        assertEquals(W, info.width());
        byte[] jpg = AvatarImages.square(src, info, 10, 0, 40, 512);
        BufferedImage out = ImageIO.read(new ByteArrayInputStream(jpg));
        assertEquals(512, out.getWidth());
        assertEquals(512, out.getHeight());
    }

    @Test
    void 裁剪框出界会被收进图内() {
        AvatarImages.Info info = new AvatarImages.Info(100, 60, 1);
        Rectangle r = AvatarImages.clampSquare(info, 90, 50, 80);
        assertEquals(60, r.width);
        assertEquals(40, r.x);
        assertEquals(0, r.y);
        assertTrue(r.x + r.width <= 100);
    }

    @Test
    void 读EXIF方向_大端与小端() {
        assertEquals(6, ExifOrientation.parseApp1(app1(false, 6)));
        assertEquals(8, ExifOrientation.parseApp1(app1(true, 8)));
        assertEquals(0, ExifOrientation.parseApp1("http://ns.adobe.com/xap/1.0/\0".getBytes()));
    }

    @Test
    void 不是JPEG按不旋转() {
        assertEquals(1, ExifOrientation.read(new ByteArrayInputStream(new byte[]{(byte) 0x89, 'P', 'N', 'G'})));
    }

    /** 拼一个只有方向标记的 APP1 段。 */
    private static byte[] app1(boolean le, int orientation) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.writeBytes(new byte[]{'E', 'x', 'i', 'f', 0, 0});
        b.writeBytes(le ? new byte[]{'I', 'I', 42, 0, 8, 0, 0, 0} : new byte[]{'M', 'M', 0, 42, 0, 0, 0, 8});
        b.writeBytes(le ? new byte[]{1, 0} : new byte[]{0, 1});
        byte[] entry = le
                ? new byte[]{0x12, 0x01, 3, 0, 1, 0, 0, 0, (byte) orientation, 0, 0, 0}
                : new byte[]{0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, (byte) orientation, 0, 0};
        b.writeBytes(entry);
        b.writeBytes(new byte[]{0, 0, 0, 0});
        return b.toByteArray();
    }
}
