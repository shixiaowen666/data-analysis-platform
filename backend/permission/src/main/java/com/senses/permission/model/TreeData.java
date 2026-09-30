package com.senses.permission.model;

import io.swagger.v3.oas.annotations.media.Schema;
import com.senses.permission.entity.Dept;
import com.senses.permission.entity.User;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "层级根目录")
public class TreeData<T> {

    @Schema(description = "标志名称")
    private String label;
    @Schema(description = "分组key")
    private String key;
    @Schema(description = "每层节点id")
    private Object id;
    @Schema(description = "展示类型")
    private String showType;
    @Schema(description = "子节点")
    private List<TreeData<T>> children;
    @Schema(description = "节点数据")
    private T data;

    public static TreeData copyBaseInfo(TreeData baseTreeData) {
        TreeData treeData = new TreeData();
        treeData.setKey(baseTreeData.getKey());
        treeData.setId(baseTreeData.getId());
        treeData.setLabel(baseTreeData.getLabel());
        treeData.setShowType(baseTreeData.getShowType());
        return treeData;
    }
}
