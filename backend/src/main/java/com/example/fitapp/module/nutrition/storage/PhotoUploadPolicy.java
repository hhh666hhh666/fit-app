package com.example.fitapp.module.nutrition.storage;

import com.example.fitapp.common.BusinessException;
import com.example.fitapp.common.ResultCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamSource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Set;

/**
 * 上传图片的公共口径：大小与类型校验、缩略图生成。餐照、日记图片和头像共用（ADR 0051）。
 *
 * <p>⚠️ 单张最多 50MB，而线上堆只有 256MB：这里**不把原图整张读进内存**。
 * 缩略图用降采样解码，只按目标尺寸读像素——一张 4800 万像素的原图完整解码要约 140MB，
 * 降采样后只要几 MB。
 */
@Component
public class PhotoUploadPolicy {

    private static final Set<String> PHOTO_MIME = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long BYTES_PER_MB = 1024L * 1024;
    /** 缩略图长边上限（px）：相册与列表加载它，点开才拉原图。 */
    public static final int THUMB_MAX_EDGE = 320;

    private final int maxMb;

    /** 上限的唯一配置点（{@code fitapp.storage.photo-max-mb}）；multipart 的限制也由它推出，见 MultipartConfig。 */
    public PhotoUploadPolicy(@Value("${fitapp.storage.photo-max-mb:50}") int maxMb) {
        this.maxMb = maxMb;
    }

    public int maxMb() {
        return maxMb;
    }

    /** 校验大小与类型，返回 MIME。 */
    public String validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "照片为空");
        }
        if (file.getSize() > maxMb * BYTES_PER_MB) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "照片超过 " + maxMb + "MB");
        }
        String mime = file.getContentType();
        if (mime == null || !PHOTO_MIME.contains(mime)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "只支持 JPEG/PNG/WebP 图片");
        }
        return mime;
    }

    /** 长边 ≤320 的 JPEG 缩略图；解码失败（如 JDK 读不了的 WebP）返回 null，读取端会回退原图。 */
    public static byte[] makeThumb(byte[] src) {
        return makeThumb(new ByteArrayResource(src), THUMB_MAX_EDGE);
    }

    public static byte[] makeThumb(InputStreamSource src) {
        return makeThumb(src, THUMB_MAX_EDGE);
    }

    /**
     * 长边 ≤maxEdge 的 JPEG。先按整数倍降采样解码（只读需要的像素），再平滑缩到目标尺寸。
     * 解码失败返回 null，不抛错、不阻断上传。
     */
    public static byte[] makeThumb(InputStreamSource src, int maxEdge) {
        BufferedImage img = readScaled(src, maxEdge);
        if (img == null) {
            return null;
        }
        return toJpeg(fit(img, maxEdge));
    }

    /**
     * 降采样解码：读出的图长边约为 maxEdge 的 1～2 倍。读不了返回 null。
     * 公开给头像裁剪用——它要在原图上按比例截一块，同样不能整张解码。
     */
    public static BufferedImage readScaled(InputStreamSource src, int maxEdge) {
        try (InputStream in = src.getInputStream();
             ImageInputStream iis = ImageIO.createImageInputStream(in)) {
            if (iis == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                // 只跳到目标的两倍左右，留一倍余量再平滑缩小，比直接按目标尺寸跳像素清楚
                int step = Math.max(1, Math.max(w, h) / maxEdge / 2);
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(step, step, 0, 0);
                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** 等比缩到长边 ≤maxEdge；JPEG 无 alpha 通道，先铺白底，免得透明区域变黑。 */
    public static BufferedImage fit(BufferedImage img, int maxEdge) {
        int w = img.getWidth();
        int h = img.getHeight();
        double scale = Math.min(1.0, (double) maxEdge / Math.max(w, h));
        int tw = Math.max(1, (int) Math.round(w * scale));
        int th = Math.max(1, (int) Math.round(h * scale));
        BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, tw, th);
        g.drawImage(img, 0, 0, tw, th, null);
        g.dispose();
        return out;
    }

    public static byte[] toJpeg(BufferedImage img) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(img, "jpg", bos);
            return bos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
}
