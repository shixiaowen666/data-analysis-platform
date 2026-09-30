package com.chatbi.chat.models;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;

@Data
public class OrderByDTO implements Serializable {

    @Schema(description = "id")
    @NotNull(message = "排序字段id不能为空")
    private Long id;

    @Schema(description = "排序字段英文名")
    private String key;

    @Schema(description = "排序字段中文名")
    private String name;

    @Schema(description = "排序顺序 desc:降序,asc:升序")
    private String order;

}
