package com.chatbi.chat.models;

import com.google.common.base.Stopwatch;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author tongweijia
 * @version v1.0.0
 * @date 2019-08-13 15:09
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DatePeriodDTO implements Serializable {

    private static final Logger logger = LoggerFactory.getLogger(DatePeriodDTO.class);

    @Schema(description = "维度Id")
    private Long id;
    @Schema(description = "开始时间")
    @NotNull(message = "开始时间不能为空")
    private Long beginDate;
    @Schema(description = "结束时间")
    @NotNull(message = "结束时间不能为空")
    private Long endDate;

    private String beginDateStr;

    private String endDateStr;

    private List<Object> dateList = new ArrayList<>();


    private static LocalDateTime getToLocalDateTime(Long dateTemp) {
        LocalDateTime now = LocalDateTime.ofEpochSecond(dateTemp / 1000, 0, ZoneOffset.of("+8"));
        return now;
    }


    /**
     * dateTemp to String
     *
     * @param dateTemp
     * @param partFormat
     * @return
     */
    public String getDateStr(Long dateTemp, String partFormat) {
        LocalDateTime dtLocal = getToLocalDateTime(dateTemp);
        return dtLocal.format(DateTimeFormatter.ofPattern(partFormat));
    }





    public long getDaysNumByIsDay(LocalDateTime begin, LocalDateTime end, boolean isDays) {
        if (begin == null) {
            begin = getToLocalDateTime(this.beginDate);
        }
        if (end == null) {
            end = getToLocalDateTime(this.endDate);
        }
        long daysNum = 0;
        Duration duration = Duration.between(begin, end);

        if (isDays) {
            daysNum = duration.toDays();
        } else {
            daysNum = duration.toHours();
        }
        return daysNum;
    }


    /**
     * 获取begin 到下个节点差值
     *
     * @param num
     * @param cycle
     * @return
     */
    private static Integer getBeginMo(int num, int cycle) {
        int beginMo = num % cycle;
        beginMo = beginMo == 0 ? beginMo : cycle - beginMo;
        return beginMo;
    }

    /**
     * 获取日期list
     *
     * @param partFormat
     * @param isDays
     * @return
     */
    public List<Object> getDateListByPartFormat(String partFormat, boolean isDays) {

        if (!dateList.isEmpty()) {
            return dateList;
        }
        if (this.beginDate == null || this.endDate == null) {
            return Collections.emptyList();
        }
        LocalDateTime begin = getToLocalDateTime(this.beginDate);
        LocalDateTime end = getToLocalDateTime(this.endDate);

        long daysNum = getDaysNumByIsDay(begin, end, isDays);
        if (isDays) {
            //daysNum 小于0，说明，begin > end, daysNum 取相反数，begin用end
            if (daysNum < 0) {
                daysNum = -daysNum;
                begin = end;
            }
            daysNum = daysNum + 1;
            for (int i = 0; i < daysNum; i++) {
                LocalDateTime days = begin.plusDays(i);
                String dayStr = days.format(DateTimeFormatter.ofPattern(partFormat));
                dateList.add(dayStr);
            }
        }

        return dateList;
    }
}
