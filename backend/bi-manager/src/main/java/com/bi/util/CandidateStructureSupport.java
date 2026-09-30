package com.bi.util;

import cn.hutool.core.util.StrUtil;
import com.bi.entity.OlapBasicProDimension;
import com.bi.entity.OlapBasicProIndicator;
import com.bi.enums.GroupItemType;
import com.bi.vo.CompatibleFieldVO;
import com.bi.vo.GroupCandidateVO;
import com.bi.vo.StructureConfigDefaults;

/**
 * 候选字段结构配置默认值
 */
public final class CandidateStructureSupport {

    private CandidateStructureSupport() {
    }

    public static void applyDimensionDefaults(GroupCandidateVO vo, OlapBasicProDimension dim) {
        StructureConfigDefaults defaults = new StructureConfigDefaults();
        defaults.setDisplayName(vo.getName());
        defaults.setDisplayOrder(null);
        defaults.setItemTypeName(GroupItemType.DIMENSION.getDesc());
        defaults.setIsRequired(1);
        defaults.setIsDefaultVisible(1);
        defaults.setFormatType(null);
        defaults.setUnit(null);
        if (dim != null && (StrUtil.isNotBlank(dim.getPartitionField()) || StrUtil.isNotBlank(dim.getTimeDynamic()))) {
            defaults.setDefaultSort("DESC");
        } else {
            defaults.setDefaultSort("default");
        }
        copyTo(vo, defaults);
    }

    public static void applyMetricDefaults(GroupCandidateVO vo, OlapBasicProIndicator indicator, GroupItemType itemType) {
        applyMetricDefaults(vo, indicator.getUnit() , itemType);
    }

    public static void applyMetricDefaults(GroupCandidateVO vo, String unit, GroupItemType itemType) {
        StructureConfigDefaults defaults = new StructureConfigDefaults();
        defaults.setDisplayName(vo.getName());
        defaults.setDisplayOrder(null);
        defaults.setItemTypeName(itemType.getDesc());
        defaults.setIsRequired(1);
        defaults.setIsDefaultVisible(1);
        defaults.setDefaultSort("default");
        defaults.setFormatType(unit);
        defaults.setUnit(unit);
        //applyUnitDefaults(defaults, unit);
        copyTo(vo, defaults);
    }

    public static void applyGroupDefaults(GroupCandidateVO vo) {
        StructureConfigDefaults defaults = new StructureConfigDefaults();
        defaults.setDisplayName(vo.getName());
        defaults.setDisplayOrder(null);
        defaults.setItemTypeName(GroupItemType.INDICATOR_GROUP.getDesc());
        defaults.setIsRequired(0);
        defaults.setIsDefaultVisible(0);
        defaults.setFormatType(null);
        defaults.setUnit(null);
        defaults.setDefaultSort("default");
        copyTo(vo, defaults);
    }

    public static void applyDimensionDefaults(CompatibleFieldVO vo, Integer dimensionType,
                                              String partitionField, String timeDynamic) {
        OlapBasicProDimension dim = new OlapBasicProDimension();
        dim.setDimensionType(dimensionType);
        dim.setPartitionField(partitionField);
        dim.setTimeDynamic(timeDynamic);
        GroupCandidateVO temp = new GroupCandidateVO();
        temp.setName(vo.getName());
        applyDimensionDefaults(temp, dim);
        copyStructureToCompatible(temp, vo);
    }

    public static void applyMetricDefaults(CompatibleFieldVO vo, String unitType, GroupItemType itemType) {
        GroupCandidateVO temp = new GroupCandidateVO();
        temp.setName(vo.getName());
        applyMetricDefaults(temp, unitType, itemType);
        copyStructureToCompatible(temp, vo);
    }

    public static StructureConfigDefaults buildDimensionDefaults(String name, OlapBasicProDimension dim) {
        GroupCandidateVO vo = new GroupCandidateVO();
        vo.setName(name);
        applyDimensionDefaults(vo, dim);
        return extractDefaults(vo);
    }

    public static StructureConfigDefaults buildMetricDefaults(String name, OlapBasicProIndicator indicator,
                                                              GroupItemType itemType) {
        GroupCandidateVO vo = new GroupCandidateVO();
        vo.setName(name);
        applyMetricDefaults(vo, indicator, itemType);
        return extractDefaults(vo);
    }

    private static void applyUnitDefaults(StructureConfigDefaults defaults, Integer unitType) {
        if (unitType == null) {
            defaults.setFormatType(null);
            defaults.setUnit(null);
            return;
        }
        switch (unitType) {
            case 1:
                defaults.setFormatType("amount");
                defaults.setUnit("元");
                break;
            case 2:
                defaults.setFormatType("percent");
                defaults.setUnit("%");
                break;
            default:
                defaults.setFormatType(null);
                defaults.setUnit(null);
                break;
        }
    }

    private static Integer resolveUnitType(String unit) {
        if (StrUtil.isBlank(unit)) {
            return null;
        }
        String value = unit.trim();
        if ("1".equals(value) || "元".equals(value)) {
            return 1;
        }
        if ("2".equals(value) || "%".equals(value)) {
            return 2;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static void copyTo(GroupCandidateVO vo, StructureConfigDefaults defaults) {
        vo.setDisplayName(defaults.getDisplayName());
        vo.setDisplayOrder(defaults.getDisplayOrder());
        vo.setItemTypeName(defaults.getItemTypeName());
        vo.setIsRequired(defaults.getIsRequired());
        vo.setIsDefaultVisible(defaults.getIsDefaultVisible());
        vo.setFormatType(defaults.getFormatType());
        vo.setUnit(defaults.getUnit());
        vo.setDefaultSort(defaults.getDefaultSort());
    }

    private static void copyStructureToCompatible(GroupCandidateVO from, CompatibleFieldVO to) {
        to.setDisplayName(from.getDisplayName());
        to.setDisplayOrder(from.getDisplayOrder());
        to.setItemTypeName(from.getItemTypeName());
        to.setIsRequired(from.getIsRequired());
        to.setIsDefaultVisible(from.getIsDefaultVisible());
        to.setFormatType(from.getFormatType());
        to.setUnit(from.getUnit());
        to.setDefaultSort(from.getDefaultSort());
    }

    private static StructureConfigDefaults extractDefaults(GroupCandidateVO vo) {
        StructureConfigDefaults defaults = new StructureConfigDefaults();
        defaults.setDisplayName(vo.getDisplayName());
        defaults.setDisplayOrder(vo.getDisplayOrder());
        defaults.setItemTypeName(vo.getItemTypeName());
        defaults.setIsRequired(vo.getIsRequired());
        defaults.setIsDefaultVisible(vo.getIsDefaultVisible());
        defaults.setFormatType(vo.getFormatType());
        defaults.setUnit(vo.getUnit());
        defaults.setDefaultSort(vo.getDefaultSort());
        return defaults;
    }
}
