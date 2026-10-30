package com.example.fitapp.module.nutrition.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 餐照的**文件存储**（ADR 0026）：图片实体落在服务器磁盘上，库里只留一个相对路径。
 *
 * <p>布局（{@code root} 由 {@code fitapp.storage.photo-dir} 指定）：
 * <pre>
 *   {root}/{userId}/{logDate}/{meal}.{ext}        原图
 *   {root}/{userId}/{logDate}/{meal}.thumb.jpg    缩略图（可选）
 * </pre>
 *
 * <p>⚠️ 路径键刻意用 {@code (userId, logDate, meal)} —— 与数据库唯一键
 * {@code uk_mp_user_date_meal} 同构，所以「重拍即替换」天然是**同路径覆盖**，
 * 不需要查旧行来定位文件，也不会随重拍而堆积孤儿。
 */
@Slf4j
@Component
public class PhotoStorage {

    /** 候选取值：换过格式（重拍时 mime 变了）会留下旧扩展名的文件，写入前要一并清掉。 */
    private static final List<String> EXT_CANDIDATES = List.of("jpg", "png", "webp");
    private static final String THUMB_SUFFIX = ".thumb.jpg";

    private final Path root;

    public PhotoStorage(@Value("${fitapp.storage.photo-dir:/data/uploads}") String dir) {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
    }

    /** 存储根目录（搬运器拿它做「是否真的挂了卷」的自检）。 */
    public Path root() {
        return root;
    }

    /** 老照片的相对键（不含扩展名）：{userId}/{logDate}/{meal}。仅由服务端用库内字段拼成，不接受外部输入。 */
    public static String relKey(Long userId, LocalDate date, String meal) {
        return userId + "/" + date + "/" + meal;
    }

    /** 新照片的相对键：一餐可以有多张（ADR 0045），带随机串才不会覆盖同餐的其他图。 */
    public static String uniqueKey(Long userId, LocalDate date, String meal) {
        return relKey(userId, date, meal) + "-" + UUID.randomUUID().toString().replace("-", "");
    }

    /** mime → 扩展名。 */
    public static String extOf(String mime) {
        return switch (mime == null ? "" : mime) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }

    /**
     * 落盘（覆盖写）。同一 key 的旧文件先清掉——重拍时格式可能从 jpg 换成 png，
     * 只写不清会留下一个永远读不到的孤儿文件。
     */
    public void write(String relKey, String mime, byte[] image, byte[] thumb) throws IOException {
        Files.createDirectories(root.resolve(relKey).getParent());
        purge(relKey);
        Files.write(root.resolve(relKey + "." + extOf(mime)), image);
        if (thumb != null && thumb.length > 0) {
            Files.write(root.resolve(relKey + THUMB_SUFFIX), thumb);
        }
    }

    /** 读原图；文件缺失（或历史遗留）返回 null，由调用方转 404。 */
    public byte[] readImage(String relKey, String mime) {
        return readOrNull(root.resolve(relKey + "." + extOf(mime)));
    }

    /** 读缩略图；不存在返回 null——读取端据此回退原图。 */
    public byte[] readThumb(String relKey) {
        return readOrNull(root.resolve(relKey + THUMB_SUFFIX));
    }

    /**
     * 删除该 key 下的全部文件（原图的所有候选扩展名 ＋ 缩略图）。
     * ⚠️ 调用时机是**数据库行已删成功之后**——失败最坏只留孤儿文件（无害），
     * 反过来则会留下「记录指向不存在的图」（有害）。
     */
    public void delete(String relKey) {
        purge(relKey);
    }

    private void purge(String relKey) {
        for (String ext : EXT_CANDIDATES) {
            deleteQuietly(root.resolve(relKey + "." + ext));
        }
        deleteQuietly(root.resolve(relKey + THUMB_SUFFIX));
    }

    private byte[] readOrNull(Path p) {
        try {
            return Files.isReadable(p) ? Files.readAllBytes(p) : null;
        } catch (IOException e) {
            log.warn("读取餐照文件失败：{}", p, e);
            return null;
        }
    }

    private void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException e) {
            // 删不掉只意味着留下一个孤儿文件，不该让业务失败
            log.warn("删除餐照文件失败（留下孤儿文件，无害）：{}", p, e);
        }
    }
}
