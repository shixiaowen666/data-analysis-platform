package com.bi.vo;

import lombok.Data;

import java.util.List;

@Data
public class GroupCandidatesVO {

    private List<GroupCandidateVO> dimensions;

    private List<GroupCandidateVO> metrics;

    private List<GroupCandidateVO> groups;
}
