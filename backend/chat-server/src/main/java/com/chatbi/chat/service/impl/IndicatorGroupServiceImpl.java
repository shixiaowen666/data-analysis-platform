package com.chatbi.chat.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.annotation.JSONField;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chatbi.chat.config.PagingProperties;
import com.chatbi.chat.entity.OlapBasicPro;
import com.chatbi.chat.entity.OlapGroupItem;
import com.chatbi.chat.feign.client.QueryServerClient;
import com.chatbi.chat.feign.dto.ColumnMeta;
import com.chatbi.chat.feign.dto.GetDataSqlResponse;
import com.chatbi.chat.feign.dto.QueryDataRequest;
import com.chatbi.chat.feign.dto.QueryDataResponse;
import com.chatbi.chat.mapper.OlapGroupItemMapper;
import com.chatbi.chat.service.IndicatorGroupService;
import com.chatbi.chat.service.IndicatorGroupService.ItemRequest;
import com.chatbi.chat.service.OlapBasicProService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class IndicatorGroupServiceImpl implements IndicatorGroupService {

    @Autowired
    private OlapGroupItemMapper olapGroupItemMapper;

    @Autowired
    private OlapBasicProService olapBasicProService;

    @Autowired
    private PagingProperties pagingProperties;
    @Autowired
    private QueryServerClient queryServerClient;

    @Override
    public JSONObject preview(List<ItemRequest> items) {
        // 1. 构建列树 + 收集叶子对象
        List<ColumnNode> columnTree = buildColumnTree(items);
        List<FlatLeaf> flatLeaves = collectLeaves(columnTree);

        // 2. 批量查 olap_basic_pro
        List<Long> objectIds = flatLeaves.stream().map(l -> l.item.getObjectId()).distinct().collect(Collectors.toList());
        List<OlapBasicPro> proList = olapBasicProService.listByIds(objectIds);
        Map<Long, OlapBasicPro> proMap = proList.stream()
                .collect(Collectors.toMap(OlapBasicPro::getId, p -> p, (k1, k2) -> k1));

        // 3. 填叶子元数据 + 去重
        Set<Long> seenDimIds = new LinkedHashSet<>();
        Set<Long> seenIndIds = new LinkedHashSet<>();
        List<QueryDataRequest.Sort> sorts = new ArrayList<>();
        List<String> columnOrder = new ArrayList<>();
        for (FlatLeaf leaf : flatLeaves) {
            OlapBasicPro pro = proMap.get(leaf.item.getObjectId());
            if (pro == null || pro.getCategory() == null) continue;
            leaf.englishName = pro.getEnglishName();
            leaf.node.setKey(pro.getEnglishName());
            if (leaf.node.getName() == null) {
                leaf.node.setName(pro.getChineseName());
            }
            leaf.node.setType(pro.getCategory() == 1 ? "dimension" : "indicator");
            if (pro.getCategory() == 1) {
                seenDimIds.add(pro.getId());
            } else if (pro.getCategory() == 2) {
                seenIndIds.add(pro.getId());
                if (leaf.item.getDefaultSort() != null) {
                    QueryDataRequest.Sort s = new QueryDataRequest.Sort();
                    s.setBasicId(pro.getId());
                    s.setDirection(leaf.item.getDefaultSort());
                    sorts.add(s);
                }
            }
            columnOrder.add(pro.getEnglishName());
        }

        // 4. 构建请求
        QueryDataRequest request = new QueryDataRequest();
        request.setDimensionIds(new ArrayList<>(seenDimIds));
        request.setIndicatorIds(new ArrayList<>(seenIndIds));
        request.setPreferredTableIds(Collections.emptyList());
        request.setPreferredModelIds(Collections.emptyList());
        if (!sorts.isEmpty()) {
            request.setSorts(sorts);
        }
        QueryDataRequest.Paging paging = new QueryDataRequest.Paging();
        paging.setPage(pagingProperties.getDefaultPage());
        paging.setPageSize(pagingProperties.getPreviewPageSize());
        request.setPaging(paging);
        log.info("**************************************************");
        log.info("**** 跨模块调用 开始: data-server.execute (preview) ****");
        log.info("**************************************************");
        log.info(">>> group/preview data-server request: {}", JSON.toJSONString(request));
        long callStart = System.currentTimeMillis();
        QueryDataResponse<GetDataSqlResponse> response = queryServerClient.execute(request);
        long cost = System.currentTimeMillis() - callStart;
        log.info("**************************************************");
        log.info("**** 跨模块调用 结束: data-server.execute (preview), 耗时={}ms ****", cost);
        log.info("**************************************************");
        log.info("<<< group/preview data-server response: {}", JSON.toJSONString(response));

        // 6. 构建返回
        JSONObject result = new JSONObject();
        if (response != null) {
            result.put("code", response.getCode());
            result.put("message", response.getMessage());
        }
        if (response != null && response.getData() != null) {
            GetDataSqlResponse data = response.getData();
            Map<String, ColumnMeta> colMap = new LinkedHashMap<>();
            if (CollectionUtils.isNotEmpty(data.getColumns())) {
                for (ColumnMeta c : data.getColumns()) {
                    colMap.put(c.getKey(), c);
                }
            }
            for (FlatLeaf leaf : flatLeaves) {
                ColumnMeta meta = colMap.get(leaf.englishName);
                if (meta != null) {
                    leaf.node.setUnit(meta.getUnit());
                    leaf.node.setPrecision(meta.getPrecision());
                }
            }
            JSONObject dataObj = new JSONObject();
            dataObj.put("total", data.getTotal());
            dataObj.put("columns", columnTree);
            dataObj.put("sql", data.getSql());
            dataObj.put("page", data.getPage());
            dataObj.put("pageSize", data.getPageSize());
            dataObj.put("records", data.getRecords());
            result.put("data", dataObj);
        }
        return result;
    }

    private List<ColumnNode> buildColumnTree(List<ItemRequest> items) {
        List<ColumnNode> tree = new ArrayList<>();
        for (ItemRequest item : items) {
            if ("indicator_group".equals(item.getItemType())) {
                List<OlapGroupItem> children = olapGroupItemMapper.selectList(
                        new LambdaQueryWrapper<OlapGroupItem>()
                                .eq(OlapGroupItem::getGroupId, item.getObjectId())
                                .orderByAsc(OlapGroupItem::getDisplayOrder));
                ColumnNode group = new ColumnNode();
                group.setName(item.getDisplayName());
                group.setChildren(buildColumnFromGroupItems(children));
                tree.add(group);
            } else {
                ColumnNode leaf = new ColumnNode();
                leaf.setItem(item);
                tree.add(leaf);
            }
        }
        return tree;
    }

    private List<ColumnNode> buildColumnFromGroupItems(List<OlapGroupItem> children) {
        List<ColumnNode> nodes = new ArrayList<>();
        for (OlapGroupItem child : children) {
            ColumnNode node = new ColumnNode();
            if ("indicator_group".equals(child.getItemType())) {
                node.setName(child.getDisplayName());
                List<OlapGroupItem> grandchildren = olapGroupItemMapper.selectList(
                        new LambdaQueryWrapper<OlapGroupItem>()
                                .eq(OlapGroupItem::getGroupId, child.getObjectId())
                                .orderByAsc(OlapGroupItem::getDisplayOrder));
                node.setChildren(buildColumnFromGroupItems(grandchildren));
            } else {
                node.setName(child.getDisplayName());
                ItemRequest ir = new ItemRequest();
                ir.setItemType(child.getItemType());
                ir.setObjectId(child.getObjectId());
                ir.setDefaultSort(child.getDefaultSort());
                node.setItem(ir);
            }
            nodes.add(node);
        }
        return nodes;
    }

    private List<FlatLeaf> collectLeaves(List<ColumnNode> nodes) {
        List<FlatLeaf> leaves = new ArrayList<>();
        for (ColumnNode node : nodes) {
            if (node.getChildren() != null) {
                leaves.addAll(collectLeaves(node.getChildren()));
            } else if (node.getItem() != null) {
                FlatLeaf leaf = new FlatLeaf();
                leaf.node = node;
                leaf.item = node.getItem();
                leaves.add(leaf);
            }
        }
        return leaves;
    }

    @Data
    public static class ColumnNode {
        private String key;
        private String name;
        private String type;
        private String unit;
        private Integer precision;
        private List<ColumnNode> children;
        @JSONField(serialize = false)
        private transient ItemRequest item;
    }

    @Data
    private static class FlatLeaf {
        private ColumnNode node;
        private ItemRequest item;
        private String englishName;
    }
}
