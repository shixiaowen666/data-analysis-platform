package com.chatbi.chat.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.File;

/***
 * @ClassName FileUtils
 * @Description
 * @Author chenxiwen
 * @Date 3/25/25 10:36 PM
 * @Version 1.0
 */
@Slf4j
public class FileUtils {

    public static void ensureDirectoryExists(String directoryPath) {
        File directory = new File(directoryPath);
        if (!directory.exists()) {
            boolean created = directory.mkdirs();
            if (created) {
                log.info("目录不存在，已成功创建：" + directoryPath);
            } else {
                throw new RuntimeException("无法创建目录：" + directoryPath);
            }
        } else {
            log.info("目录已存在：" + directoryPath);
        }
    }
}
