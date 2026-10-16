package com.example.fitapp.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;

/**
 * 瞬时数据库故障的有限重试。
 *
 * <p><b>为什么存在</b>：本项目与 quizzy 共用同一个 MySQL 容器（ADR 0005）。容器被内核
 * OOM-killer 杀掉到重启完成之间有约十秒——这段时间里连接池持有的连接会集体失效，此刻的写请求
 * 抛 {@link TransientDataAccessException}（线上实测形态是 {@code RecoverableDataAccessException}
 * 包着 MySQL 的 {@code CommunicationsException}）。这类失败重试一次大多就能过去，不该以
 * 「系统繁忙」的面目直接甩给用户。</p>
 *
 * <p><b>什么才会被重试</b>：只认下面两类，其余 {@link DataAccessException} 一律原样抛出——
 * 尤其 {@code DataIntegrityViolationException}（唯一键冲突等）是业务性失败，重试毫无意义：</p>
 * <ul>
 *   <li>{@link TransientDataAccessException}：连接中断、锁等待超时、死锁等</li>
 *   <li>{@link RecoverableDataAccessException}：**线上真正遇到的那一个**——Spring 把 MySQL 的
 *       {@code CommunicationsException} 翻译成它</li>
 *   <li>{@link DataAccessResourceFailureException}：连数据库都拿不到
 *       （如池子里的连接全废时的 {@code CannotGetJdbcConnectionException}）</li>
 * </ul>
 *
 * <p><b>幂等前提</b>：重试意味着同一动作可能执行两次，调用方必须自己保证幂等。餐照上传靠
 * {@code meal_photo} 的唯一键 {@code uk_mp_user_date_meal} 成立——代码是「先查后写」，
 * 重复执行收敛到同一行。</p>
 *
 * <p><b>等多久</b>：不追求「扛过整段重启窗口」（那要十几秒，Tomcat 线程白占、用户干等），
 * 只吃掉「个别连接失效」这一类高频瞬时失败：两次重试分别等 400ms、1000ms。真撞上整段窗口时
 * 由 {@link GlobalExceptionHandler} 给出明确文案，用户重按一次即可。</p>
 */
@Slf4j
public final class TransientRetry {

    /** 第 n 次尝试失败后等待的毫秒数（尝试次数更多时复用最后一档）。 */
    private static final long[] BACKOFF_MS = {400L, 1000L};

    private TransientRetry() {
    }

    /**
     * 执行至多 {@code attempts} 次：命中可重试故障就退避后再来一次，其余异常立即冒泡。
     * 全部尝试用尽仍失败时，抛出**最后一次的原异常**——文案由全局异常处理器统一给。
     */
    public static void run(String what, int attempts, Runnable action) {
        for (int attempt = 1; ; attempt++) {
            try {
                action.run();
                return;
            } catch (DataAccessException e) {
                if (!retryable(e) || attempt >= attempts) {
                    throw e;
                }
                long wait = BACKOFF_MS[Math.min(attempt - 1, BACKOFF_MS.length - 1)];
                log.warn("{}：第 {}/{} 次尝试遇瞬时数据库故障，{}ms 后重试（{}）",
                        what, attempt, attempts, wait, e.toString());
                sleepQuietly(wait);
            }
        }
    }

    private static boolean retryable(DataAccessException e) {
        // ⚠️ RecoverableDataAccessException 必须**单独列出**：在 Spring 里它是 DataAccessException
        //    的直接子类，并不挂在 TransientDataAccessException 之下（名字里那个 "Recoverable"
        //    是「可恢复」的意思，与 Transient 分支平级）。只判 Transient 会漏掉线上真正的形态——
        //    这个坑最初就是被 DietServiceTest 里那两条重试用例抓出来的。
        return e instanceof TransientDataAccessException
                || e instanceof RecoverableDataAccessException
                || e instanceof DataAccessResourceFailureException;
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
