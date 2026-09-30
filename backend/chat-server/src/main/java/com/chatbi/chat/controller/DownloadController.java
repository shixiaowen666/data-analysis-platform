package com.chatbi.chat.controller;

import com.alibaba.excel.EasyExcel;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chatbi.chat.entity.DcarChatModelQA;
import com.chatbi.chat.enums.ResultCode;
import com.chatbi.chat.models.DownloadParamDTO;
import com.chatbi.chat.models.SaasUser;
import com.chatbi.chat.response.CommonVo;
import com.chatbi.chat.service.ChatModelQAService;
import com.chatbi.chat.service.impl.MinioService;
import com.chatbi.chat.utils.UserThreadLocal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 快照下载接口：从 MinIO 读取步骤快照生成 xlsx/docx，不回源查库。
 */
@RestController
@RequestMapping({"/api/chat-server"})
@Tag(name = "快照下载")
@Slf4j
public class DownloadController {

    @Resource
    private ChatModelQAService chatModelQAService;
    @Resource
    private MinioService minioService;

    @Value("${minio.chatdata.bucketName}")
    private String bucketName;

    @Operation(summary = "对话级结果查询", description = "按 chatSessionId+chatId 返回该对话全部步骤的快照结果(question/stepType/chartData)")
    @RequestMapping(value = "/history/chat", method = RequestMethod.POST)
    public CommonVo historyChat(@RequestBody DownloadParamDTO param) {
        String chatSessionId = param.getChatSessionId();
        String chatId = param.getChatId();
        if (StringUtils.isBlank(chatSessionId) || StringUtils.isBlank(chatId)) {
            return CommonVo.Builder.fail(ResultCode.BAD_REQUEST, "参数缺失：chatSessionId/chatId 不能为空");
        }
        SaasUser currentUser = UserThreadLocal.get();
        if (currentUser == null || currentUser.getId() == null) {
            return CommonVo.Builder.fail(ResultCode.UNAUTHORIZED, "未获取到用户信息");
        }
        List<DcarChatModelQA> records = chatModelQAService.listByChatKey(chatSessionId, chatId, currentUser.getId());
        if (records == null || records.isEmpty()) {
            return CommonVo.Builder.fail(ResultCode.NOT_FOUND, "记录不存在");
        }
        JSONArray items = new JSONArray();
        for (DcarChatModelQA record : records) {
            if (StringUtils.isBlank(record.getMinioFilePath())) {
                log.warn("步骤无快照数据，跳过: chatSessionId={}, chatId={}, itemId={}",
                        chatSessionId, chatId, record.getItemId());
                continue;
            }
            try (InputStream in = minioService.getObject(bucketName, record.getMinioFilePath())) {
                String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                JSONObject snapshot = JSON.parseObject(json);
                if (snapshot == null) {
                    continue;
                }
                JSONObject item = new JSONObject();
                item.put("itemId", record.getItemId());
                item.put("stepType", snapshot.getString("stepType"));
                item.put("question", StringUtils.defaultIfBlank(snapshot.getString("question"), record.getQuestion()));
                item.put("chartData", snapshot.get("chartData"));
                items.add(item);
            } catch (Exception e) {
                log.warn("读取MinIO快照失败，跳过该步骤: chatSessionId={}, chatId={}, itemId={}, filePath={}, error={}",
                        chatSessionId, chatId, record.getItemId(), record.getMinioFilePath(), e.getMessage());
            }
        }
        JSONObject data = new JSONObject();
        data.put("chatSessionId", chatSessionId);
        data.put("chatId", chatId);
        data.put("items", items);
        return CommonVo.Builder.SUCC().initSuccData(data);
    }

    @Operation(summary = "对话级步骤清除", description = "清除该对话下 itemId>0 的所有步骤记录及MinIO快照，保留 itemId=0 的用户提问；建议在 close 后调用")
    @RequestMapping(value = "/history/chat/clear", method = RequestMethod.POST)
    public CommonVo clearHistoryChat(@RequestBody DownloadParamDTO param) {
        String chatSessionId = param.getChatSessionId();
        String chatId = param.getChatId();
        if (StringUtils.isBlank(chatSessionId) || StringUtils.isBlank(chatId)) {
            return CommonVo.Builder.fail(ResultCode.BAD_REQUEST, "参数缺失：chatSessionId/chatId 不能为空");
        }
        SaasUser currentUser = UserThreadLocal.get();
        if (currentUser == null || currentUser.getId() == null) {
            return CommonVo.Builder.fail(ResultCode.UNAUTHORIZED, "未获取到用户信息");
        }
        List<DcarChatModelQA> records = chatModelQAService.listByChatKey(chatSessionId, chatId, currentUser.getId());
        int deletedFiles = 0;
        List<String> failedFiles = new ArrayList<>();
        for (DcarChatModelQA record : records) {
            if (record.getItemId() == null || record.getItemId() <= 0
                    || StringUtils.isBlank(record.getMinioFilePath())) {
                continue;
            }
            try {
                minioService.removeObject(bucketName, record.getMinioFilePath());
                deletedFiles++;
            } catch (Exception e) {
                log.warn("删除MinIO快照失败: chatSessionId={}, chatId={}, itemId={}, filePath={}, error={}",
                        chatSessionId, chatId, record.getItemId(), record.getMinioFilePath(), e.getMessage());
                failedFiles.add(record.getMinioFilePath());
            }
        }
        int deletedSteps = chatModelQAService.removeStepsByChatKey(chatSessionId, chatId, currentUser.getId());
        log.info("对话步骤清除完成: chatSessionId={}, chatId={}, deletedSteps={}, deletedFiles={}, failedFiles={}",
                chatSessionId, chatId, deletedSteps, deletedFiles, failedFiles);
        JSONObject data = new JSONObject();
        data.put("chatSessionId", chatSessionId);
        data.put("chatId", chatId);
        data.put("deletedSteps", deletedSteps);
        data.put("deletedFiles", deletedFiles);
        data.put("failedFiles", failedFiles);
        return CommonVo.Builder.SUCC().initSuccData(data);
    }

    @Operation(summary = "下载步骤快照文件", description = "query/compute生成xlsx，analyze/summarize生成docx，其他步骤类型不支持")
    @RequestMapping(value = "/download/history/item", method = RequestMethod.POST)
    public void downloadFile(@RequestBody DownloadParamDTO param,
                             HttpServletResponse response) throws IOException {
        String chatSessionId = param.getChatSessionId();
        String chatId = param.getChatId();
        Integer itemId = param.getItemId();
        if (StringUtils.isBlank(chatSessionId) || StringUtils.isBlank(chatId) || itemId == null) {
            writeError(response, 400, "参数缺失：chatSessionId/chatId/itemId 不能为空");
            return;
        }
        DcarChatModelQA record = chatModelQAService.getByChatKey(chatSessionId, chatId, itemId);
        if (record == null) {
            writeError(response, 404, "记录不存在");
            return;
        }
        SaasUser currentUser = UserThreadLocal.get();
        if (currentUser == null || currentUser.getId() == null || !currentUser.getId().equals(record.getCreatedBy())) {
            writeError(response, 403, "无权下载他人记录");
            return;
        }
        if (StringUtils.isBlank(record.getMinioFilePath())) {
            writeError(response, 404, "该步骤无快照数据");
            return;
        }

        JSONObject snapshot;
        try (InputStream in = minioService.getObject(bucketName, record.getMinioFilePath())) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            snapshot = JSON.parseObject(json);
        } catch (Exception e) {
            log.error("读取MinIO快照失败: filePath={}", record.getMinioFilePath(), e);
            writeError(response, 404, "快照文件不存在，无法下载");
            return;
        }
        if (snapshot == null) {
            writeError(response, 500, "快照数据解析失败");
            return;
        }

        String stepType = StringUtils.defaultString(snapshot.getString("stepType"));
        String question = snapshot.getString("question");
        JSONObject chartData = snapshot.getJSONObject("chartData");
        Object dataVO = chartData != null ? chartData.get("dataVO") : null;

        switch (stepType) {
            case "query":
                writeQueryExcel(dataVO, response);
                break;
            case "compute":
                writeComputeExcel(dataVO, response);
                break;
            case "analyze":
            case "summarize":
                writeAnswerDocx(question, dataVO, response);
                break;
            default:
                writeError(response, 400, "该步骤类型不支持下载");
        }
    }

    /** query：dataVO = QueryDataResponse，取 data.columns + data.records */
    private void writeQueryExcel(Object dataVO, HttpServletResponse response) throws IOException {
        if (!(dataVO instanceof JSONObject)) {
            writeError(response, 404, "该步骤无快照数据可导出");
            return;
        }
        JSONObject data = ((JSONObject) dataVO).getJSONObject("data");
        if (data == null) {
            writeError(response, 404, "该步骤无快照数据可导出");
            return;
        }
        JSONArray columns = data.getJSONArray("columns");
        JSONArray records = data.getJSONArray("records");
        List<List<String>> headers = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        if (columns != null) {
            for (int i = 0; i < columns.size(); i++) {
                JSONObject col = columns.getJSONObject(i);
                String key = col.getString("key");
                String name = col.getString("name");
                keys.add(key);
                headers.add(List.of(StringUtils.isNotBlank(name) ? name : key));
            }
        }
        List<List<Object>> rows = new ArrayList<>();
        if (records != null) {
            for (int i = 0; i < records.size(); i++) {
                JSONObject record = records.getJSONObject(i);
                List<Object> row = new ArrayList<>();
                for (String key : keys) {
                    row.add(record.get(key));
                }
                rows.add(row);
            }
        }
        writeExcelResponse(response, headers, rows);
    }

    /** compute：dataVO = {columns:[{name,role,data_type,description}], rows:[...]} */
    private void writeComputeExcel(Object dataVO, HttpServletResponse response) throws IOException {
        if (!(dataVO instanceof JSONObject)) {
            writeError(response, 404, "该步骤无快照数据可导出");
            return;
        }
        JSONObject obj = (JSONObject) dataVO;
        JSONArray columns = obj.getJSONArray("columns");
        JSONArray rowsArr = obj.getJSONArray("rows");
        List<List<String>> headers = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        if (columns != null) {
            for (int i = 0; i < columns.size(); i++) {
                JSONObject col = columns.getJSONObject(i);
                String name = col.getString("name");
                String cnName = col.getString("cn_name");
                keys.add(name);
                headers.add(List.of(StringUtils.isNotBlank(cnName) ? cnName : name));
            }
        }
        List<List<Object>> rows = new ArrayList<>();
        if (rowsArr != null) {
            for (int i = 0; i < rowsArr.size(); i++) {
                JSONObject record = rowsArr.getJSONObject(i);
                List<Object> row = new ArrayList<>();
                for (String key : keys) {
                    row.add(record.get(key));
                }
                rows.add(row);
            }
        }
        writeExcelResponse(response, headers, rows);
    }

    private void writeExcelResponse(HttpServletResponse response, List<List<String>> headers, List<List<Object>> rows) throws IOException {
        String fileName = "数据导出_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode(fileName, "UTF-8"));
        EasyExcel.write(response.getOutputStream()).head(headers).sheet("数据").doWrite(rows);
        log.info("xlsx导出完成: {}行", rows.size());
    }

    /** analyze/summarize：提取文本生成 docx，标题=question，正文=答案文本 */
    private void writeAnswerDocx(String question, Object dataVO, HttpServletResponse response) throws IOException {
        String text = extractAnswerText(dataVO);
        if (text == null) {
            writeError(response, 404, "该步骤无文本内容可导出");
            return;
        }
        String fileName = "数据导出_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".docx";
        response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode(fileName, "UTF-8"));

        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph title = doc.createParagraph();
            XWPFRun titleRun = title.createRun();
            titleRun.setBold(true);
            titleRun.setFontSize(16);
            titleRun.setText(StringUtils.isNotBlank(question) ? question : "查询结果");

            String[] lines = text.split("\n");
            for (String line : lines) {
                XWPFParagraph p = doc.createParagraph();
                p.createRun().setText(line);
            }
            doc.write(response.getOutputStream());
        }
        log.info("docx导出完成: question={}", question);
    }

    private String extractAnswerText(Object dataVO) {
        if (dataVO == null) {
            return null;
        }
        if (dataVO instanceof String) {
            return StringUtils.trimToNull((String) dataVO);
        }
        if (dataVO instanceof JSONObject) {
            JSONObject obj = (JSONObject) dataVO;
            String[] preferredKeys = {"expected_answer", "answer", "summary", "content"};
            for (String key : preferredKeys) {
                Object value = obj.get(key);
                if (value instanceof String && StringUtils.isNotBlank((String) value)) {
                    return (String) value;
                }
            }
            // compute 风格 {columns, rows}：取文本列（名称含 answer/result/analysis/summary）的值
            String fromRows = extractTextFromRows(obj);
            if (fromRows != null) {
                return fromRows;
            }
            for (Object value : obj.values()) {
                if (value instanceof String && StringUtils.isNotBlank((String) value)) {
                    return (String) value;
                }
            }
        }
        return null;
    }

    private String extractTextFromRows(JSONObject obj) {
        JSONArray rows = obj.getJSONArray("rows");
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        JSONArray columns = obj.getJSONArray("columns");
        String bestCol = null;
        if (columns != null) {
            for (int i = 0; i < columns.size(); i++) {
                JSONObject col = columns.getJSONObject(i);
                String name = col.getString("name");
                if (StringUtils.isBlank(name)) {
                    continue;
                }
                String lower = name.toLowerCase();
                if (lower.contains("answer") || lower.contains("result")
                        || lower.contains("analysis") || lower.contains("summary")) {
                    bestCol = name;
                    break;
                }
            }
        }
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            JSONObject row = rows.getJSONObject(i);
            if (row == null) {
                continue;
            }
            if (bestCol != null) {
                Object value = row.get(bestCol);
                if (value instanceof String && StringUtils.isNotBlank((String) value)) {
                    texts.add((String) value);
                }
            } else {
                for (Object value : row.values()) {
                    if (value instanceof String && StringUtils.isNotBlank((String) value)) {
                        texts.add((String) value);
                    }
                }
            }
        }
        if (texts.isEmpty()) {
            return null;
        }
        return String.join("\n", texts);
    }

    private void writeError(HttpServletResponse response, int httpStatus, String msg) throws IOException {
        response.setStatus(httpStatus);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().print(JSON.toJSONString(CommonVo.Builder.fail(ResultCode.INTERNAL_ERROR, msg)));
    }
}
