package com.bi.service.impl;

import cn.hutool.core.util.StrUtil;
import com.bi.util.FileUtils;
import com.common.result.R;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.FieldSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.*;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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

    public R uploadKryoFile(String fileNameWithPath, List<Map<String, Object>> data) {
        File file = kryoSerialize(fileNameWithPath, data);
        if (file == null || !file.exists() || file.length() == 0) {
            return R.fail("Serialization failed or resulted in an empty file.");
        }
        try (InputStream inputStream = new FileInputStream(file)) {
            String[] objectNameArr = file.getPath().split("/");
            String objectName = objectNameArr[objectNameArr.length - 2] + "/" + objectNameArr[objectNameArr.length - 1];
            upload(inputStream, objectName);
        } catch (IOException e) {
            return R.fail("Error uploading to MinIO: " + e.getMessage());
        } finally {
            if (file.exists() && !file.delete()) {
                log.error("Failed to delete temporary file: " + file.getPath());
            }
        }
        return R.ok();
    }

    public R upload(InputStream stream, String fileName) {
        if (StrUtil.isBlank(bucketName)) {
            return R.fail("文件桶名称为空");
        }
        if (StrUtil.isBlank(fileName)) {
            return R.fail("文件名称为空");
        }
        try {
            MinioClient client = buildClient();
            createBucket(client, bucketName);
            putObject(client, bucketName, fileName, stream, "application/x-kryo");
            return R.ok("上传成功");
        } catch (Exception e) {
            log.error("文件上传异常：" + e);
            return R.fail("上传失败");
        }
    }

    public R downloadIo(String bucketName, String objectName, String fileName) {
        File file = new File(fileName);
        if (!file.exists()) {
            createDir(file.getAbsolutePath());
        }
        MinioClient client;
        try {
            client = buildClient();
        } catch (Exception e) {
            log.error("初始化MinioClient失败:", e);
            return R.fail("minio服务异常");
        }
        try {
            if (StrUtil.isEmpty(bucketName)) {
                bucketName = this.bucketName;
            }
            log.info("bucketName == " + bucketName + ",objectName == " + objectName);
            client.statObject(StatObjectArgs.builder().bucket(bucketName).object(objectName).build());
        } catch (Exception e) {
            log.info("bucketName中无此object");
            return R.fail("查询历史记录已过期，请重新查询！");
        }
        try {
            client.downloadObject(DownloadObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .filename(fileName)
                    .build());
        } catch (Exception e) {
            log.error("获取minio文件失败:", e);
            return R.fail("查询历史记录已过期，请重新查询！");
        }
        return R.ok();
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

    public void deleteFile(Integer dateCount) {
        MinioClient client;
        try {
            client = buildClient();
        } catch (Exception e) {
            log.error("初始化MinioClient失败:" + e);
            return;
        }
        try {
            ZonedDateTime threshold = ZonedDateTime.now().minusDays(dateCount);
            Iterable<Result<Item>> objects = client.listObjects(
                    ListObjectsArgs.builder().bucket(bucketName).prefix("").build());
            for (Result<Item> result : objects) {
                Item item = result.get();
                ZonedDateTime lastModified = item.lastModified().toInstant().atZone(ZoneId.systemDefault());
                if (lastModified.isBefore(threshold)) {
                    client.removeObject(RemoveObjectArgs.builder()
                            .bucket(bucketName).object(item.objectName()).build());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void createBucket(MinioClient client, String bucketName) throws Exception {
        if (!bucketExists(client, bucketName)) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        }
    }

    public boolean bucketExists(MinioClient client, String bucketName) throws Exception {
        return client.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
    }

    public void putObject(MinioClient client, String bucketName, String objectName, InputStream stream, String contentType) throws Exception {
        client.putObject(PutObjectArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .stream(stream, stream.available(), -1)
                .contentType(contentType)
                .build());
    }

    public InputStream getObject(String bucketName, String objectName) throws Exception {
        if (StrUtil.isEmpty(bucketName)) {
            bucketName = this.bucketName;
        }
        MinioClient client = null;
        try {
            client = buildClient();
        } catch (Exception e) {
            log.error("初始化MinioClient失败:" + e);
        }
        return client.getObject(GetObjectArgs.builder()
                .bucket(bucketName).object(objectName).build());
    }

    public File kryoSerialize(String fileNameWithPath, List<Map<String, Object>> data) {
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

    public List<Map<String, Object>> getKryoDataFromMinIo(String bucketName, String objectName) throws Exception {
        if (StrUtil.isEmpty(bucketName)) {
            bucketName = this.bucketName;
        }
        InputStream inputStream = getObject(bucketName, objectName);
        return getDataByKryo(inputStream);
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

    public Iterable<Result<Item>> getFolderListFile(String folderPath) {
        MinioClient client;
        try {
            client = buildClient();
        } catch (Exception e) {
            log.error("初始化MinioClient失败:" + e);
            return null;
        }
        return client.listObjects(ListObjectsArgs.builder()
                .bucket(bucketName).prefix(folderPath).recursive(true).build());
    }

    public R putFileForJson(Object data, String filePath) {
        FileUtils.ensureDirectoryExists(localFilePrefix + filePath.substring(0, filePath.lastIndexOf("/")));
        File file = new File(localFilePrefix + filePath);
        try {
            FileOutputStream outputStream = new FileOutputStream(file);
            objectMapper.writeValue(outputStream, data);
            MinioClient client = buildClient();
            createBucket(client, bucketName);
            putObject(client, bucketName, filePath, Files.newInputStream(file.toPath()), "application/json");
            log.info("上传文件({})到minio的桶({})成功", filePath, bucketName);
            return R.ok("上传成功");
        } catch (Exception e) {
            log.error("文件上传异常：" + e);
            return R.fail("上传失败");
        } finally {
            file.delete();
        }
    }
}
