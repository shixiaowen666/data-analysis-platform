package com.chatbi.chat.models;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
public class ThbIndParamDto implements Serializable {

	private static final long serialVersionUID = -7638381130915526653L;
	
	/***
	 * 日、周、月、年维度ID
	 */
	private Long dimId;
	
	/**
	 * 时间间隔
	 */
	private Integer interval;
	/** 
	 * 同环比
	 * @see
	 **/
	private Integer thbType;


	private String bmtDate;
	/**
	 * 指标后缀
	 */
	private String indSuffix;
	
	/**
	 * pddate信息
	 */
	private List<Map<String, String>> ptDateInfo;
}
