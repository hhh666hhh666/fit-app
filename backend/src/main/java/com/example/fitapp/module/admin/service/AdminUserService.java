package com.example.fitapp.module.admin.service;

import com.example.fitapp.common.BusinessException;
import com.example.fitapp.common.ResultCode;
import com.example.fitapp.module.admin.mapper.AdminUserMapper;
import com.example.fitapp.module.admin.vo.AdminMeVO;
import com.example.fitapp.module.admin.vo.AdminUserVO;
import com.example.fitapp.module.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** 管理端的用户：只读列表，以及「我是谁」（ADR 0048）。禁用、删除不做。 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final AdminUserMapper adminUserMapper;

    public List<AdminUserVO> list() {
        return adminUserMapper.listWithStats();
    }

    /** 能走到这里说明已经过了管理员校验；只回身份，不回密码哈希。 */
    public AdminMeVO me(Long userId) {
        User user = adminUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return new AdminMeVO(user.getId(), user.getUsername());
    }
}
