package com.example.fitapp.module.account.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.fitapp.common.BusinessException;
import com.example.fitapp.common.PhotoData;
import com.example.fitapp.common.ResultCode;
import com.example.fitapp.module.account.dto.CropDTO;
import com.example.fitapp.module.account.entity.Avatar;
import com.example.fitapp.module.account.image.AvatarImages;
import com.example.fitapp.module.account.mapper.AvatarMapper;
import com.example.fitapp.module.account.vo.AvatarVO;
import com.example.fitapp.module.nutrition.service.DietService;
import com.example.fitapp.module.nutrition.storage.PhotoStorage;
import com.example.fitapp.module.nutrition.storage.PhotoUploadPolicy;
import com.example.fitapp.module.user.entity.User;
import com.example.fitapp.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.awt.Rectangle;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 头像与历史头像（ADR 0051）。每张都留原图，另存三份小图：
 * 缩略图（历史列表里完整显示原图）、预览图（裁剪框里用，不必拉几十 MB 的原图）、裁剪结果（头像本身）。
 *
 * <p>历史里一项对应一张原图。切回某张或重新裁剪当前头像都是「重裁」：覆盖这一项的裁剪结果、
 * 刷新它的使用时刻，于是它移到最前面，不另起一条。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AvatarService {

    /** 头像本身的边长（px）。 */
    static final int CROP_EDGE = 512;
    /** 裁剪框里显示的预览图长边（px）：手机屏够清楚，又比原图小得多。 */
    static final int VIEW_EDGE = 1280;
    /** 服务端解得动的格式：JDK 读不了 WebP，传上来也裁不了。 */
    private static final Set<String> AVATAR_MIME = Set.of("image/jpeg", "image/png");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final AvatarMapper avatarMapper;
    private final UserMapper userMapper;
    private final PhotoStorage photoStorage;
    private final PhotoUploadPolicy uploadPolicy;
    private final DietService dietService;
    private final AvatarTrash avatarTrash;

    /** 历史头像：最近用过的在前。 */
    public List<AvatarVO> list(Long userId) {
        Long current = currentId(userId);
        return avatarMapper.selectList(Wrappers.<Avatar>lambdaQuery()
                        .eq(Avatar::getUserId, userId)
                        .orderByDesc(Avatar::getUsedAt)
                        .orderByDesc(Avatar::getId))
                .stream().map(a -> toVO(a, current)).toList();
    }

    /** 传一张新头像并按给定的框裁好，直接设为当前头像。 */
    public AvatarVO upload(Long userId, MultipartFile file, CropDTO crop) {
        String mime = uploadPolicy.validate(file);
        if (!AVATAR_MIME.contains(mime)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "头像只支持 JPEG/PNG 图片");
        }
        AvatarImages.Info info = AvatarImages.probe(file);
        if (info == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "这张图读不出来，换一张试试");
        }
        Rectangle box = cropOf(info, crop);
        byte[] thumb = AvatarImages.whole(file, info, PhotoUploadPolicy.THUMB_MAX_EDGE);
        byte[] view = AvatarImages.whole(file, info, VIEW_EDGE);
        byte[] square = AvatarImages.square(file, info, box.x, box.y, box.width, CROP_EDGE);
        if (thumb == null || view == null || square == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "这张图读不出来，换一张试试");
        }
        long size = file.getSize();
        int extra = thumb.length + view.length + square.length;
        dietService.checkPhotoQuota(userId, size + extra);

        String relKey = userId + "/avatar/" + UUID.randomUUID().toString().replace("-", "");
        try {
            photoStorage.write(relKey, mime, file, thumb);
            photoStorage.writeSide(relKey, PhotoStorage.VIEW_SUFFIX, view);
            photoStorage.writeSide(relKey, PhotoStorage.CROP_SUFFIX, square);
        } catch (IOException e) {
            photoStorage.delete(relKey);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "头像保存失败，请稍后重试");
        }

        Avatar a = new Avatar();
        a.setUserId(userId);
        a.setPath(relKey);
        a.setMime(mime);
        a.setWidth(info.width());
        a.setHeight(info.height());
        a.setSizeBytes((int) size);
        a.setExtraBytes(extra);
        a.setCropX(box.x);
        a.setCropY(box.y);
        a.setCropSize(box.width);
        a.setUsedAt(LocalDateTime.now(ZONE));
        try {
            avatarMapper.insert(a);
            setCurrent(userId, a.getId());
        } catch (RuntimeException e) {
            // 库没写成就别留文件：反过来是「文件在、记录没有」，只是孤儿，但能清就清
            photoStorage.delete(relKey);
            throw e;
        }
        return toVO(a, a.getId());
    }

    /** 重裁：切回历史里的某张，或重新裁剪当前头像。新结果覆盖原来的，这一项移到最前面并设为当前。 */
    public AvatarVO recrop(Long userId, Long id, CropDTO crop) {
        Avatar a = requireOwn(userId, id);
        Path original = photoStorage.imageFile(a.getPath(), a.getMime());
        if (original == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "原图找不到了");
        }
        InputStreamSource src = new FileSystemResource(original);
        AvatarImages.Info info = AvatarImages.probe(src);
        if (info == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "原图读不出来");
        }
        Rectangle box = cropOf(info, crop);
        byte[] square = AvatarImages.square(src, info, box.x, box.y, box.width, CROP_EDGE);
        if (square == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "原图读不出来");
        }
        byte[] oldCrop = photoStorage.readSide(a.getPath(), PhotoStorage.CROP_SUFFIX);
        int extra = (a.getExtraBytes() == null ? 0 : a.getExtraBytes())
                - (oldCrop == null ? 0 : oldCrop.length) + square.length;
        try {
            photoStorage.writeSide(a.getPath(), PhotoStorage.CROP_SUFFIX, square);
        } catch (IOException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "头像保存失败，请稍后重试");
        }
        a.setCropX(box.x);
        a.setCropY(box.y);
        a.setCropSize(box.width);
        a.setExtraBytes(extra);
        a.setUsedAt(LocalDateTime.now(ZONE));
        avatarMapper.updateById(a);
        setCurrent(userId, a.getId());
        return toVO(a, a.getId());
    }

    /** 取图：crop＝头像本身，thumb＝历史列表里的完整原图缩略，view＝裁剪框里的预览，original＝原图。 */
    public PhotoData image(Long userId, Long id, String kind) {
        Avatar a = avatarMapper.selectOne(Wrappers.<Avatar>lambdaQuery()
                .eq(Avatar::getId, id)
                .eq(Avatar::getUserId, userId));
        if (a == null) {
            return null;
        }
        if ("original".equals(kind)) {
            Path p = photoStorage.imageFile(a.getPath(), a.getMime());
            return p == null ? null : PhotoData.of(p, a.getMime());
        }
        byte[] data = switch (kind) {
            case "thumb" -> photoStorage.readThumb(a.getPath());
            case "view" -> photoStorage.readSide(a.getPath(), PhotoStorage.VIEW_SUFFIX);
            default -> photoStorage.readSide(a.getPath(), PhotoStorage.CROP_SUFFIX);
        };
        return data == null ? null : PhotoData.of(data, "image/jpeg");
    }

    /** 从历史里删一张：进回收站（ADR 0047），文件留着。删的是当前头像就回到首字母，不自动换成上一张。 */
    @Transactional
    public void delete(Long userId, Long id) {
        Avatar a = requireOwn(userId, id);
        avatarTrash.put(userId, a);
        avatarMapper.deleteById(a.getId());
        if (a.getId().equals(currentId(userId))) {
            setCurrent(userId, null);
        }
    }

    /** 「不用头像」：回到首字母，历史里的都还在。 */
    public void clearCurrent(Long userId) {
        setCurrent(userId, null);
    }

    private Rectangle cropOf(AvatarImages.Info info, CropDTO crop) {
        if (crop == null || crop.getX() == null || crop.getY() == null || crop.getSize() == null) {
            return AvatarImages.centerSquare(info);
        }
        return AvatarImages.clampSquare(info, crop.getX(), crop.getY(), crop.getSize());
    }

    private Avatar requireOwn(Long userId, Long id) {
        Avatar a = avatarMapper.selectOne(Wrappers.<Avatar>lambdaQuery()
                .eq(Avatar::getId, id)
                .eq(Avatar::getUserId, userId));
        if (a == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "头像不存在");
        }
        return a;
    }

    private Long currentId(Long userId) {
        User u = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .select(User::getId, User::getAvatarId)
                .eq(User::getId, userId));
        return u == null ? null : u.getAvatarId();
    }

    private void setCurrent(Long userId, Long avatarId) {
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .set(User::getAvatarId, avatarId)
                .eq(User::getId, userId));
    }

    private static AvatarVO toVO(Avatar a, Long current) {
        AvatarVO vo = new AvatarVO();
        vo.setId(a.getId());
        vo.setWidth(a.getWidth());
        vo.setHeight(a.getHeight());
        vo.setCropX(a.getCropX());
        vo.setCropY(a.getCropY());
        vo.setCropSize(a.getCropSize());
        vo.setCurrent(a.getId().equals(current));
        vo.setUsedAt(a.getUsedAt());
        return vo;
    }
}
