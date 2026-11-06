package com.example.fitapp.module.account.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 守卫：注销账号要删干净（ADR 0050）。扫全部迁移脚本里建过的表，每张都必须有人管——
 * 要么在 USER_TABLES（按 user_id 删），要么是 AccountDeletionMapper 顺着父表删的子表，
 * 要么明确不属于任何用户。新建表忘了登记，这里就红。
 */
class AccountTablesCoverageTest {

    /** AccountDeletionMapper 里顺着父表删的子表。 */
    private static final Set<String> CHILD_TABLES = Set.of(
            "workout_set", "workout_block", "plan_exercise_set", "plan_exercise", "food_combo_item", "diary_photo_ref");

    /** 不属于任何用户的表：用户表本身最后单独删；example_item 是骨架遗留的示例表。 */
    private static final Set<String> NOT_USER_DATA = Set.of("user", "example_item");

    private static final Pattern CREATE = Pattern.compile("CREATE TABLE (?:IF NOT EXISTS )?`?([a-z_]+)`?", Pattern.CASE_INSENSITIVE);

    @Test
    void 迁移脚本里的每张表都被注销流程覆盖() throws IOException {
        Set<String> tables = new TreeSet<>();
        try (Stream<Path> files = Files.list(Path.of("src/main/resources/db/migration"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".sql")).toList()) {
                Matcher m = CREATE.matcher(Files.readString(f));
                while (m.find()) {
                    tables.add(m.group(1).toLowerCase());
                }
            }
        }
        Set<String> covered = new HashSet<>(AccountService.USER_TABLES);
        covered.addAll(CHILD_TABLES);
        covered.addAll(NOT_USER_DATA);
        Set<String> missing = new TreeSet<>(tables);
        missing.removeAll(covered);
        assertTrue(missing.isEmpty(), "这些表没登记到注销流程里：" + missing);
        assertTrue(tables.size() > 20, "没扫到迁移脚本？只找到 " + tables);
    }
}
