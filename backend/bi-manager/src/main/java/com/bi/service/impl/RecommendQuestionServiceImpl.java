package com.bi.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bi.dto.RecommendQuestionQueryDTO;
import com.bi.dto.RecommendQuestionSaveDTO;
import com.bi.entity.RecommendQuestion;
import com.bi.entity.RecommendQuestionTag;
import com.bi.entity.Tag;
import com.bi.mapper.RecommendQuestionMapper;
import com.bi.mapper.RecommendQuestionTagMapper;
import com.bi.mapper.TagMapper;
import com.bi.service.IRecommendQuestionService;
import com.bi.vo.ApiPageResult;
import com.bi.vo.RecommendQuestionVO;
import com.bi.vo.TagVO;
import com.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendQuestionServiceImpl implements IRecommendQuestionService {

    private final RecommendQuestionMapper recommendQuestionMapper;
    private final RecommendQuestionTagMapper recommendQuestionTagMapper;
    private final TagMapper tagMapper;

    @Override
    public ApiPageResult<RecommendQuestionVO> listQuestions(RecommendQuestionQueryDTO query, Long tenantId) {
        LambdaQueryWrapper<RecommendQuestion> wrapper = new LambdaQueryWrapper<>();

        // 标签筛选：先查出关联的问题ID
        if (query.getTagId() != null) {
            List<Long> questionIds = recommendQuestionTagMapper.selectList(
                    new LambdaQueryWrapper<RecommendQuestionTag>()
                            .eq(RecommendQuestionTag::getTagId, query.getTagId())
                            .eq(RecommendQuestionTag::getTenantId, tenantId))
                    .stream()
                    .map(RecommendQuestionTag::getQuestionId)
                    .collect(Collectors.toList());
            if (questionIds.isEmpty()) {
                return new ApiPageResult<>(Collections.emptyList(), 0L, (long) query.getPage(), (long) query.getPageSize());
            }
            wrapper.in(RecommendQuestion::getId, questionIds);
        }

        wrapper.eq(RecommendQuestion::getAiBodyCode, query.getAiBodyCode())
                .eq(RecommendQuestion::getTenantId, tenantId);
        if (StrUtil.isNotBlank(query.getKeyword())) {
            wrapper.like(RecommendQuestion::getQuestion, query.getKeyword());
        }
        wrapper.orderByAsc(RecommendQuestion::getSortOrder);

        Page<RecommendQuestion> page = recommendQuestionMapper.selectPage(
                new Page<>(query.getPage(), query.getPageSize()), wrapper);

        List<RecommendQuestion> records = page.getRecords();
        if (records.isEmpty()) {
            return new ApiPageResult<>(Collections.emptyList(), page.getTotal(), page.getCurrent(), page.getSize());
        }

        // 批量查标签
        List<Long> questionIds = records.stream().map(RecommendQuestion::getId).collect(Collectors.toList());
        Map<Long, List<TagVO>> tagMap = buildTagMap(questionIds);

        List<RecommendQuestionVO> voList = records.stream().map(q -> {
            RecommendQuestionVO vo = new RecommendQuestionVO();
            vo.setId(q.getId());
            vo.setAiBodyCode(q.getAiBodyCode());
            vo.setQuestion(q.getQuestion());
            vo.setDescription(q.getDescription());
            vo.setSortOrder(q.getSortOrder());
            vo.setStatus(q.getStatus());
            vo.setTags(tagMap.getOrDefault(q.getId(), Collections.emptyList()));
            vo.setCreatedAt(q.getCreatedAt());
            return vo;
        }).collect(Collectors.toList());

        return new ApiPageResult<>(voList, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveQuestion(RecommendQuestionSaveDTO dto, Long tenantId) {
        RecommendQuestion question = new RecommendQuestion();
        question.setAiBodyCode(dto.getAiBodyCode());
        question.setQuestion(dto.getQuestion());
        question.setDescription(dto.getDescription());
        question.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        question.setTenantId(tenantId);

        if (dto.getId() != null) {
            question.setId(dto.getId());
            recommendQuestionMapper.updateById(question);
            // 先删旧的标签关联，再插入新的
            recommendQuestionTagMapper.delete(
                    new LambdaQueryWrapper<RecommendQuestionTag>()
                            .eq(RecommendQuestionTag::getQuestionId, dto.getId()));
        } else {
            question.setStatus(1);
            recommendQuestionMapper.insert(question);
        }

        // 插入标签关联
        if (dto.getTagIds() != null && !dto.getTagIds().isEmpty()) {
            for (Long tagId : dto.getTagIds()) {
                RecommendQuestionTag rel = new RecommendQuestionTag();
                rel.setQuestionId(question.getId());
                rel.setTagId(tagId);
                rel.setTenantId(tenantId);
                rel.setCreatedAt(java.time.LocalDateTime.now());
                recommendQuestionTagMapper.insert(rel);
            }
        }
    }

    @Override
    public void updateStatus(Long id, Integer status, Long tenantId) {
        RecommendQuestion question = recommendQuestionMapper.selectById(id);
        if (question == null || !question.getTenantId().equals(tenantId)) {
            throw new BizException(404, "推荐问题不存在");
        }
        question.setStatus(status);
        recommendQuestionMapper.updateById(question);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteQuestion(Long id, Long tenantId) {
        RecommendQuestion question = recommendQuestionMapper.selectById(id);
        if (question == null || !question.getTenantId().equals(tenantId)) {
            throw new BizException(404, "推荐问题不存在");
        }
        recommendQuestionTagMapper.delete(
                new LambdaQueryWrapper<RecommendQuestionTag>()
                        .eq(RecommendQuestionTag::getQuestionId, id));
        recommendQuestionMapper.deleteById(id);
    }

    private Map<Long, List<TagVO>> buildTagMap(List<Long> questionIds) {
        List<RecommendQuestionTag> relations = recommendQuestionTagMapper.selectList(
                new LambdaQueryWrapper<RecommendQuestionTag>()
                        .in(RecommendQuestionTag::getQuestionId, questionIds));
        if (relations.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> tagIds = relations.stream().map(RecommendQuestionTag::getTagId).distinct().collect(Collectors.toList());
        Map<Long, Tag> tagMap = tagMapper.selectBatchIds(tagIds).stream()
                .collect(Collectors.toMap(Tag::getId, t -> t));

        Map<Long, List<TagVO>> result = new HashMap<>();
        for (RecommendQuestionTag rel : relations) {
            Tag tag = tagMap.get(rel.getTagId());
            if (tag != null) {
                TagVO vo = new TagVO();
                vo.setId(tag.getId());
                vo.setName(tag.getName());
                result.computeIfAbsent(rel.getQuestionId(), k -> new ArrayList<>()).add(vo);
            }
        }
        return result;
    }
}
