package com.quality.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class TaskCreateDTO {
    @NotNull private Long diagnosisId;
    private List<ChangeDTO> changes;
    /** 任务级验证配置覆盖 {maxCases, similarLimit, regressionLimit, concurrency} */
    private Map<String, Object> verifyConfig;
}
