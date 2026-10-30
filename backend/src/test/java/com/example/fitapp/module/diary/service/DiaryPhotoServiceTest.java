package com.example.fitapp.module.diary.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.fitapp.common.BusinessException;
import com.example.fitapp.module.diary.entity.DiaryPhoto;
import com.example.fitapp.module.diary.entity.DiaryPhotoRef;
import com.example.fitapp.module.diary.mapper.DiaryPhotoMapper;
import com.example.fitapp.module.diary.mapper.DiaryPhotoRefMapper;
import com.example.fitapp.module.nutrition.service.DietService;
import com.example.fitapp.module.nutrition.storage.PhotoStorage;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 纯单元测试：配额、上传失败不留文件、引用同步、删除日记时图片的去留。 */
@ExtendWith(MockitoExtension.class)
class DiaryPhotoServiceTest {

    /** 单测里没有 Spring，要手动注册表信息，否则 Lambda 条件取不到列名。 */
    @BeforeAll
    static void registerTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), DiaryPhoto.class);
    }

    @Mock
    private DiaryPhotoTrash photoTrash;

    @Mock
    private DiaryPhotoMapper photoMapper;

    @Mock
    private DiaryPhotoRefMapper refMapper;

    @Mock
    private DietService dietService;

    @Mock
    private PhotoStorage photoStorage;

    private DiaryPhotoService service() {
        return new DiaryPhotoService(photoMapper, refMapper, dietService, photoStorage, photoTrash);
    }

    private static MockMultipartFile jpeg() {
        return new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }

    private static DiaryPhoto photo(Long id) {
        DiaryPhoto p = new DiaryPhoto();
        p.setId(id);
        p.setUserId(7L);
        p.setPath("7/diary/p" + id);
        p.setMime("image/jpeg");
        return p;
    }

    @Test
    void 上传时记上没人引用的时刻_路径不用外部输入() throws IOException {
        service().upload(7L, jpeg());

        verify(dietService).checkPhotoQuota(7L, 3);
        ArgumentCaptor<DiaryPhoto> saved = ArgumentCaptor.forClass(DiaryPhoto.class);
        verify(photoMapper).insert(saved.capture());
        assertEquals(true, saved.getValue().getPath().startsWith("7/diary/"));
        verify(photoStorage).write(eq(saved.getValue().getPath()), eq("image/jpeg"), any(), any());
    }

    @Test
    void 配额满了不落盘() throws IOException {
        doThrow(new BusinessException(com.example.fitapp.common.ResultCode.BAD_REQUEST, "满了"))
                .when(dietService).checkPhotoQuota(any(), any(Long.class));

        assertThrows(BusinessException.class, () -> service().upload(7L, jpeg()));

        verify(photoStorage, never()).write(anyString(), anyString(), any(), any());
    }

    @Test
    void 写库失败要删掉刚落盘的文件() {
        when(photoMapper.insert(any(DiaryPhoto.class))).thenThrow(new RuntimeException("库挂了"));

        assertThrows(RuntimeException.class, () -> service().upload(7L, jpeg()));

        verify(photoStorage).delete(anyString());
    }

    @Test
    void 取别人的图等于没有() {
        when(photoMapper.selectOne(any())).thenReturn(null);

        assertNull(service().read(7L, 9L, false));

        verify(photoStorage, never()).readImage(any(), any());
    }

    @Test
    void 正文新写到的图建立引用_别人的图不算() {
        when(refMapper.selectList(any())).thenReturn(List.of());
        // 正文写了 9 和 10，只有 9 查得到（10 是别人的）
        when(photoMapper.selectList(any())).thenReturn(List.of(photo(9L)));

        service().syncRefs(7L, 3L, "![图片](diary-photo:9)\n正文\n![图片](diary-photo:10)");

        ArgumentCaptor<DiaryPhotoRef> ref = ArgumentCaptor.forClass(DiaryPhotoRef.class);
        verify(refMapper, times(1)).insert(ref.capture());
        assertEquals(9L, ref.getValue().getPhotoId());
        assertEquals(3L, ref.getValue().getEntryId());
    }

    @Test
    void 正文去掉的图留在相册_只去掉引用() {
        when(refMapper.selectList(any())).thenReturn(List.of(new DiaryPhotoRef(3L, 9L)));

        service().syncRefs(7L, 3L, "图都删了");

        verify(refMapper).delete(any());
        verify(photoMapper, never()).deleteById(any(Long.class));
        verify(photoStorage, never()).delete(any());
    }

    @Test
    void 删除日记默认不动图片() {
        when(refMapper.selectList(any())).thenReturn(List.of(new DiaryPhotoRef(3L, 9L)));

        service().releaseEntry(3L, false);

        verify(refMapper).delete(any());
        verify(photoMapper, never()).deleteById(any(Long.class));
        verify(photoStorage, never()).delete(any());
    }

    @Test
    void 选了同时删图_只清只属于它的图() {
        when(refMapper.selectList(any())).thenReturn(List.of(new DiaryPhotoRef(3L, 9L), new DiaryPhotoRef(3L, 10L)));
        // 9 只属于这篇，10 还有别的日记在用
        when(refMapper.selectCount(any())).thenReturn(0L).thenReturn(1L);
        when(photoMapper.selectById(9L)).thenReturn(photo(9L));

        List<DiaryPhoto> taken = service().releaseEntry(3L, true);

        assertEquals(1, taken.size());
        assertEquals(9L, taken.get(0).getId());
        verify(photoMapper).deleteById(9L);
        verify(photoMapper, never()).deleteById(10L);
        // 文件要留到回收站真正清理时才删（ADR 0047）
        verify(photoStorage, never()).delete(any());
    }
}
