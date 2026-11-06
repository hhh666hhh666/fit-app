package com.example.fitapp.module.account.controller;

import com.example.fitapp.common.PhotoData;
import com.example.fitapp.common.Result;
import com.example.fitapp.module.account.dto.CropDTO;
import com.example.fitapp.module.account.service.AvatarService;
import com.example.fitapp.module.account.vo.AvatarVO;
import com.example.fitapp.security.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 头像与历史头像（ADR 0051）。 */
@RestController
@RequestMapping("/api/account/avatars")
@RequiredArgsConstructor
public class AvatarController {

    private final AvatarService avatarService;

    @GetMapping
    public Result<List<AvatarVO>> list() {
        return Result.success(avatarService.list(UserContext.requireUserId()));
    }

    /** 传新头像（multipart：file ＋ 裁剪框 x、y、size，按摆正后的原图像素）；直接设为当前头像。 */
    @PostMapping
    public Result<AvatarVO> upload(@RequestParam("file") MultipartFile file,
                                   @RequestParam(required = false) Integer x,
                                   @RequestParam(required = false) Integer y,
                                   @RequestParam(required = false) Integer size) {
        CropDTO crop = new CropDTO();
        crop.setX(x);
        crop.setY(y);
        crop.setSize(size);
        return Result.success(avatarService.upload(UserContext.requireUserId(), file, crop));
    }

    /** 重裁：切回历史里的某张或重新裁剪当前头像，设为当前并移到最前面。 */
    @PutMapping("/{id}/crop")
    public Result<AvatarVO> recrop(@PathVariable Long id, @Valid @RequestBody CropDTO body) {
        return Result.success(avatarService.recrop(UserContext.requireUserId(), id, body));
    }

    /** 直出图片；kind＝crop（默认，头像本身）／thumb／view／original。 */
    @GetMapping("/{id}/image")
    public ResponseEntity<Resource> image(@PathVariable Long id,
                                          @RequestParam(defaultValue = "crop") String kind) {
        return PhotoData.response(avatarService.image(UserContext.requireUserId(), id, kind));
    }

    /** 从历史里删一张（进回收站）。 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        avatarService.delete(UserContext.requireUserId(), id);
        return Result.success();
    }

    /** 不用头像：回到首字母，历史都还在。 */
    @DeleteMapping("/current")
    public Result<Void> clearCurrent() {
        avatarService.clearCurrent(UserContext.requireUserId());
        return Result.success();
    }
}
