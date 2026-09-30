package com.bi.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bi.dto.TagSaveDTO;
import com.bi.entity.RecommendQuestionTag;
import com.bi.entity.Tag;
import com.bi.mapper.RecommendQuestionTagMapper;
import com.bi.mapper.TagMapper;
import com.bi.service.ITagService;
import com.bi.vo.TagVO;
import com.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TagServiceImpl implements ITagService {

    private final TagMapper tagMapper;
    private final RecommendQuestionTagMapper recommendQuestionTagMapper;

    @Override
    public List<TagVO> listTags(String keyword, Long tenantId) {
        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Tag::getTenantId, tenantId);
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.like(Tag::getName, keyword);
        }
        wrapper.orderByAsc(Tag::getId);
        return tagMapper.selectList(wrapper).stream().map(tag -> {
            TagVO vo = new TagVO();
            vo.setId(tag.getId());
            vo.setName(tag.getName());
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public void saveTag(TagSaveDTO dto, Long tenantId) {
        // 检查同名标签
        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Tag::getTenantId, tenantId).eq(Tag::getName, dto.getName());
        if (dto.getId() != null) {
            wrapper.ne(Tag::getId, dto.getId());
        }
        if (tagMapper.selectCount(wrapper) > 0) {
            throw new BizException(400, "标签名称已存在");
        }

        Tag tag = new Tag();
        tag.setName(dto.getName());
        tag.setTenantId(tenantId);
        if (dto.getId() != null) {
            tag.setId(dto.getId());
            tagMapper.updateById(tag);
        } else {
            tagMapper.insert(tag);
        }
    }

    @Override
    public void deleteTag(Long id, Long tenantId) {
        Tag tag = tagMapper.selectById(id);
        if (tag == null || !tag.getTenantId().equals(tenantId)) {
            throw new BizException(404, "标签不存在");
        }
        Long count = recommendQuestionTagMapper.selectCount(
                new LambdaQueryWrapper<RecommendQuestionTag>()
                        .eq(RecommendQuestionTag::getTagId, id));
        if (count > 0) {
            throw new BizException(400, "标签已被问题引用，无法删除");
        }
        tagMapper.deleteById(id);
    }
}
