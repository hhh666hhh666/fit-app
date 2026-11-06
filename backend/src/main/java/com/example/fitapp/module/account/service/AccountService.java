package com.example.fitapp.module.account.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.fitapp.common.BusinessException;
import com.example.fitapp.common.ResultCode;
import com.example.fitapp.module.account.dto.DeleteAccountDTO;
import com.example.fitapp.module.account.dto.NicknameDTO;
import com.example.fitapp.module.account.dto.PasswordDTO;
import com.example.fitapp.module.account.dto.UsernameDTO;
import com.example.fitapp.module.account.mapper.AccountDeletionMapper;
import com.example.fitapp.module.account.vo.AccountVO;
import com.example.fitapp.module.auth.vo.LoginVO;
import com.example.fitapp.module.nutrition.storage.PhotoStorage;
import com.example.fitapp.module.user.entity.User;
import com.example.fitapp.module.user.mapper.UserMapper;
import com.example.fitapp.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * 账号页（ADR 0050）：昵称、改用户名、改密码、注销。头像见 AvatarService（ADR 0051）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    /** 昵称上限按字符数（code point）算：emoji 也算一个。 */
    static final int NICKNAME_MAX = 20;

    /**
     * 带 user_id 的表，注销时逐张按 user_id 删（ADR 0050）。没有 user_id 的子表在 AccountDeletionMapper 里顺着父表删。
     * ⚠️ 新建带 user_id 的表要加进来——AccountTablesCoverageTest 会扫迁移脚本兜底。
     */
    public static final List<String> USER_TABLES = List.of(
            "food", "meal_log", "meal_photo", "diet_daily", "user_setting", "food_recognition",
            "exercise", "agent_token", "workout", "food_combo", "preset_hidden", "plan",
            "week_schedule", "user_profile", "trash_item", "diary_entry", "diary_photo", "body_record",
            "avatar");

    private final UserMapper userMapper;
    private final AccountDeletionMapper deletionMapper;
    private final PhotoStorage photoStorage;
    private final JwtUtil jwtUtil;
    private final TransactionTemplate tx;

    public AccountVO get(Long userId) {
        return toVO(requireUser(userId));
    }

    /** 改昵称；空或全是空白就是清掉。 */
    public AccountVO updateNickname(Long userId, NicknameDTO dto) {
        requireUser(userId);
        String nickname = dto.getNickname() == null ? "" : dto.getNickname().strip();
        if (nickname.codePointCount(0, nickname.length()) > NICKNAME_MAX) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "昵称最多 " + NICKNAME_MAX + " 个字");
        }
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .set(User::getNickname, nickname.isEmpty() ? null : nickname)
                .eq(User::getId, userId));
        return get(userId);
    }

    /** 改用户名：要当前密码；旧名立刻释放；登录令牌认的是用户 id，所以哪台设备都不会掉线。 */
    public AccountVO updateUsername(Long userId, UsernameDTO dto) {
        User user = requireUser(userId);
        checkPassword(user, dto.getPassword());
        String username = dto.getUsername().trim();
        if (username.equals(user.getUsername())) {
            return toVO(user);
        }
        Long taken = userMapper.selectCount(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, username)
                .ne(User::getId, userId));
        if (taken != null && taken > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "用户名已存在");
        }
        try {
            userMapper.update(null, Wrappers.<User>lambdaUpdate()
                    .set(User::getUsername, username)
                    .eq(User::getId, userId));
        } catch (DuplicateKeyException e) {
            // 查完到写入之间被别人抢注了
            throw new BusinessException(ResultCode.CONFLICT, "用户名已存在");
        }
        return get(userId);
    }

    /**
     * 改密码：要旧密码；令牌版本加一，其他设备的旧令牌随之失效，本机拿返回的新令牌继续用。
     */
    public LoginVO changePassword(Long userId, PasswordDTO dto) {
        User user = requireUser(userId);
        checkPassword(user, dto.getOldPassword());
        if (ENCODER.matches(dto.getNewPassword(), user.getPasswordHash())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "新密码和旧密码一样");
        }
        int next = (user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1;
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .set(User::getPasswordHash, ENCODER.encode(dto.getNewPassword()))
                .set(User::getTokenVersion, next)
                .eq(User::getId, userId));
        return new LoginVO(jwtUtil.generateToken(userId, next), userId, user.getUsername());
    }

    /**
     * 注销：密码 ＋ 手打用户名两道确认，然后立刻彻底删除全部数据和图片文件，不进回收站。
     * 库在一个事务里删完，文件在事务提交之后再删——反过来，事务回滚时图片却已经没了。
     */
    public void delete(Long userId, DeleteAccountDTO dto) {
        User user = requireUser(userId);
        checkPassword(user, dto.getPassword());
        if (!user.getUsername().equals(dto.getUsername().trim())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "用户名不对");
        }
        tx.executeWithoutResult(status -> {
            deletionMapper.deleteWorkoutSets(userId);
            deletionMapper.deleteWorkoutBlocks(userId);
            deletionMapper.deletePlanExerciseSets(userId);
            deletionMapper.deletePlanExercises(userId);
            deletionMapper.deleteFoodComboItems(userId);
            deletionMapper.deleteDiaryPhotoRefs(userId);
            for (String table : USER_TABLES) {
                deletionMapper.deleteByUser(table, userId);
            }
            deletionMapper.deleteUser(userId);
        });
        photoStorage.deleteUserDir(userId);
        log.info("账号已注销：userId={}", userId);
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return user;
    }

    private static void checkPassword(User user, String password) {
        if (password == null || !ENCODER.matches(password, user.getPasswordHash())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "密码不正确");
        }
    }

    private static AccountVO toVO(User user) {
        AccountVO vo = new AccountVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatarId(user.getAvatarId());
        return vo;
    }
}
