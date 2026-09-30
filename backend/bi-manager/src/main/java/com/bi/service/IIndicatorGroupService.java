package com.bi.service;

import com.bi.dto.IndicatorGroupQueryDTO;
import com.bi.dto.IndicatorGroupSaveDTO;
import com.bi.vo.*;

public interface IIndicatorGroupService {

    ApiPageResult<IndicatorGroupVO> listGroups(IndicatorGroupQueryDTO query, Long tenantId);

    IndicatorGroupDetailVO getDetail(Long id, Long tenantId);

    GroupSaveResultVO saveGroup(IndicatorGroupSaveDTO dto, Long tenantId);

    GroupSaveResultVO onlineGroup(Long id, Long tenantId);

    void offlineGroup(Long id, Long tenantId);

    void deleteGroup(Long id, Long tenantId);

    GroupCandidatesVO listCandidates(String keyword, String candidateType, Long excludeGroupId, Long tenantId);
}
