package com.bi.service;

import com.bi.dto.TagSaveDTO;
import com.bi.vo.TagVO;

import java.util.List;

public interface ITagService {

    List<TagVO> listTags(String keyword, Long tenantId);

    void saveTag(TagSaveDTO dto, Long tenantId);

    void deleteTag(Long id, Long tenantId);
}
