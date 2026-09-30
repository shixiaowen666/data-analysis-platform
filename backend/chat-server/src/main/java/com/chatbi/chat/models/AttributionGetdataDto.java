package com.chatbi.chat.models;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.Valid;
import java.util.List;

@Data
public class AttributionGetdataDto {


    @Schema(description = "时间颗粒度")
    @Valid
    private DateDimTypeDTO date;

    private DimDTO dimRequestDTO;

    private String baseline;

    private String comparison;

    private List<String> timeFrame;

    @Schema(description = "过滤条件")
    @Valid
    private List<FilterDTO> filters;

}
