package com.example.fitapp.module.exercise.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * 动作动图与缩略图的文件（ADR 0055）：放在上传目录的 {@code exercise-media/} 下，由
 * {@code scripts/exercise-dataset/sync-media.sh} 放进去，应用只读不写。
 *
 * <pre>
 *   {photo-dir}/exercise-media/{media}.gif   动图（180×180）
 *   {photo-dir}/exercise-media/{media}.jpg   缩略图（180×180）
 * </pre>
 *
 * <p>⚠️ 媒体版权归 Gym visual，不进公开仓库，也只给登录用户看（接口走鉴权）。
 * 与用户图片同在一个根目录下，但用户图片都在 {@code {userId}/} 这种数字目录里，互不相碰。
 */
@Component
public class ExerciseMediaStorage {

    public static final String DIR = "exercise-media";

    /** 文件键只认数据集的格式（编号-媒体号），也就杜绝了 ../ 之类的路径。 */
    public static final Pattern KEY = Pattern.compile("^\\d{4}-[A-Za-z0-9]{4,16}$");

    public enum Kind {
        GIF("gif", "image/gif"),
        THUMB("jpg", "image/jpeg");

        public final String ext;
        public final String mime;

        Kind(String ext, String mime) {
            this.ext = ext;
            this.mime = mime;
        }
    }

    private final Path root;

    public ExerciseMediaStorage(@Value("${fitapp.storage.photo-dir:/data/uploads}") String dir) {
        this.root = Paths.get(dir).toAbsolutePath().normalize().resolve(DIR);
    }

    /** 文件路径；键不合法或文件不存在返回 null。 */
    public Path find(String key, Kind kind) {
        if (key == null || !KEY.matcher(key).matches()) {
            return null;
        }
        Path p = root.resolve(key + "." + kind.ext);
        return Files.isReadable(p) ? p : null;
    }
}
