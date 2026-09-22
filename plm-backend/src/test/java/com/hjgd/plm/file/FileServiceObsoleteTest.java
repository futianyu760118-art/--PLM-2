package com.hjgd.plm.file;

import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.file.entity.PlmFile;
import com.hjgd.plm.file.mapper.PlmFileMapper;
import com.hjgd.plm.file.service.impl.FileServiceImpl;
import com.hjgd.plm.file.watermark.WatermarkEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Phase 0 止血 1/4: 作废锁 —— 拦截靠 DB 标记(obsolete=1), 水印是纵深防御。
 * 因此「磁盘缺失/字体缺失导致盖章失败」不得阻断 ECN 生效, 但「可下载」必须立即被拒。
 */
@DisplayName("作废文件锁定与下载拦截")
@ExtendWith(MockitoExtension.class)
class FileServiceObsoleteTest {

    @Mock private PlmFileMapper fileMapper;
    @Mock private WatermarkEngine watermarkEngine;

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
    @DisplayName("markObsolete: 先落库 obsolete=1/has_watermark=1, 磁盘无文件也不影响落锁")
    void markObsoleteLocksInDbEvenWithoutDiskFile() throws Exception {
        PlmFile f = file(0, "nope.pdf");
        when(fileMapper.selectById(7L)).thenReturn(f);

        fileService.markObsolete(7L);

        ArgumentCaptor<PlmFile> cap = ArgumentCaptor.forClass(PlmFile.class);
        verify(fileMapper).updateById(cap.capture());
        assertEquals(1, cap.getValue().getObsolete());
        assertEquals(1, cap.getValue().getHasWatermark());
        // 磁盘上没有文件 → 不盖章, 但下载拦截已生效
        verify(watermarkEngine, never()).apply(any(), any(), any());
    }

    @Test
    @DisplayName("已作废文件重复 markObsolete: 幂等, 不重复盖章")
    void markObsoleteIsIdempotent() throws Exception {
        PlmFile f = file(1, "nope.pdf");
        f.setHasWatermark(1);
        when(fileMapper.selectById(7L)).thenReturn(f);

        fileService.markObsolete(7L);

        verify(fileMapper).updateById(any(PlmFile.class));
        verify(watermarkEngine, never()).apply(any(), any(), any());
    }

    @Test
    @DisplayName("盖章失败不回滚落锁(磁盘/字体异常不阻断 ECN 生效)")
    void stampingFailureKeepsDbLock() throws Exception {
        java.nio.file.Path src = tempDir.resolve("HJ001-drawing.pdf");
        java.nio.file.Files.writeString(src, "pdf-bytes");
        assertTrue(java.nio.file.Files.isRegularFile(src), "临时图纸应已落盘: " + src);
        // 水印内容含操作人, 需已登录上下文
        com.hjgd.plm.auth.security.LoginUser user = mock(com.hjgd.plm.auth.security.LoginUser.class);
        when(user.getRealName()).thenReturn("测试工程师");
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        user, null, java.util.List.of()));
        PlmFile f = file(0, src.toAbsolutePath().toString());
        when(fileMapper.selectById(7L)).thenReturn(f);
        doThrow(new RuntimeException("字体缺失")).when(watermarkEngine).apply(any(), any(), any());

        assertDoesNotThrow(() -> fileService.markObsolete(7L));

        verify(watermarkEngine).apply(any(), any(), any());
        verify(fileMapper).updateById(any(PlmFile.class));
        assertEquals(1, f.getObsolete());
        // 盖章临时文件不得残留
        assertFalse(java.nio.file.Files.exists(src.resolveSibling(src.getFileName() + ".wm.tmp")));
    }
}
