package com.chatbi.chat.models;

import lombok.Data;

import java.io.Serializable;

@Data
public class DimInfo implements Serializable {

	private static final long serialVersionUID = 3248913554903053905L;
	private Long id;
	private String dimName;
	private String dimKey;

}
