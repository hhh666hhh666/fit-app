package com.example.fitapp.module.nutrition.storage;

import com.example.fitapp.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhotoUploadPolicyTest {

    private static byte[] jpeg(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", bos);
        return bos.toByteArray();
    }

    @Test
    void 超过上限给出带数字的提示() {
        PhotoUploadPolicy p = new PhotoUploadPolicy(1);
        MockMultipartFile big = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[1024 * 1024 + 1]);
        BusinessException e = assertThrows(BusinessException.class, () -> p.validate(big));
        assertEquals("照片超过 1MB", e.getMessage());
    }

    @Test
    void 上限之内放行并返回类型() {
        PhotoUploadPolicy p = new PhotoUploadPolicy(1);
        MockMultipartFile ok = new MockMultipartFile("file", "a.png", "image/png", new byte[1024 * 1024]);
        assertEquals("image/png", p.validate(ok));
    }

    @Test
    void 只认三种图片类型() {
        PhotoUploadPolicy p = new PhotoUploadPolicy(50);
        MockMultipartFile gif = new MockMultipartFile("file", "a.gif", "image/gif", new byte[]{1});
        assertThrows(BusinessException.class, () -> p.validate(gif));
    }

    /** 大图降采样解码后缩到长边 320，比例不变（ADR 0051）。 */
    @Test
    void 大图生成长边320的缩略图() throws Exception {
        byte[] thumb = PhotoUploadPolicy.makeThumb(jpeg(4000, 3000));
        assertNotNull(thumb);
        BufferedImage t = ImageIO.read(new ByteArrayInputStream(thumb));
        assertEquals(320, t.getWidth());
        assertEquals(240, t.getHeight());
    }

    @Test
    void 小图不放大() throws Exception {
        BufferedImage t = ImageIO.read(new ByteArrayInputStream(PhotoUploadPolicy.makeThumb(jpeg(100, 50))));
        assertEquals(100, t.getWidth());
        assertEquals(50, t.getHeight());
    }

    @Test
    void 解不出来的内容返回空_不抛错() {
        assertNull(PhotoUploadPolicy.makeThumb(new byte[]{1, 2, 3}));
    }

    @Test
    void 降采样读出的图不比目标的两倍大太多() throws Exception {
        BufferedImage img = PhotoUploadPolicy.readScaled(
                new org.springframework.core.io.ByteArrayResource(jpeg(6000, 4000)), 320);
        assertNotNull(img);
        assertTrue(img.getWidth() <= 320 * 4, "宽 " + img.getWidth());
    }
}
