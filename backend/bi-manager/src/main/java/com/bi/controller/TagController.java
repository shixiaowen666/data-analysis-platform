package com.bi.controller;

import com.bi.dto.TagSaveDTO;
import com.bi.service.ITagService;
import com.bi.vo.TagVO;
import com.common.base.UserThreadLocal;
import com.common.result.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Validated
@RestController
@RequestMapping("/v1/recommend/tag")
@RequiredArgsConstructor
public class TagController {

    private final ITagService tagService;

    @GetMapping("/list")
    public R<List<TagVO>> listTags(@RequestParam(required = false) String keyword) {
        return R.ok(tagService.listTags(keyword, getTenantId()));
    }

    @PostMapping("/save")
    public R<Void> saveTag(@Valid @RequestBody TagSaveDTO dto) {
        tagService.saveTag(dto, getTenantId());
        return R.ok();
    }

    @GetMapping("/del/{id}")
    public R<Void> deleteTag(@PathVariable Long id) {
        tagService.deleteTag(id, getTenantId());
        return R.ok();
    }

    private Long getTenantId() {
        return UserThreadLocal.get().getTenantId();
    }
}
