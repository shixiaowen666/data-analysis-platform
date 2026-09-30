package com.bi.util;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Set;

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

    //创建脚本文件
    public static void createDcarScript(String directoryPath,String scriptname,StringBuffer paramBuffer) throws IOException {

        // 脚本完整路径
        String scriptPath = directoryPath + "/" + scriptname;

        // 确保目录存在
        File dir = new File(directoryPath);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Failed to create directory: " + directoryPath);
        }
        // 创建脚本内容
        String scriptContent = paramBuffer.toString();
        // 写入脚本文件
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(scriptPath))) {
            writer.write(scriptContent);
        }
        // 设置脚本可执行权限
        /*try {
            setExecutablePermissions(scriptPath);
        } catch (IOException e) {
            //throw new IOException("Failed to set executable permissions", e);
        }*/
    }

    // 设置可执行权限的方法
    private static void setExecutablePermissions(String filePath) throws IOException {
        Set<PosixFilePermission> perms = new HashSet<>();
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_WRITE);
        perms.add(PosixFilePermission.OWNER_EXECUTE);
        perms.add(PosixFilePermission.GROUP_READ);
        perms.add(PosixFilePermission.GROUP_EXECUTE);
        perms.add(PosixFilePermission.OTHERS_READ);
        perms.add(PosixFilePermission.OTHERS_EXECUTE);
        try {
            Files.setPosixFilePermissions(Paths.get(filePath), perms);
        } catch (IOException e) {
            //throw new IOException("Failed to set executable permissions", e);
        }

    }
}
