package com.example.fitapp.module.nutrition.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.fitapp.common.BusinessException;
import com.example.fitapp.common.ResultCode;
import com.example.fitapp.module.account.dto.CropDTO;
import com.example.fitapp.module.nutrition.entity.Food;
import com.example.fitapp.module.nutrition.mapper.FoodMapper;
import com.example.fitapp.module.nutrition.storage.PhotoStorage;
import com.example.fitapp.module.nutrition.storage.PhotoUploadPolicy;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 食物图片（ADR 0057）：只存方图和缩略图、谁能设、配额怎么算、换图时旧文件何时删。 */
@ExtendWith(MockitoExtension.class)
class FoodImageServiceTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Food.class);
    }

    @Mock
    private FoodMapper foodMapper;
    @Mock
    private PhotoStorage photoStorage;
    @Mock
    private DietService dietService;

    private FoodImageService service() {
        return new FoodImageService(foodMapper, photoStorage, new PhotoUploadPolicy(50), dietService);
    }

    private static MockMultipartFile jpeg(int w, int h) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), "jpg", bos);
        return new MockMultipartFile("file", "a.jpg", "image/jpeg", bos.toByteArray());
    }

    private static Food food(long id, Long userId) {
        Food f = new Food();
        f.setId(id);
        f.setUserId(userId);
        f.setName("猪大肠");
        return f;
    }

    private static CropDTO crop(int x, int y, int size) {
        CropDTO c = new CropDTO();
        c.setX(x);
        c.setY(y);
        c.setSize(size);
        return c;
    }

    @Test
    void 自建食物设图_只存截好的方图和缩略图_算进照片空间() throws Exception {
        when(foodMapper.selectById(10L)).thenReturn(food(10L, 7L));

        Food f = service().setOwn(7L, 10L, jpeg(1600, 1200), crop(100, 50, 1000));

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> image = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<byte[]> thumb = ArgumentCaptor.forClass(byte[].class);
        verify(photoStorage).write(key.capture(), eq("image/jpeg"), image.capture(), thumb.capture());
        assertTrue(key.getValue().startsWith("7/food/"), key.getValue());
        BufferedImage big = ImageIO.read(new ByteArrayInputStream(image.getValue()));
        BufferedImage small = ImageIO.read(new ByteArrayInputStream(thumb.getValue()));
        assertEquals(800, big.getWidth());
        assertEquals(800, big.getHeight());
        assertEquals(240, small.getWidth());
        int bytes = image.getValue().length + thumb.getValue().length;
        verify(dietService).checkPhotoQuota(7L, bytes);
        assertEquals(bytes, f.getImageBytes());
        assertEquals(key.getValue(), "7/food/" + f.getImageKey());
    }

    @Test
    void 小图不放大() throws Exception {
        when(foodMapper.selectById(10L)).thenReturn(food(10L, 7L));

        service().setOwn(7L, 10L, jpeg(300, 500), null);

        ArgumentCaptor<byte[]> image = ArgumentCaptor.forClass(byte[].class);
        verify(photoStorage).write(anyString(), eq("image/jpeg"), image.capture(), any(byte[].class));
        assertEquals(300, ImageIO.read(new ByteArrayInputStream(image.getValue())).getWidth());
    }

    @Test
    void 换图_配额只算差值_库里改完才删旧文件() throws Exception {
        Food f = food(10L, 7L);
        f.setImageKey("old");
        f.setImageBytes(50_000);
        when(foodMapper.selectById(10L)).thenReturn(f);

        service().setOwn(7L, 10L, jpeg(800, 800), null);

        ArgumentCaptor<Long> delta = ArgumentCaptor.forClass(Long.class);
        verify(dietService).checkPhotoQuota(eq(7L), delta.capture());
        assertTrue(delta.getValue() < 0, "新图比旧的小，差值应为负：" + delta.getValue());
        InOrder order = inOrder(foodMapper, photoStorage);
        order.verify(foodMapper).update(any(), any());
        order.verify(photoStorage).delete("7/food/old");
    }

    @Test
    void 预置食物_用户不能设图() {
        when(foodMapper.selectById(99L)).thenReturn(food(99L, null));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service().setOwn(7L, 99L, jpeg(10, 10), null));
        assertEquals(ResultCode.FORBIDDEN.getCode(), e.getCode());
    }

    @Test
    void 别人的食物_当作不存在() {
        when(foodMapper.selectById(10L)).thenReturn(food(10L, 8L));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service().setOwn(7L, 10L, jpeg(10, 10), null));
        assertEquals(ResultCode.NOT_FOUND.getCode(), e.getCode());
    }

    @Test
    void 管理员给预置设图_放在_preset_目录_不算任何人的配额() throws Exception {
        when(foodMapper.selectById(99L)).thenReturn(food(99L, null));

        service().setPreset(99L, jpeg(400, 400), null);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(photoStorage).write(key.capture(), eq("image/jpeg"), any(byte[].class), any(byte[].class));
        assertTrue(key.getValue().startsWith("preset/food/"), key.getValue());
        verify(dietService, never()).checkPhotoQuota(anyLong(), anyLong());
    }

    @Test
    void WebP_拒收() {
        when(foodMapper.selectById(10L)).thenReturn(food(10L, 7L));
        MockMultipartFile webp = new MockMultipartFile("file", "a.webp", "image/webp", new byte[]{1, 2, 3});

        assertThrows(BusinessException.class, () -> service().setOwn(7L, 10L, webp, null));
    }

    @Test
    void 去掉图片_旧文件直接删() {
        Food f = food(10L, 7L);
        f.setImageKey("old");
        when(foodMapper.selectById(10L)).thenReturn(f);

        service().removeOwn(7L, 10L);

        verify(foodMapper).update(any(), any());
        verify(photoStorage).delete("7/food/old");
    }

    @Test
    void 取图_别人的自建食物取不到_预置人人可取() {
        Food other = food(10L, 8L);
        other.setImageKey("k");
        Food preset = food(99L, null);
        preset.setImageKey("p");
        when(foodMapper.selectById(10L)).thenReturn(other);
        when(foodMapper.selectById(99L)).thenReturn(preset);
        when(photoStorage.readThumb("preset/food/p")).thenReturn(new byte[]{1});

        assertNull(service().image(7L, 10L, "thumb"));
        assertNotNull(service().image(7L, 99L, "thumb"));
    }

    @Test
    void 复制为自定义_图片复制到用户名下_算进配额() {
        Food preset = food(99L, null);
        preset.setImageKey("p");
        when(foodMapper.selectById(99L)).thenReturn(preset);
        when(photoStorage.readImage("preset/food/p", "image/jpeg")).thenReturn(new byte[100]);
        when(photoStorage.readThumb("preset/food/p")).thenReturn(new byte[20]);

        FoodImageService.Copied c = service().copyFrom(7L, 99L);

        assertNotNull(c);
        assertEquals(120, c.bytes());
        verify(dietService).checkPhotoQuota(7L, 120);
    }

    @Test
    void 复制为自定义_来源没有图就不复制() {
        when(foodMapper.selectById(99L)).thenReturn(food(99L, null));

        assertNull(service().copyFrom(7L, 99L));
        assertNull(service().copyFrom(7L, null));
    }
}
