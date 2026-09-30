package com.senses.permission.listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;

import java.util.ArrayList;
import java.util.List;

public class ExcelHeadOneListener extends AnalysisEventListener<Object> {

    private static List<Object> list = new ArrayList<Object>();

    @Override
    public void invoke(Object t, AnalysisContext analysisContext) {
        list.add(t);

    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {

    }

    public static List<Object> getDataList(){
        return list;
    }

    /**
     * 清除数据
     */
    public static void clearDataList(){
        list.clear();
    }
}
