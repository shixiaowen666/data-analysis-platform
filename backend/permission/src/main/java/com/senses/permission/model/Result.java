package com.senses.permission.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "result参数模型，仅调用文件分享时使用")
@NoArgsConstructor
@Setter
@Getter
@ToString
public class Result<T> implements Serializable {
    @Schema(description = "响应码，0:成功")
    protected int code;
    @Schema(description = "响应描述")
    protected String msg;
    @Schema(description = "数据")
    protected T datas;
    /**
     * bdf_provider相应区别码
     **/
    private  String bdfProviderSystemCode="bdf_provider_file_hub_result";

}


