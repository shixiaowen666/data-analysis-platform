package com.chatbi.chat.models;

import lombok.Data;

import java.io.Serializable;

@Data
public class ThbIndCalDto implements Serializable {

	private static final long serialVersionUID = -5204502734965160407L;
	
	/** 
	 * 指标ID
	 **/
	private Long indId;
	
	/** 
	 * 同环比
	 * @see
	 **/
	private Integer thbType;


	private String bmtDate;
	/** 
	 * 同环比计算方式
	 *  0：(本期-上期)/|上期期|；  1：本期-上期
	 **/
	private Integer thbCalType;
	
	/**
	 * 指标后缀
	 */
	private String indSuffix;
}
