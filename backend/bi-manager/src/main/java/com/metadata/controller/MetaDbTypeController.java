package com.metadata.controller;

import com.common.enums.DbTypeEnum;
import com.common.result.R;
import com.metadata.vo.meta.DbTypeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据库类型 Controller
 */
@Tag(name = "数据源管理")
@RestController
@RequestMapping("/v1/meta/db-type")
public class MetaDbTypeController {

    @Operation(summary = "数据库类型列表")
    @GetMapping("/list")
    public R<List<DbTypeVO>> list() {
        List<DbTypeVO> list = DbTypeEnum.listAll().stream()
                .map(item -> {
                    DbTypeVO vo = new DbTypeVO();
                    vo.setId(item.getId());
                    vo.setName(item.getName());
                    vo.setJdbcPrefix(item.getJdbcPrefix());
                    vo.setDefaultPort(item.getDefaultPort());
                    return vo;
                })
                .collect(Collectors.toList());
        return R.ok(list);
    }

    @Operation(summary = "获取 JDBC 前缀")
    @GetMapping("/jdbc-prefix")
    public R<String> getJdbcPrefix(@Parameter(description = "数据库类型 ID") @RequestParam Integer id) {
        return R.ok(DbTypeEnum.getJdbcPrefixById(id));
    }
}
