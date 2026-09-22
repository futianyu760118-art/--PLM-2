package com.hjgd.plm.file.service;

import com.hjgd.plm.file.entity.PlmFile;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileService {

    PlmFile upload(MultipartFile file, String partNo, String fileType, String visibility);

    PlmFile getById(Long id);

    Resource download(Long id, boolean isOutsource);

    void markObsolete(Long id);

    void delete(Long id);
}
