package com.chatbi.chat.service.impl;

import com.chatbi.chat.response.ResultData;
import com.chatbi.chat.utils.FileUtils;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.FieldSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.*;

@Slf4j
@Service
public class MinioService {

    @Value("${minio.endpoint}")
    private String endpoint;

    @Value("${minio.accessKey}")
    private String accessKey;

    @Value("${minio.secretKey}")
    private String secretKey;

    @Value("${minio.chatdata.bucketName}")
    private String bucketName;

    @Value("${dcar.analysis.filePath:}")
    private String localFilePrefix;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private MinioClient buildClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    /**
     * 上传文件
     */
    public ResultData upload(InputStream stream, String fileName) {
        if (StringUtils.isBlank(bucketName)) {
            return ResultData.fail("文件桶名称为空");
        }
        if (StringUtils.isBlank(fileName)) {
            return ResultData.fail("文件名称为空");
        }
        try {
            MinioClient client = buildClient();
            createBucket(client, bucketName);
            putObject(client, bucketName, fileName, stream, "application/x-kryo");
            return ResultData.success("上传成功");
        } catch (Exception e) {
            log.error("文件上传异常：" + e);
            return ResultData.fail("上传失败");
        }
    }

    public ResultData putFileForJson(Object data, String filePath) {
        FileUtils.ensureDirectoryExists(localFilePrefix + filePath.substring(0, filePath.lastIndexOf("/")));
        File file = new File(localFilePrefix + filePath);
        try {
            FileOutputStream outputStream = new FileOutputStream(file);
            objectMapper.writeValue(outputStream, data);

            MinioClient client = buildClient();
            createBucket(client, bucketName);
            putObject(client, bucketName, filePath, Files.newInputStream(file.toPath()), "application/json");
            log.info("上传文件({})到minio的桶({})成功", filePath, bucketName);
            return ResultData.success("上传成功");
        } catch (Exception e) {
            log.error("文件上传异常：" + e);
            return ResultData.fail("上传失败");
        } finally {
            file.delete();
            log.info("本地文件({})删除成功", filePath);
        }
    }

    /**
     * 文件流下载
     */
    public ResultData downloadIo(String bucketName, String objectName, String fileName) {
        File file = new File(fileName);
        if (!file.exists()) {
            createDir(file.getAbsolutePath());
        }
        MinioClient client;
        try {
            client = buildClient();
        } catch (Exception e) {
            log.error("初始化MinioClient失败:", e);
            return ResultData.fail("minio服务异常");
        }

        try {
            if (StringUtils.isEmpty(bucketName)) {
                bucketName = this.bucketName;
            }
            log.info("bucketName == " + bucketName + ",objectName == " + objectName);
            client.statObject(StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build());
        } catch (ErrorResponseException e) {
            log.info("bucketName中无此object");
            return ResultData.fail("查询历史记录已过期，请重新查询！");
        } catch (Exception e) {
            log.error("Minio校验文件存在失败:", e);
            return ResultData.fail("Minio服务异常");
        }
        try {
            client.downloadObject(DownloadObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .filename(fileName)
                    .build());
        } catch (Exception e) {
            log.error("获取minio文件失败:", e);
            return ResultData.fail("查询历史记录已过期，请重新查询！");
        }
        return ResultData.success();
    }

    private void createDir(String absolutePath) {
        int lastIndex = absolutePath.lastIndexOf("/");
        String dirPath = absolutePath.substring(0, lastIndex);
        File file = new File(dirPath);
        if (file.exists() && file.isDirectory()) {
            return;
        } else {
            file.mkdirs();
        }
    }

    /**
     * 创建bucket
     */
    public void createBucket(MinioClient client, String bucketName) throws Exception {
        if (!bucketExists(client, bucketName)) {
            client.makeBucket(MakeBucketArgs.builder()
                    .bucket(bucketName)
                    .build());
        }
    }

    /**
     * 检查文件存储桶是否存在
     */
    public boolean bucketExists(MinioClient client, String bucketName) throws Exception {
        return client.bucketExists(BucketExistsArgs.builder()
                .bucket(bucketName)
                .build());
    }

    /**
     * 上传文件
     */
    public void putObject(MinioClient client, String bucketName, String objectName, InputStream stream, String contentType) throws Exception {
        String contentTypeHeader = StringUtils.isBlank(contentType) ? "application/json" : contentType;
        client.putObject(PutObjectArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .stream(stream, stream.available(), -1)
                .contentType(contentTypeHeader)
                .build());
    }

    /**
     * 文件流下载
     */
    /**
     * 删除对象（不存在时静默成功）
     */
    public void removeObject(String bucketName, String objectName) throws Exception {
        buildClient().removeObject(
                io.minio.RemoveObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build());
    }

    public InputStream getObject(String bucketName, String objectName) throws Exception {
        if (org.springframework.util.StringUtils.isEmpty(bucketName)) {
            bucketName = this.bucketName;
        }
        MinioClient client = buildClient();
        return client.getObject(GetObjectArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .build());
    }

    public File kryoSerialize(String fileNameWithPath,
                              List<Map<String, Object>> data) {
        File file = new File(fileNameWithPath);
        FileOutputStream fileOutputStream = null;
        Output output = null;
        try {
            fileOutputStream = new FileOutputStream(file);
            output = new Output(fileOutputStream);

            Kryo kryo = register();
            kryo.writeObject(output, data);
            log.info("Serialized data size: {}", output.total());
        } catch (IOException e) {
            e.printStackTrace();
            log.error("Error serializing data: " + e.getMessage());
        } finally {
            if (output != null) {
                output.close();
            }
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return file;
    }

    public List<Map<String, Object>> getDataByKryo(InputStream inputStream) {
        Kryo kryo = register();
        Input kryoInput = new Input(inputStream);
        return kryo.readObject(kryoInput, ArrayList.class);
    }

    public Kryo register() {
        Kryo kryo = new Kryo();
        kryo.register(ArrayList.class);
        kryo.register(HashMap.class);
        kryo.register(LinkedHashMap.class);
        kryo.register(BigDecimal.class);
        kryo.register(Map.class, new FieldSerializer<>(kryo, LinkedHashMap.class));
        return kryo;
    }
}
