package com.senses.permission.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileCopyUtils;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;

/**
 * @Description
 * @Date 2025-02-07 17:07
 * @Author liaojinlei
 **/
@Slf4j
public class FileUtils {
    /**
     * 导出模板
     * @param response
     * @param fileName
     */
    public static void downloadTemplate(HttpServletResponse response,String fileName){
        // 使用 ClassPathResource 来加载 resources 目录下的文件
        ClassPathResource resource = new ClassPathResource(fileName);

        // 设置响应头
        response.setContentType("application/vnd.ms-excel");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-Disposition", "attachment; filename=" + resource.getFilename());

        // 读取文件并写入响应
        try (InputStream inputStream = resource.getInputStream()) {
            FileCopyUtils.copy(inputStream, response.getOutputStream());
            response.getOutputStream().flush();
        } catch (IOException e) {
            log.error("",e);
        }
    }
}
