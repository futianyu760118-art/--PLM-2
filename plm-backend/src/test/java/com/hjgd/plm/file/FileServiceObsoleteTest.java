package com.hjgd.plm.file;

import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.file.entity.PlmFile;
import com.hjgd.plm.file.mapper.PlmFileMapper;
import com.hjgd.plm.file.service.impl.FileServiceImpl;
import com.hjgd.plm.material.entity.Material;
import com.hjgd.plm.material.mapper.MaterialMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 作废锁: 拦截靠 DB 标记(obsolete=1), 下载必须立即被拒 */
@DisplayName("作废文件锁定与下载拦截")
@ExtendWith(MockitoExtension.class)
class FileServiceObsoleteTest {

    @Mock private PlmFileMapper fileMapper;
    @Mock private MaterialMapper materialMapper;

    @InjectMocks private FileServiceImpl fileService;

    @TempDir java.nio.file.Path tempDir;

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    private PlmFile file(int obsolete, String path) {
        PlmFile f = new PlmFile();
        f.setId(7L);
        f.setFileName("HJ001-结构图.pdf");
        f.setFilePath(path);
        f.setObsolete(obsolete);
        f.setHasWatermark(0);
        return f;
    }

    /** 上传路径需要登录态(取 uploadedBy 的真实姓名)与可写的存储目录 */
    private void givenUploadContext() {
        com.hjgd.plm.auth.security.LoginUser user = mock(com.hjgd.plm.auth.security.LoginUser.class);
        when(user.getRealName()).thenReturn("测试工程师");
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        user, null, List.of()));
        ReflectionTestUtils.setField(fileService, "intranetDir", tempDir.toString());
        ReflectionTestUtils.setField(fileService, "extranetDir", tempDir.toString());
    }

    private MockMultipartFile drawing() {
        return new MockMultipartFile("file", "HJ001-结构图.pdf", "application/pdf", "pdf-bytes".getBytes());
    }

    @Test
    @DisplayName("作废文件禁止下载")
    void obsoleteFileCannotBeDownloaded() {
        when(fileMapper.selectById(7L)).thenReturn(file(1, "nope.pdf"));

        BusinessException ex = assertThrows(BusinessException.class, () -> fileService.download(7L, false));

        assertTrue(ex.getMessage().contains("作废文件禁止下载"));
    }

    @Test
    @DisplayName("未作废文件通过作废闸门(磁盘缺失另行报错, 不是作废拦截)")
    void nonObsoleteFilePassesObsoleteGate() {
        when(fileMapper.selectById(7L)).thenReturn(file(0, "nope.pdf"));

        BusinessException ex = assertThrows(BusinessException.class, () -> fileService.download(7L, false));

        assertTrue(ex.getMessage().contains("文件不存在"));
    }

    @Test
    @DisplayName("markObsolete 落库 obsolete=1(磁盘无需存在)")
    void markObsoleteLocksInDb() {
        PlmFile f = file(0, "nope.pdf");
        when(fileMapper.selectById(7L)).thenReturn(f);

        fileService.markObsolete(7L);

        ArgumentCaptor<PlmFile> cap = ArgumentCaptor.forClass(PlmFile.class);
        verify(fileMapper).updateById(cap.capture());
        assertEquals(1, cap.getValue().getObsolete());
    }

    @Test
    @DisplayName("回归 D1: 上传落料号当前版本号(ECN 生效据此定位并作废旧图)")
    void uploadStampsMaterialVersion() {
        givenUploadContext();
        Material m = new Material();
        m.setPartNo("HJ202607040001");
        m.setVersionNo("V1.0");
        when(materialMapper.selectOne(any())).thenReturn(m);

        PlmFile saved = fileService.upload(drawing(), "HJ202607040001", "DRAWING", "INTRANET");

        assertEquals("V1.0", saved.getVersionNo(),
                "上传必须落版本号, 否则 ECN 生效时 version_no=oldVersion 匹配不到该文件");
        verify(fileMapper).insert(saved);
    }

    @Test
    @DisplayName("回归 D1: 物料不存在时版本号留空, 上传本身不失败")
    void uploadKeepsNullVersionWhenMaterialMissing() {
        givenUploadContext();
        when(materialMapper.selectOne(any())).thenReturn(null);

        PlmFile saved = fileService.upload(drawing(), "HJ-NOT-EXIST", "DRAWING", "INTRANET");

        assertNull(saved.getVersionNo());
        verify(fileMapper).insert(saved);
    }

    @Test
    @DisplayName("无料号的公共附件: 不查物料, 版本号为空")
    void uploadWithoutPartNoSkipsMaterialLookup() {
        givenUploadContext();

        PlmFile saved = fileService.upload(drawing(), null, "OTHER", "INTRANET");

        assertNull(saved.getVersionNo());
        verifyNoInteractions(materialMapper);
        verify(fileMapper).insert(saved);
    }
}
