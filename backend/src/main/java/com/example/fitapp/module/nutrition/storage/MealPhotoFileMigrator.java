package com.example.fitapp.module.nutrition.storage;

import com.example.fitapp.module.nutrition.entity.MealPhoto;
import com.example.fitapp.module.nutrition.mapper.MealPhotoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 把库里遗留的餐照 BLOB 搬到文件系统（ADR 0026 的搬迁步骤，随应用启动执行）。
 *
 * <p>为什么放在应用里而不是迁移脚本里：写文件需要文件系统与挂载卷，而这些只有应用进程才有；
 * Flyway 的 SQL 迁移阶段够不着。所以 V25 只加列、放开 NOT NULL，实际落盘由这里完成。
 *
 * <p>三条设计约束（改动前先读）：
 * <ul>
 *   <li><b>幂等</b>：按 {@code image IS NOT NULL} 挑待搬行，搬完即把两列置 NULL——中断后重跑
 *       不会重复劳动，也不会漏。</li>
 *   <li><b>不丢数据</b>：先落盘、成功之后才清库里的 BLOB。任何一步失败，原图都还在库中，
 *       下次启动自动重试。</li>
 *   <li><b>不阻断启动</b>：单个失败只记日志、继续搬下一张——图片搬不动不该让整个服务起不来。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "fitapp.storage.migrate-on-start", havingValue = "true", matchIfMissing = true)
public class MealPhotoFileMigrator implements ApplicationRunner {

    private final MealPhotoMapper mealPhotoMapper;
    private final PhotoStorage storage;

    @Override
    public void run(ApplicationArguments args) {
        List<MealPhoto> pending;
        try {
            pending = mealPhotoMapper.selectPendingMigration();
        } catch (Exception e) {
            // 表还没建好（首次部署 Flyway 刚建库）等情况：跳过，不影响启动
            log.warn("查询待搬运餐照失败，跳过本次搬迁：{}", e.toString());
            return;
        }
        if (pending.isEmpty()) {
            return;   // 常态——绝大多数启动都走到这里，不做任何额外动作
        }

        log.info("发现 {} 张餐照仍存在库中，开始搬迁到文件系统：{}", pending.size(), storage.root());

        // ⚠️ 最关键的一道闸：如果存储目录其实没有挂载宿主机卷，落盘会写进容器可写层，
        //    容器一重建就没了——而那时库里的 BLOB 已被清空，图片将永久丢失。
        //    所以宁可这次不搬（图片原样留在库中），也不要搬到一个会消失的地方。
        if (!isMountPoint(storage.root())) {
            log.error("已中止餐照搬迁：存储目录 {} 不是挂载点，极可能容器没挂载宿主机目录。"
                            + "此时落盘会写进容器可写层、容器重建即丢，而库中的原图已被清空。"
                            + "图片原样保留在库中；挂好卷后重启即会自动继续。",
                    storage.root());
            return;
        }

        int ok = 0;
        int failed = 0;
        for (MealPhoto p : pending) {
            String relKey = PhotoStorage.relKey(p.getUserId(), p.getLogDate(), p.getMeal());
            try {
                storage.write(relKey, p.getMime(), p.getImage(), p.getThumb());
                mealPhotoMapper.markMigrated(p.getId(), relKey,
                        p.getThumb() == null ? null : p.getThumb().length);
                ok++;
            } catch (Exception e) {
                failed++;
                log.error("餐照搬运失败 id={}（原图仍留在库中，下次启动会重试）", p.getId(), e);
            }
        }
        log.info("餐照搬迁结束：成功 {} 张、失败 {} 张（失败的仍保留在库中）", ok, failed);
    }

    /**
     * 判断目录是不是挂载点：读 Linux 的 {@code /proc/mounts}（每行第二列是挂载点）。
     * <p>读不到（非 Linux、或权限受限）时**返回 true 照常搬**——本地开发与测试要能跑；
     * 而容器里这个文件一定在，所以生产场景的保护不受影响。
     */
    private boolean isMountPoint(Path target) {
        Path proc = Paths.get("/proc/mounts");
        if (!Files.isReadable(proc)) {
            log.warn("读不到 /proc/mounts（非 Linux？），跳过挂载自检；请自行确认 {} 已挂载宿主机目录",
                    target);
            return true;
        }
        String want = target.toAbsolutePath().normalize().toString();
        try {
            for (String line : Files.readAllLines(proc)) {
                String[] parts = line.split(" ");
                if (parts.length > 1 && parts[1].equals(want)) {
                    return true;
                }
            }
        } catch (IOException e) {
            log.warn("解析 /proc/mounts 失败，跳过挂载自检：{}", e.toString());
            return true;
        }
        return false;
    }
}
