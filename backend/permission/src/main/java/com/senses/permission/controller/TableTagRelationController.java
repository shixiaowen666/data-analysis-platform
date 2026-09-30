package com.senses.permission.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.senses.permission.model.ResultData;
import com.senses.permission.model.vo.TableTagRelationVo;
import com.senses.permission.service.TableTagRelationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


/**
 * @author shangxiaoliang
 */
@RestController
@RequestMapping("/upc/api/table/relation")
@Tag(name = "数据表标签配置信息 API")
public class TableTagRelationController {

    @Autowired
    private TableTagRelationService tableTagRelationService;


    @GetMapping(value = "/getByTableId")
    @Operation(summary = "根据表ID查询行权限配置信息")
    public ResultData<TableTagRelationVo> getByTableId(@RequestParam Long tableId) {
        return tableTagRelationService.getByTableId(tableId);
    }

    @PostMapping(value = "/save")
    @Operation(summary = "数据表行权限配置信息编辑")
    public ResultData save(@RequestBody TableTagRelationVo tableTagRelationVo) {
        try {
            tableTagRelationService.save(tableTagRelationVo);
        } catch (Exception e) {
            return ResultData.fail(e.getMessage());
        }
        return ResultData.success();
    }


}
