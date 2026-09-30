package com.senses.permission.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.senses.permission.entity.UserTagValue;
import com.senses.permission.model.ResultData;
import com.senses.permission.model.param.TagParam;
import com.senses.permission.model.param.UserTagValueParam;
import com.senses.permission.model.vo.UserTagValueTableVO;
import com.senses.permission.service.TagService;
import com.senses.permission.service.UserTagValueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author : wanjei
 * @date : 2025-08-26
 */
@RestController
@RequestMapping("/upc/api/tag")
@Tag(name = "标签管理 API")
public class TagController {

    @Autowired
    private TagService tagService;

    @Autowired
    private UserTagValueService userTagValueService;


    @GetMapping(value = "/all")
    @Operation(summary = "查询所有标签信息")
    public ResultData<List<TagParam>> all() {
        return tagService.getAllTags();
    }

    @PostMapping(value = "/saveOrUpdate")
    @Operation(summary = "新增或编辑或删除标签")
    public ResultData createTag(@RequestBody TagParam tagParam, @RequestHeader(value = "username", required = false) String username) {
        return tagService.addOrModifyTag(tagParam, username);
    }

    @PostMapping("/saveUserTagValue")
    @Operation(summary = "用户标签值编辑")
    public ResultData saveUserTagValue(@RequestBody UserTagValueParam userTagValueParam) {
        return userTagValueService.saveUserTagValue(userTagValueParam);
    }

    @GetMapping("/userTagValueTable")
    @Operation(summary = "所有用户标签值查询")
    public ResultData<UserTagValueTableVO> getUserTagValuesTable(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam("pageNo") Integer pageNo,
            @RequestParam("pageSize") Integer pageSize
    ) {
        return tagService.getUserTagValuesTable(keyword, pageNo, pageSize);
    }

    @GetMapping("/value/getByUserId")
    @Operation(summary = "根据用户ID查询用户标签值")
    public ResultData<List<UserTagValue>> getUserTagValuesByUserId(
            @RequestParam(value = "userId", required = false) Long userId
    ) {
        return tagService.getUserTagValuesByUserId(userId);
    }

}
