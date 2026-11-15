package com.example.fitapp.module.nutrition.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.fitapp.common.BusinessException;
import com.example.fitapp.common.PhotoData;
import com.example.fitapp.common.ResultCode;
import com.example.fitapp.module.account.dto.CropDTO;
import com.example.fitapp.module.account.image.AvatarImages;
import com.example.fitapp.module.nutrition.entity.Food;
import com.example.fitapp.module.nutrition.mapper.FoodMapper;
import com.example.fitapp.module.nutrition.storage.PhotoStorage;
import com.example.fitapp.module.nutrition.storage.PhotoUploadPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * 食物图片（ADR 0057）：每条食物最多一张，当封面用。上传时按裁剪框截成方图，只存方图（边长至多 800）
 * 和一张 240 的缩略图，不留原图。
 *
 * <p>文件放在上传目录：自建食物在 {@code {userId}/food/{imageKey}}（注销账号时随用户目录一起删），
 * 预置食物在 {@code preset/food/{imageKey}}。换图就换一个新的 imageKey，前端拿它当缓存版本号。</p>
 *
 * <p>自建食物的图片算进这个用户的照片空间；预置食物的图片是系统数据，不算进任何人的。
 * 换图、去掉图时旧文件直接删，不进回收站；删整条食物时图片跟着食物进回收站（见 {@link FoodTrash}）。</p>
 */
@Service
@RequiredArgsConstructor
public class FoodImageService {

    /** 方图边长上限（px）：详情页大图够用。原图更小就按原尺寸，不放大。 */
    static final int IMAGE_EDGE = 800;
    /** 缩略图边长（px）：列表里约 44px 见方，三倍屏也够清楚。 */
    static final int THUMB_EDGE = 240;
    /** 服务端裁得动的格式：JDK 读不了 WebP（和头像一样）。 */
    private static final Set<String> MIME = Set.of("image/jpeg", "image/png");
    private static final String JPEG = "image/jpeg";

    private final FoodMapper foodMapper;
    private final PhotoStorage photoStorage;
    private final PhotoUploadPolicy uploadPolicy;
    private final DietService dietService;

    /** 这条食物图片的相对键；没有图返回 null。 */
    public static String relKey(Food f) {
        return f == null || f.getImageKey() == null ? null : dirOf(f.getUserId()) + "/" + f.getImageKey();
    }

    private static String dirOf(Long ownerId) {
        return (ownerId == null ? "preset" : String.valueOf(ownerId)) + "/food";
    }

    /** 给自建食物设图（换图也走这里）。 */
    public Food setOwn(Long userId, Long foodId, MultipartFile file, CropDTO crop) {
        Food f = requireOwn(userId, foodId);
        return replace(f, file, crop);
    }

    /** 管理端给预置食物设图。 */
    public Food setPreset(Long foodId, MultipartFile file, CropDTO crop) {
        return replace(requirePreset(foodId), file, crop);
    }

    public void removeOwn(Long userId, Long foodId) {
        clear(requireOwn(userId, foodId));
    }

    public void removePreset(Long foodId) {
        clear(requirePreset(foodId));
    }

    /** 取图：kind＝thumb 取缩略图，其他取方图。预置的人人可取，自建的只有本人；没有图返回 null。 */
    public PhotoData image(Long userId, Long foodId, String kind) {
        Food f = foodMapper.selectById(foodId);
        if (f == null || (f.getUserId() != null && !f.getUserId().equals(userId))) {
            return null;
        }
        String key = relKey(f);
        if (key == null) {
            return null;
        }
        byte[] data = "thumb".equals(kind) ? photoStorage.readThumb(key) : photoStorage.readImage(key, JPEG);
        return data == null ? null : PhotoData.of(data, JPEG);
    }

    /** 复制出来的一份图片：已经落盘，新食物入库时写上这两个值。 */
    public record Copied(String imageKey, int bytes) {
    }

    /**
     * 「复制为自定义」时把来源的图片复制一份到这个用户名下（两边各自独立）。
     * 来源看不到、没有图、文件丢了都返回 null——复制食物不能因为图片失败。
     */
    public Copied copyFrom(Long userId, Long sourceId) {
        if (sourceId == null) {
            return null;
        }
        Food src = foodMapper.selectById(sourceId);
        if (src == null || (src.getUserId() != null && !src.getUserId().equals(userId))) {
            return null;
        }
        String from = relKey(src);
        byte[] image = from == null ? null : photoStorage.readImage(from, JPEG);
        if (image == null) {
            return null;
        }
        byte[] thumb = photoStorage.readThumb(from);
        int bytes = image.length + (thumb == null ? 0 : thumb.length);
        dietService.checkPhotoQuota(userId, bytes);
        String key = newKey();
        try {
            photoStorage.write(dirOf(userId) + "/" + key, JPEG, image, thumb);
        } catch (IOException e) {
            photoStorage.delete(dirOf(userId) + "/" + key);
            return null;
        }
        return new Copied(key, bytes);
    }

    /** 新食物没写进库时，把 {@link #copyFrom} 落的文件删掉。 */
    public void discard(Long userId, Copied copied) {
        if (copied != null) {
            photoStorage.delete(dirOf(userId) + "/" + copied.imageKey());
        }
    }

    private Food replace(Food f, MultipartFile file, CropDTO crop) {
        String mime = uploadPolicy.validate(file);
        if (!MIME.contains(mime)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "食物图片只支持 JPEG/PNG 图片");
        }
        AvatarImages.Info info = AvatarImages.probe(file);
        if (info == null) {
            throw unreadable();
        }
        Rectangle box = crop == null || crop.getX() == null || crop.getY() == null || crop.getSize() == null
                ? AvatarImages.centerSquare(info)
                : AvatarImages.clampSquare(info, crop.getX(), crop.getY(), crop.getSize());
        byte[] square = AvatarImages.square(file, info, box.x, box.y, box.width, Math.min(IMAGE_EDGE, box.width));
        byte[] thumb = AvatarImages.square(file, info, box.x, box.y, box.width, Math.min(THUMB_EDGE, box.width));
        if (square == null || thumb == null) {
            throw unreadable();
        }
        int bytes = square.length + thumb.length;
        int old = f.getImageBytes() == null ? 0 : f.getImageBytes();
        if (f.getUserId() != null) {
            dietService.checkPhotoQuota(f.getUserId(), bytes - (long) old);
        }

        String key = newKey();
        String rel = dirOf(f.getUserId()) + "/" + key;
        try {
            photoStorage.write(rel, JPEG, square, thumb);
        } catch (IOException e) {
            photoStorage.delete(rel);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "图片保存失败，请稍后重试");
        }
        String oldRel = relKey(f);
        try {
            writeColumns(f.getId(), key, bytes);
        } catch (RuntimeException e) {
            photoStorage.delete(rel);
            throw e;
        }
        // 库里已经指向新图，旧文件才能删
        if (oldRel != null) {
            photoStorage.delete(oldRel);
        }
        f.setImageKey(key);
        f.setImageBytes(bytes);
        f.setPreset(f.getUserId() == null);
        return f;
    }

    private void clear(Food f) {
        String oldRel = relKey(f);
        if (oldRel == null) {
            return;
        }
        writeColumns(f.getId(), null, 0);
        photoStorage.delete(oldRel);
    }

    private void writeColumns(Long id, String key, int bytes) {
        foodMapper.update(null, Wrappers.<Food>lambdaUpdate()
                .eq(Food::getId, id)
                .set(Food::getImageKey, key)
                .set(Food::getImageBytes, bytes));
    }

    private Food requireOwn(Long userId, Long id) {
        Food f = foodMapper.selectById(id);
        if (f == null || (f.getUserId() != null && !userId.equals(f.getUserId()))) {
            throw new BusinessException(ResultCode.NOT_FOUND, "食物不存在");
        }
        if (f.getUserId() == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "预置食物的图片由管理员设置");
        }
        return f;
    }

    private Food requirePreset(Long id) {
        Food f = id == null ? null : foodMapper.selectById(id);
        if (f == null || f.getUserId() != null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "预置食物不存在");
        }
        return f;
    }

    private static String newKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static BusinessException unreadable() {
        return new BusinessException(ResultCode.BAD_REQUEST, "这张图读不出来，换一张试试");
    }
}
