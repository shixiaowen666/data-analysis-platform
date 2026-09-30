package com.senses.permission.model;

import io.swagger.v3.oas.annotations.media.Schema;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;


/**
 * @desc 分页查询对象
 */
@Data
@Schema(description = "分页查询对象")
public class PageParam<T> {

	@Schema(description = "分页对象，必填current当前页，size长度")
	private Page page;
	@Schema(description = "其他查询条件")
	private T queryParam;
}
