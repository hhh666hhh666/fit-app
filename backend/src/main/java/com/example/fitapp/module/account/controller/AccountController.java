package com.example.fitapp.module.account.controller;

import com.example.fitapp.common.Result;
import com.example.fitapp.module.account.dto.DeleteAccountDTO;
import com.example.fitapp.module.account.dto.NicknameDTO;
import com.example.fitapp.module.account.dto.PasswordDTO;
import com.example.fitapp.module.account.dto.UsernameDTO;
import com.example.fitapp.module.account.service.AccountService;
import com.example.fitapp.module.account.vo.AccountVO;
import com.example.fitapp.module.auth.vo.LoginVO;
import com.example.fitapp.security.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 账号页（ADR 0050）。头像接口在 AvatarController。 */
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    public Result<AccountVO> get() {
        return Result.success(accountService.get(UserContext.requireUserId()));
    }

    @PutMapping("/nickname")
    public Result<AccountVO> nickname(@RequestBody NicknameDTO body) {
        return Result.success(accountService.updateNickname(UserContext.requireUserId(), body));
    }

    @PutMapping("/username")
    public Result<AccountVO> username(@Valid @RequestBody UsernameDTO body) {
        return Result.success(accountService.updateUsername(UserContext.requireUserId(), body));
    }

    /** 返回新令牌：本机换上它继续登录，其他设备的旧令牌已失效。 */
    @PutMapping("/password")
    public Result<LoginVO> password(@Valid @RequestBody PasswordDTO body) {
        return Result.success(accountService.changePassword(UserContext.requireUserId(), body));
    }

    /** 注销：立刻彻底删除，不进回收站。用 POST 是因为要带密码，DELETE 带请求体不可靠。 */
    @PostMapping("/delete")
    public Result<Void> delete(@Valid @RequestBody DeleteAccountDTO body) {
        accountService.delete(UserContext.requireUserId(), body);
        return Result.success();
    }
}
