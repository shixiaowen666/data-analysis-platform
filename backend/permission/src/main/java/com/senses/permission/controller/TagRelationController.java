package com.senses.permission.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.senses.permission.entity.TagFieldRelation;
import com.senses.permission.model.ResultData;
import com.senses.permission.service.TagFieldRelationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * @author wanjie
 */
@RestController
@RequestMapping("/upc/api/tag/relation")
@Tag(name = "标签数据表关系配置 API")
public class TagRelationController {

    @Autowired
    private TagFieldRelationService tagDataPermissionService;


    @GetMapping(value = "/saveByTableId")
    @Operation(summary = "根据表ID修改行权限配置信息")
    public ResultData saveByTableId(@RequestBody TagFieldRelation tagFieldRelation) {
        if(tagDataPermissionService.saveByTableId(tagFieldRelation)){
            return ResultData.success();
        }
        return ResultData.fail("保存失败");
    }

}
