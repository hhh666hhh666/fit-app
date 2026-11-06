package com.example.fitapp.module.account.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.fitapp.common.BusinessException;
import com.example.fitapp.module.account.dto.CropDTO;
import com.example.fitapp.module.account.entity.Avatar;
import com.example.fitapp.module.account.mapper.AvatarMapper;
import com.example.fitapp.module.account.vo.AvatarVO;
import com.example.fitapp.module.nutrition.service.DietService;
import com.example.fitapp.module.nutrition.storage.PhotoStorage;
import com.example.fitapp.module.nutrition.storage.PhotoUploadPolicy;
import com.example.fitapp.module.user.entity.User;
import com.example.fitapp.module.user.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.InputStreamSource;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvatarServiceTest {

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant a = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(a, User.class);
        TableInfoHelper.initTableInfo(a, Avatar.class);
    }

    @Mock
    private AvatarMapper avatarMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private PhotoStorage photoStorage;
    @Mock
    private DietService dietService;
    @Mock
    private AvatarTrash avatarTrash;

    private AvatarService service() {
        return new AvatarService(avatarMapper, userMapper, photoStorage, new PhotoUploadPolicy(50), dietService, avatarTrash);
    }

    private static byte[] jpeg(int w, int h) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), "jpg", bos);
        return bos.toByteArray();
    }

    @Test
    void 上传_存原图和三份小图_设为当前() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", jpeg(1600, 1200));
        doAnswer(inv -> {
            inv.<Avatar>getArgument(0).setId(5L);
            return 1;
        }).when(avatarMapper).insert(any(Avatar.class));
        CropDTO crop = new CropDTO();
        crop.setX(100);
        crop.setY(50);
        crop.setSize(800);

        AvatarVO vo = service().upload(7L, file, crop);

        assertTrue(vo.isCurrent());
        assertEquals(100, vo.getCropX());
        assertEquals(800, vo.getCropSize());
        assertEquals(1600, vo.getWidth());
        ArgumentCaptor<Avatar> saved = ArgumentCaptor.forClass(Avatar.class);
        verify(avatarMapper).insert(saved.capture());
        assertTrue(saved.getValue().getPath().startsWith("7/avatar/"));
        verify(photoStorage).write(eq(saved.getValue().getPath()), eq("image/jpeg"), any(InputStreamSource.class), any());
        verify(photoStorage).writeSide(eq(saved.getValue().getPath()), eq(PhotoStorage.CROP_SUFFIX), any());
        verify(photoStorage).writeSide(eq(saved.getValue().getPath()), eq(PhotoStorage.VIEW_SUFFIX), any());
        verify(userMapper).update(any(), any());
    }

    @Test
    void 上传_WebP不收() {
        MockMultipartFile file = new MockMultipartFile("file", "a.webp", "image/webp", new byte[]{1, 2});
        assertThrows(BusinessException.class, () -> service().upload(7L, file, null));
        verify(avatarMapper, never()).insert(any(Avatar.class));
    }

    @Test
    void 上传_读不出来的图不落盘() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1, 2, 3});
        assertThrows(BusinessException.class, () -> service().upload(7L, file, null));
        verify(photoStorage, never()).write(anyString(), anyString(), any(InputStreamSource.class), any());
    }

    @Test
    void 删除当前头像_进回收站并回到首字母() {
        Avatar a = new Avatar();
        a.setId(5L);
        a.setUserId(7L);
        a.setCreatedAt(LocalDateTime.now());
        when(avatarMapper.selectOne(any())).thenReturn(a);
        User u = new User();
        u.setId(7L);
        u.setAvatarId(5L);
        when(userMapper.selectOne(any())).thenReturn(u);

        service().delete(7L, 5L);

        verify(avatarTrash).put(7L, a);
        verify(avatarMapper).deleteById(5L);
        verify(userMapper).update(any(), any());
        verify(photoStorage, never()).delete(any());
    }

    @Test
    void 删除别的头像_当前头像不变() {
        Avatar a = new Avatar();
        a.setId(5L);
        a.setUserId(7L);
        when(avatarMapper.selectOne(any())).thenReturn(a);
        User u = new User();
        u.setId(7L);
        u.setAvatarId(9L);
        when(userMapper.selectOne(any())).thenReturn(u);

        service().delete(7L, 5L);

        verify(userMapper, never()).update(any(), any());
    }
}
