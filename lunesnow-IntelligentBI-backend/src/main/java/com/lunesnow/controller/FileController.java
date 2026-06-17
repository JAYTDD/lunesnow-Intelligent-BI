package com.lunesnow.controller;

import cn.hutool.core.io.FileUtil;
import com.lunesnow.common.BaseResponse;
import com.lunesnow.common.ErrorCode;
import com.lunesnow.common.ResultUtils;
import com.lunesnow.exception.BusinessException;
import com.lunesnow.model.dto.file.UploadFileRequest;
import com.lunesnow.model.entity.User;
import com.lunesnow.model.enums.FileUploadBizEnum;
import com.lunesnow.service.UserService;
import java.util.Arrays;

import jakarta.servlet.http.HttpServletRequest;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件接口
 *
 * @author lunesnow
  */
@RestController
@RequestMapping("/file")
@Slf4j
public class FileController {

    @Resource
    private UserService userService;

    /**
     * 文件上传（本地存储）
     *
     * @param multipartFile
     * @param uploadFileRequest
     * @param request
     * @return
     */
    @PostMapping("/upload")
    public BaseResponse<String> uploadFile(@RequestPart("file") MultipartFile multipartFile,
                                           UploadFileRequest uploadFileRequest,
                                           HttpServletRequest request) {
        String biz = uploadFileRequest.getBiz();
        FileUploadBizEnum fileUploadBizEnum = FileUploadBizEnum.getEnumByValue(biz);
        if (fileUploadBizEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        validFile(multipartFile, fileUploadBizEnum);
        User loginUser = userService.getLoginUser(request);

        try {
            // 保存到本地临时目录（UUID前缀防重名 + 路径遍历）
            String originalFilename = multipartFile.getOriginalFilename();
            String sanitizedFilename = originalFilename == null ? "unknown"
                    : originalFilename.replaceAll("[\\\\/]", "");
            String filename = loginUser.getId() + "-" + java.util.UUID.randomUUID() + "-" + sanitizedFilename;
            String tempDir = System.getProperty("java.io.tmpdir");
            String filepath = tempDir + "/" + filename;
            multipartFile.transferTo(new java.io.File(filepath));
            log.info("文件已保存到本地: {}", filepath);
            return ResultUtils.success(filepath);
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        }
    }

    /**
     * 校验文件
     *
     * @param multipartFile
     * @param fileUploadBizEnum 业务类型
     */
    private void validFile(MultipartFile multipartFile, FileUploadBizEnum fileUploadBizEnum) {
        // 文件大小
        long fileSize = multipartFile.getSize();
        // 文件后缀
        String fileSuffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        final long ONE_M = 1024 * 1024L;
        if (FileUploadBizEnum.USER_AVATAR.equals(fileUploadBizEnum)) {
            if (fileSize > ONE_M) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小不能超过 1M");
            }
            if (!Arrays.asList("jpeg", "jpg", "svg", "png", "webp").contains(fileSuffix)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件类型错误");
            }
        } else {
            if (fileSize > 10 * ONE_M) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小不能超过 10MB");
            }
            if (!Arrays.asList("jpeg", "jpg", "png", "gif", "webp", "svg",
                    "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "json").contains(fileSuffix)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件类型不支持");
            }
        }
    }
}
