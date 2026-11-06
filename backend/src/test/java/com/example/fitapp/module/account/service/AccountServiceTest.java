package com.example.fitapp.module.account.service;

import com.example.fitapp.common.BusinessException;
import com.example.fitapp.common.ResultCode;
import com.example.fitapp.module.account.dto.DeleteAccountDTO;
import com.example.fitapp.module.account.dto.NicknameDTO;
import com.example.fitapp.module.account.dto.PasswordDTO;
import com.example.fitapp.module.account.dto.UsernameDTO;
import com.example.fitapp.module.account.mapper.AccountDeletionMapper;
import com.example.fitapp.module.auth.vo.LoginVO;
import com.example.fitapp.module.nutrition.storage.PhotoStorage;
import com.example.fitapp.module.user.entity.User;
import com.example.fitapp.module.user.mapper.UserMapper;
import com.example.fitapp.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @org.junit.jupiter.api.BeforeAll
    static void initTableInfo() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                User.class);
    }

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    private static final String HASH = ENCODER.encode("secret123");

    @Mock
    private UserMapper userMapper;
    @Mock
    private AccountDeletionMapper deletionMapper;
    @Mock
    private PhotoStorage photoStorage;

    private final JwtUtil jwtUtil = new JwtUtil("test-secret-for-unit-tests-0123456789abcdef0123456789abcdef", 7);

    private AccountService service() {
        return new AccountService(userMapper, deletionMapper, photoStorage, jwtUtil,
                new TransactionTemplate(mock(PlatformTransactionManager.class)));
    }

    private User user() {
        User u = new User();
        u.setId(7L);
        u.setUsername("lzh");
        u.setPasswordHash(HASH);
        u.setTokenVersion(2);
        return u;
    }

    @Test
    void 昵称按字符数算_emoji_也算一个() {
        when(userMapper.selectById(7L)).thenReturn(user());
        NicknameDTO ok = new NicknameDTO();
        ok.setNickname("💪".repeat(20));
        service().updateNickname(7L, ok);

        NicknameDTO tooLong = new NicknameDTO();
        tooLong.setNickname("字".repeat(21));
        assertThrows(BusinessException.class, () -> service().updateNickname(7L, tooLong));
    }

    @Test
    void 改用户名_密码错就拒绝() {
        when(userMapper.selectById(7L)).thenReturn(user());
        UsernameDTO dto = new UsernameDTO();
        dto.setUsername("newname");
        dto.setPassword("wrong");
        BusinessException e = assertThrows(BusinessException.class, () -> service().updateUsername(7L, dto));
        assertEquals("密码不正确", e.getMessage());
        verify(userMapper, never()).update(any(), any());
    }

    @Test
    void 改用户名_被别人占了就冲突() {
        when(userMapper.selectById(7L)).thenReturn(user());
        when(userMapper.selectCount(any())).thenReturn(1L);
        UsernameDTO dto = new UsernameDTO();
        dto.setUsername("taken");
        dto.setPassword("secret123");
        BusinessException e = assertThrows(BusinessException.class, () -> service().updateUsername(7L, dto));
        assertEquals(ResultCode.CONFLICT.getCode(), e.getCode());
    }

    /** 改密码让版本加一：新令牌带新版本，旧令牌（带旧版本）在拦截器里被拒（ADR 0050）。 */
    @Test
    void 改密码_令牌版本加一并签发新令牌() {
        when(userMapper.selectById(7L)).thenReturn(user());
        PasswordDTO dto = new PasswordDTO();
        dto.setOldPassword("secret123");
        dto.setNewPassword("another456");

        LoginVO vo = service().changePassword(7L, dto);

        assertEquals(3, jwtUtil.parseToken(vo.token()).tokenVersion());
        verify(userMapper).update(any(), any());
    }

    @Test
    void 改密码_旧密码错就拒绝() {
        when(userMapper.selectById(7L)).thenReturn(user());
        PasswordDTO dto = new PasswordDTO();
        dto.setOldPassword("wrong");
        dto.setNewPassword("another456");
        assertThrows(BusinessException.class, () -> service().changePassword(7L, dto));
        verify(userMapper, never()).update(any(), any());
    }

    @Test
    void 注销_用户名打错不删任何东西() {
        when(userMapper.selectById(7L)).thenReturn(user());
        DeleteAccountDTO dto = new DeleteAccountDTO();
        dto.setPassword("secret123");
        dto.setUsername("lz");
        assertThrows(BusinessException.class, () -> service().delete(7L, dto));
        verify(deletionMapper, never()).deleteUser(anyLong());
        verify(photoStorage, never()).deleteUserDir(anyLong());
    }

    @Test
    void 注销_删光所有表再删图片目录() {
        when(userMapper.selectById(7L)).thenReturn(user());
        DeleteAccountDTO dto = new DeleteAccountDTO();
        dto.setPassword("secret123");
        dto.setUsername("lzh");

        service().delete(7L, dto);

        for (String t : AccountService.USER_TABLES) {
            verify(deletionMapper).deleteByUser(t, 7L);
        }
        verify(deletionMapper).deleteWorkoutSets(7L);
        verify(deletionMapper).deleteDiaryPhotoRefs(7L);
        verify(deletionMapper).deleteUser(7L);
        verify(photoStorage).deleteUserDir(7L);
    }

    @Test
    void 注销_密码错不删() {
        when(userMapper.selectById(7L)).thenReturn(user());
        DeleteAccountDTO dto = new DeleteAccountDTO();
        dto.setPassword("nope");
        dto.setUsername("lzh");
        assertThrows(BusinessException.class, () -> service().delete(7L, dto));
        verify(deletionMapper, never()).deleteByUser(anyString(), anyLong());
    }
}
