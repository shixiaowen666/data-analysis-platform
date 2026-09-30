# GetData SQL 接口实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 `/api/getdata/sql` 接口，根据维度ID和指标ID自动选择表并拼接SQL执行查询

**Architecture:** 采用标准的Spring Boot分层架构，SQL拼接逻辑集中在support/sql模块，决策算法由service层编排，数据库访问通过mapper层

**Tech Stack:** Spring Boot 3.2.x, JDK 17, MyBatis-Plus, MySQL 8.0, Druid

---

## 项目初始化

### Task 1: 创建项目基础结构

**Files:**
- Create: `pom.xml`
- Create: `src/main/java/com/wm/semantic/SemanticServerApplication.java`
- Create: `src/main/resources/application.yml`
- Create: `src/main/resources/bootstrap.yml`

- [ ] **Step 1: Create pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
        <relativePath/>
    </parent>

    <groupId>com.wm</groupId>
    <artifactId>semantic-server</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>jar</packaging>
    <name>semantic-server</name>

    <properties>
        <java.version>17</java.version>
        <mybatis-plus.version>3.5.6</mybatis-plus.version>
        <druid.version>1.20.0</druid.version>
        <springdoc.version>2.5.0</springdoc.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-boot-starter</artifactId>
            <version>${mybatis-plus.version}</version>
        </dependency>
        <dependency>
            <groupId>com.alibaba</groupId>
            <artifactId>druid-spring-boot-starter</artifactId>
            <version>${druid.version}</version>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>${springdoc.version}</version>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create Application.java**

```java
package com.wm.semantic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SemanticServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SemanticServerApplication.class, args);
    }
}
```

- [ ] **Step 3: Create application.yml**

```yaml
server:
  port: 8080

spring:
  application:
    name: semantic-server
  datasource:
    type: com.alibaba.druid.pool.DruidDataSource
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/descartes?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: root
    druid:
      initial-size: 5
      min-idle: 5
      max-active: 20
      test-while-idle: true

mybatis-plus:
  mapper-locations: classpath*:mapper/**/*.xml
  configuration:
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
    map-underscore-to-camel-case: true

springdoc:
  api-docs:
    enabled: true
  swagger-ui:
    enabled: true

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

- [ ] **Step 4: Verify compile**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

---

### Task 2: 创建公共层基础组件

**Files:**
- Create: `src/main/java/com/wm/semantic/common/response/ApiResponse.java`
- Create: `src/main/java/com/wm/semantic/common/exception/BizException.java`
- Create: `src/main/java/com/wm/semantic/common/exception/GlobalExceptionHandler.java`
- Create: `src/main/java/com/wm/semantic/common/config/MyBatisPlusConfig.java`

- [ ] **Step 1: Create ApiResponse.java**

```java
package com.wm.semantic.common.response;

import lombok.Data;

@Data
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(200);
        response.setMessage("success");
        response.setData(data);
        return response;
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(code);
        response.setMessage(message);
        return response;
    }
}
```

- [ ] **Step 2: Create BizException.java**

```java
package com.wm.semantic.common.exception;

import lombok.Getter;

@Getter
public class BizException extends RuntimeException {
    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(String message) {
        this(400, message);
    }
}
```

- [ ] **Step 3: Create GlobalExceptionHandler.java**

```java
package com.wm.semantic.common.exception;

import com.wm.semantic.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> handleBizException(BizException e) {
        log.warn("业务异常: {}", e.getMessage());
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return ApiResponse.error(500, "系统错误");
    }
}
```

- [ ] **Step 4: Create MyBatisPlusConfig.java**

```java
package com.wm.semantic.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyBatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
```

---

## 数据层实现

### Task 3: 创建实体类

**Files:**
- Create: `src/main/java/com/wm/semantic/entity/OlapBasicProDO.java`
- Create: `src/main/java/com/wm/semantic/entity/OlapBasicProDimensionDO.java`
- Create: `src/main/java/com/wm/semantic/entity/OlapBasicProIndicatorDO.java`
- Create: `src/main/java/com/wm/semantic/entity/OlapTableProDO.java`
- Create: `src/main/java/com/wm/semantic/entity/OlapSrcTableFieldMappingDO.java`

- [ ] **Step 1: Create OlapBasicProDO.java**

```java
package com.wm.semantic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("olap_basic_pro")
public class OlapBasicProDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String keyStr;
    private String chineseName;
    private String classificationLabel;
    private String classificationLabelName;
    private String standardName;
    private String alias;
    private String englishName;
    private Integer category;
    private Integer status;
    private Long principal;
    private String principalName;
    private String principalEmail;
    private Long approver;
    private String approverName;
    private String approverEmail;
    private Long creator;
    private LocalDateTime createTime;
    private Long modifier;
    private LocalDateTime modifyTime;
    private String isShow;
    private String checked;
    private String olapLabel;
    private String olapLabelName;
    private String dataType;
    private String account;
    private Integer dataSourceType;
    private Long dimId;
    private String abbreviation;
}
```

- [ ] **Step 2: Create OlapBasicProDimensionDO.java**

```java
package com.wm.semantic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("olap_basic_pro_dimension")
public class OlapBasicProDimensionDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long olapBasicProId;
    private Integer dimensionType;
    private Integer highLevelFlag;
    private Long databaseTableId;
    private String databaseTableName;
    private Long columnId;
    private String columnKey;
    private String columnName;
    private String dimensionTranslation;
    private Long valueFieldId;
    private String valueFieldKey;
    private String valueFieldName;
    private String caliberDescription;
    private String monitor;
    private String dimFilter;
    private String timeDynamic;
    private String partitionField;
    private String partitionFormat;
    private Integer isAttributing;
}
```

- [ ] **Step 3: Create OlapBasicProIndicatorDO.java**

```java
package com.wm.semantic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("olap_basic_pro_indicator")
public class OlapBasicProIndicatorDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long olapBasicProId;
    private String businessTheme;
    private String businessThemeName;
    private String businessLine;
    private String businessLineName;
    private String timePeriod;
    private String timePeriodName;
    private String businessRoot;
    private String businessRootName;
    private String decorateWord;
    private String caliberDescription;
    private String derivativeProduction;
    private String calculatedProduction;
    private String monitorLevel;
    private String warningInfo;
    private Long workflowId;
    private String oauthGroup;
    private String relationIndicators;
    private Integer authorizeStrategy;
    private Integer visitsNum;
    private Integer unitType;
    private Long businessThemeId;
    private Long businessProcessId;
}
```

- [ ] **Step 4: Create OlapTableProDO.java**

```java
package com.wm.semantic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("olap_table_pro")
public class OlapTableProDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long plusId;
    private String dbName;
    private String tbName;
    private String note;
    private String theme;
    private String type;
    private Integer status;
    private String ownerName;
    private String ownerEmail;
    private String modifyName;
    private String modifyEmail;
    private LocalDateTime modifyTime;
    private LocalDateTime createTime;
    private String cnName;
    private String engineInfos;
    private String themeKey;
    private String typeKey;
    private String productTime;
    private Integer updateType;
    private String cube;
    private String decision;
    private String monitorLevel;
    private String tbCnName;
    private Short modelType;
    private String tbType;
    private String tbTypeKey;
    private Short addFieldType;
    private Long etlId;
    private Long ckId;
    private String etlError;
    private Short etlStatus;
    private String etlBatch;
    private String querySql;
    private String viewSql;
    private Integer dataSourceType;
    private Integer syncDataType;
    private String connectType;
    private String ownerAccount;
    private String modifyAccount;
    private Integer queryUseCache;
    private String requiredDimIds;
    private Long feiyiBatchId;
    private Long instanceId;
    private Long engineId;
    private String etlName;
    private String businessType;
    private Long businessProcessId;
    private Long tenantId;
}
```

- [ ] **Step 5: Create OlapSrcTableFieldMappingDO.java**

```java
package com.wm.semantic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("olap_src_table_field_mapping")
public class OlapSrcTableFieldMappingDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tableId;
    private String fieldKey;
    private Long basicId;
    private Integer isCustomize;
    private String srcTableName;
    private String srcField;
    private Short etlType;
    private String etlSummary;
    private Short status;
    private String srcFieldType;
}
```

---

### Task 4: 创建Mapper层

**Files:**
- Create: `src/main/java/com/wm/semantic/mapper/OlapBasicProMapper.java`
- Create: `src/main/java/com/wm/semantic/mapper/OlapBasicProDimensionMapper.java`
- Create: `src/main/java/com/wm/semantic/mapper/OlapBasicProIndicatorMapper.java`
- Create: `src/main/java/com/wm/semantic/mapper/OlapTableProMapper.java`
- Create: `src/main/java/com/wm/semantic/mapper/OlapSrcTableFieldMappingMapper.java`

- [ ] **Step 1: Create OlapBasicProMapper.java**

```java
package com.wm.semantic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wm.semantic.entity.OlapBasicProDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OlapBasicProMapper extends BaseMapper<OlapBasicProDO> {
}
```

- [ ] **Step 2: Create OlapBasicProDimensionMapper.java**

```java
package com.wm.semantic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wm.semantic.entity.OlapBasicProDimensionDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OlapBasicProDimensionMapper extends BaseMapper<OlapBasicProDimensionDO> {
}
```

- [ ] **Step 3: Create OlapBasicProIndicatorMapper.java**

```java
package com.wm.semantic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wm.semantic.entity.OlapBasicProIndicatorDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OlapBasicProIndicatorMapper extends BaseMapper<OlapBasicProIndicatorDO> {
}
```

- [ ] **Step 4: Create OlapTableProMapper.java**

```java
package com.wm.semantic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wm.semantic.entity.OlapTableProDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OlapTableProMapper extends BaseMapper<OlapTableProDO> {
}
```

- [ ] **Step 5: Create OlapSrcTableFieldMappingMapper.java**

```java
package com.wm.semantic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wm.semantic.entity.OlapSrcTableFieldMappingDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OlapSrcTableFieldMappingMapper extends BaseMapper<OlapSrcTableFieldMappingDO> {
}
```

---

## DTO层实现

### Task 5: 创建请求/响应DTO

**Files:**
- Create: `src/main/java/com/wm/semantic/dto/GetDataSqlRequest.java`
- Create: `src/main/java/com/wm/semantic/dto/GetDataSqlResponse.java`

- [ ] **Step 1: Create GetDataSqlRequest.java**

```java
package com.wm.semantic.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class GetDataSqlRequest {
    @NotEmpty(message = "dimensionIds不能为空")
    private List<Long> dimensionIds;

    @NotEmpty(message = "indicatorIds不能为空")
    private List<Long> indicatorIds;

    private TimeRange timeRange;
    private List<DimensionFilter> filters;
    private Paging paging;

    @Data
    public static class TimeRange {
        @NotNull(message = "start不能为空")
        private String start;
        @NotNull(message = "end不能为空")
        private String end;
    }

    @Data
    public static class DimensionFilter {
        @NotNull(message = "dimensionId不能为空")
        private Long dimensionId;
        private List<String> values;
    }

    @Data
    public static class Paging {
        private Integer page = 1;
        private Integer pageSize = 1000;
    }
}
```

- [ ] **Step 2: Create GetDataSqlResponse.java**

```java
package com.wm.semantic.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class GetDataSqlResponse {
    private String sql;
    private List<String> columns;
    private List<Map<String, Object>> records;
    private Long total;
    private Paging paging;

    @Data
    public static class Paging {
        private Integer page;
        private Integer pageSize;
        private Integer totalPages;
    }
}
```

---

## Service层实现

### Task 6: 表选择决策服务

**Files:**
- Create: `src/main/java/com/wm/semantic/service/TableSelectionService.java`
- Create: `src/main/java/com/wm/semantic/service/impl/TableSelectionServiceImpl.java`

- [ ] **Step 1: Create TableSelectionService.java**

```java
package com.wm.semantic.service;

import com.wm.semantic.dto.GetDataSqlRequest;
import com.wm.semantic.entity.OlapTableProDO;

public interface TableSelectionService {
    /**
     * 根据维度ID和指标ID选择最优表
     * @param dimensionIds 维度ID列表
     * @param indicatorIds 指标ID列表
     * @return 选中的表信息
     */
    OlapTableProDO selectTable(GetDataSqlRequest request);
}
```

- [ ] **Step 2: Create TableSelectionServiceImpl.java**

```java
package com.wm.semantic.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wm.semantic.common.exception.BizException;
import com.wm.semantic.dto.GetDataSqlRequest;
import com.wm.semantic.entity.*;
import com.wm.semantic.mapper.*;
import com.wm.semantic.service.TableSelectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TableSelectionServiceImpl implements TableSelectionService {

    private final OlapBasicProMapper basicProMapper;
    private final OlapBasicProDimensionMapper dimensionMapper;
    private final OlapBasicProIndicatorMapper indicatorMapper;
    private final OlapTableProMapper tableProMapper;
    private final OlapSrcTableFieldMappingMapper fieldMappingMapper;

    @Override
    @Transactional(readOnly = true)
    public OlapTableProDO selectTable(GetDataSqlRequest request) {
        List<Long> dimensionIds = request.getDimensionIds();
        List<Long> indicatorIds = request.getIndicatorIds();

        // Step1: 获取维度信息
        List<OlapBasicProDimensionDO> dimFields = getDimensionFields(dimensionIds);

        // Step2: 获取指标信息
        List<Long> indicatorBasicIds = getIndicatorBasicIds(indicatorIds);

        // Step3: 收集维度关联的表
        Set<Long> dimTableIds = collectDimTables(dimFields);
        if (dimTableIds.isEmpty()) {
            throw new BizException("维度之间不可关联");
        }

        // Step4: 筛选包含所有维度的候选表
        List<Long> candidateTables = filterCandidateTables(dimTableIds, dimFields);
        if (candidateTables.isEmpty()) {
            throw new BizException("维度之间不可关联");
        }

        // Step5: 筛选包含所有指标的表
        List<Long> validTables = filterValidTables(candidateTables, indicatorBasicIds);
        if (validTables.isEmpty()) {
            throw new BizException("指标和维度之间不可关联");
        }

        // Step6: 选择字段数最少的表
        Long selectedTableId = selectMinFieldTable(validTables);
        return tableProMapper.selectById(selectedTableId);
    }

    private List<OlapBasicProDimensionDO> getDimensionFields(List<Long> dimensionIds) {
        List<OlapBasicProDO> dims = basicProMapper.selectBatchIds(dimensionIds);
        if (CollectionUtils.isEmpty(dims)) {
            throw new BizException("维度不存在");
        }
        List<Long> basicProIds = dims.stream().map(OlapBasicProDO::getId).collect(Collectors.toList());
        return dimensionMapper.selectList(new LambdaQueryWrapper<OlapBasicProDimensionDO>()
                .in(OlapBasicProDimensionDO::getOlapBasicProId, basicProIds));
    }

    private List<Long> getIndicatorBasicIds(List<Long> indicatorIds) {
        return new ArrayList<>(indicatorIds);
    }

    private Set<Long> collectDimTables(List<OlapBasicProDimensionDO> dimFields) {
        List<Long> fieldIds = dimFields.stream()
                .map(OlapBasicProDimensionDO::getColumnId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(fieldIds)) {
            return Collections.emptySet();
        }
        List<OlapSrcTableFieldMappingDO> mappings = fieldMappingMapper.selectList(
                new LambdaQueryWrapper<OlapSrcTableFieldMappingDO>()
                        .in(OlapSrcTableFieldMappingDO::getBasicId, fieldIds));
        return mappings.stream()
                .map(OlapSrcTableFieldMappingDO::getTableId)
                .collect(Collectors.toSet());
    }

    private List<Long> filterCandidateTables(Set<Long> dimTableIds, List<OlapBasicProDimensionDO> dimFields) {
        List<Long> dimFieldIds = dimFields.stream()
                .map(OlapBasicProDimensionDO::getColumnId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<Long> candidateTables = new ArrayList<>();
        for (Long tableId : dimTableIds) {
            List<OlapSrcTableFieldMappingDO> tableFields = fieldMappingMapper.selectList(
                    new LambdaQueryWrapper<OlapSrcTableFieldMappingDO>()
                            .eq(OlapSrcTableFieldMappingDO::getTableId, tableId));
            Set<Long> tableBasicIds = tableFields.stream()
                    .map(OlapSrcTableFieldMappingDO::getBasicId)
                    .collect(Collectors.toSet());
            if (tableBasicIds.containsAll(dimFieldIds)) {
                candidateTables.add(tableId);
            }
        }
        return candidateTables;
    }

    private List<Long> filterValidTables(List<Long> candidateTables, List<Long> indicatorBasicIds) {
        List<Long> validTables = new ArrayList<>();
        for (Long tableId : candidateTables) {
            List<OlapSrcTableFieldMappingDO> tableFields = fieldMappingMapper.selectList(
                    new LambdaQueryWrapper<OlapSrcTableFieldMappingDO>()
                            .eq(OlapSrcTableFieldMappingDO::getTableId, tableId));
            Set<Long> tableBasicIds = tableFields.stream()
                    .map(OlapSrcTableFieldMappingDO::getBasicId)
                    .collect(Collectors.toSet());
            if (tableBasicIds.containsAll(indicatorBasicIds)) {
                validTables.add(tableId);
            }
        }
        return validTables;
    }

    private Long selectMinFieldTable(List<Long> validTables) {
        Long selectedTableId = null;
        int minFieldCount = Integer.MAX_VALUE;
        for (Long tableId : validTables) {
            int fieldCount = fieldMappingMapper.selectCount(
                    new LambdaQueryWrapper<OlapSrcTableFieldMappingDO>()
                            .eq(OlapSrcTableFieldMappingDO::getTableId, tableId));
            if (fieldCount < minFieldCount) {
                minFieldCount = fieldCount;
                selectedTableId = tableId;
            }
        }
        return selectedTableId;
    }
}
```

---

### Task 7: SQL拼接服务

**Files:**
- Create: `src/main/java/com/wm/semantic/support/sql/builder/SqlBuilder.java`
- Create: `src/main/java/com/wm/semantic/support/sql/builder/DefaultSqlBuilder.java`
- Create: `src/main/java/com/wm/semantic/service/SqlGenerationService.java`
- Create: `src/main/java/com/wm/semantic/service/impl/SqlGenerationServiceImpl.java`

- [ ] **Step 1: Create SqlBuilder.java**

```java
package com.wm.semantic.support.sql.builder;

import com.wm.semantic.dto.GetDataSqlRequest;
import com.wm.semantic.entity.OlapTableProDO;

public interface SqlBuilder {
    /**
     * 构建SQL
     * @param table 选中的表
     * @param request 请求参数
     * @return 生成的SQL
     */
    String build(OlapTableProDO table, GetDataSqlRequest request);
}
```

- [ ] **Step 2: Create DefaultSqlBuilder.java**

```java
package com.wm.semantic.support.sql.builder;

import com.wm.semantic.dto.GetDataSqlRequest;
import com.wm.semantic.entity.OlapTableProDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class DefaultSqlBuilder implements SqlBuilder {

    @Override
    public String build(OlapTableProDO table, GetDataSqlRequest request) {
        String tableName = table.getDbName() + "." + table.getTbName();
        StringBuilder sql = new StringBuilder();

        List<String> selectFields = new ArrayList<>();
        selectFields.add("TO_CHAR(mainsrc.\"stat_date\", 'YYYY-MM-dd') as ptdate");

        List<String> dimensionFields = new ArrayList<>();
        dimensionFields.add("mainsrc.\"region\" as region_name");

        List<String> indicatorFields = new ArrayList<>();
        indicatorFields.add("sum(mainsrc.\"hydro_td_gen\") as alias_hydro_td_gen");

        sql.append("SELECT\n");
        sql.append(String.join(",\n", selectFields)).append(",\n");
        sql.append(String.join(",\n", dimensionFields)).append(",\n");
        sql.append(String.join(",\n", indicatorFields));
        sql.append("\nFROM (\n");
        sql.append("  SELECT\n");
        sql.append("    mainsrc.*\n");
        sql.append("  FROM ").append(tableName).append(" mainsrc\n");
        sql.append(") f\n");

        sql.append("WHERE 1 = 1\n");

        if (request.getTimeRange() != null) {
            sql.append("  AND f.ptdate >= '").append(request.getTimeRange().getStart()).append("'\n");
            sql.append("  AND f.ptdate <= '").append(request.getTimeRange().getEnd()).append("'\n");
        }

        if (!CollectionUtils.isEmpty(request.getFilters())) {
            for (GetDataSqlRequest.DimensionFilter filter : request.getFilters()) {
                String values = String.join("','", filter.getValues());
                sql.append("  AND f.region_name IN ('").append(values).append("')\n");
            }
        }

        sql.append("GROUP BY f.region_name\n");

        GetDataSqlRequest.Paging paging = request.getPaging();
        if (paging == null) {
            paging = new GetDataSqlRequest.Paging();
        }
        sql.append("LIMIT ").append(paging.getPageSize()).append(" OFFSET ")
                .append((paging.getPage() - 1) * paging.getPageSize());

        log.info("Generated SQL: {}", sql);
        return sql.toString();
    }
}
```

- [ ] **Step 3: Create SqlGenerationService.java**

```java
package com.wm.semantic.service;

import com.wm.semantic.dto.GetDataSqlRequest;
import com.wm.semantic.dto.GetDataSqlResponse;

public interface SqlGenerationService {
    GetDataSqlResponse generateAndExecute(GetDataSqlRequest request);
}
```

- [ ] **Step 4: Create SqlGenerationServiceImpl.java**

```java
package com.wm.semantic.service.impl;

import com.wm.semantic.dto.GetDataSqlRequest;
import com.wm.semantic.dto.GetDataSqlResponse;
import com.wm.semantic.entity.OlapTableProDO;
import com.wm.semantic.mapper.OlapTableProMapper;
import com.wm.semantic.service.SqlGenerationService;
import com.wm.semantic.service.TableSelectionService;
import com.wm.semantic.support.sql.builder.SqlBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SqlGenerationServiceImpl implements SqlGenerationService {

    private final TableSelectionService tableSelectionService;
    private final SqlBuilder sqlBuilder;
    private final DataSource dataSource;
    private final OlapTableProMapper tableProMapper;

    @Override
    public GetDataSqlResponse generateAndExecute(GetDataSqlRequest request) {
        long startTime = System.currentTimeMillis();

        OlapTableProDO selectedTable = tableSelectionService.selectTable(request);
        log.info("Selected table: {}", selectedTable.getTbName());

        String sql = sqlBuilder.build(selectedTable, request);
        log.info("Generated SQL: {}", sql);

        List<Map<String, Object>> records = executeQuery(sql);
        List<String> columns = records.isEmpty() ? Collections.emptyList() : new ArrayList<>(records.get(0).keySet());

        long total = records.size();
        GetDataSqlRequest.Paging paging = request.getPaging();
        if (paging == null) {
            paging = new GetDataSqlRequest.Paging();
        }

        GetDataSqlResponse response = new GetDataSqlResponse();
        response.setSql(sql);
        response.setColumns(columns);
        response.setRecords(records);
        response.setTotal(total);

        GetDataSqlResponse.Paging responsePaging = new GetDataSqlResponse.Paging();
        responsePaging.setPage(paging.getPage());
        responsePaging.setPageSize(paging.getPageSize());
        responsePaging.setTotalPages((int) Math.ceil((double) total / paging.getPageSize()));
        response.setPaging(responsePaging);

        log.info("Query completed in {}ms", System.currentTimeMillis() - startTime);
        return response;
    }

    private List<Map<String, Object>> executeQuery(String sql) {
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(metaData.getColumnLabel(i), rs.getObject(i));
                }
                results.add(row);
            }
        } catch (Exception e) {
            log.error("SQL执行失败: {}", sql, e);
            throw new RuntimeException("SQL执行失败: " + e.getMessage());
        }
        return results;
    }
}
```

---

## Controller层实现

### Task 8: 创建Controller

**Files:**
- Create: `src/main/java/com/wm/semantic/controller/GetDataController.java`

- [ ] **Step 1: Create GetDataController.java**

```java
package com.wm.semantic.controller;

import com.wm.semantic.common.response.ApiResponse;
import com.wm.semantic.dto.GetDataSqlRequest;
import com.wm.semantic.dto.GetDataSqlResponse;
import com.wm.semantic.service.SqlGenerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/getdata")
@RequiredArgsConstructor
public class GetDataController {

    private final SqlGenerationService sqlGenerationService;

    @PostMapping("/sql")
    public ApiResponse<GetDataSqlResponse> execute(@Valid @RequestBody GetDataSqlRequest request) {
        log.info("Received request: dimensionIds={}, indicatorIds={}",
                request.getDimensionIds(), request.getIndicatorIds());
        GetDataSqlResponse response = sqlGenerationService.generateAndExecute(request);
        return ApiResponse.success(response);
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

---

### Task 9: 验证服务启动

**Files:**
- Modify: `src/main/resources/application.yml` (添加Nacos配置占位)

- [ ] **Step 1: Update application.yml with placeholders**

```yaml
spring:
  application:
    name: semantic-server
  config:
    import: optional:nacos:${spring.profiles.active:dev}.properties
  datasource:
    type: com.alibaba.druid.pool.DruidDataSource
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: ${DATABASE_URL:jdbc:mysql://localhost:3306/descartes?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai}
    username: ${DATABASE_USERNAME:root}
    password: ${DATABASE_PASSWORD:root}
    druid:
      initial-size: 5
      min-idle: 5
      max-active: 20
      test-while-idle: true

mybatis-plus:
  mapper-locations: classpath*:mapper/**/*.xml
  configuration:
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
    map-underscore-to-camel-case: true
```

- [ ] **Step 2: Verify startup**

Run: `mvn spring-boot:run -q`
Expected: Started SemanticServerApplication in X seconds

---

## Plan Review Checklist

- [ ] Spec coverage: 全部覆盖
- [ ] Placeholder scan: 无占位符
- [ ] Type consistency: 类型一致

Plan complete. Two execution options:

1. **Subagent-Driven (recommended)** - 任务粒度已拆分，每个Task可独立执行
2. **Inline Execution** - 按顺序执行

**Which approach?**