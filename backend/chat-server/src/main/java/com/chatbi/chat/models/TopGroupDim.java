package com.chatbi.chat.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
public class TopGroupDim implements Serializable {
    private static final long serialVersionUID = -2205399978074920957L;
    /**
     * 维度id
     */
    private Long id;
    /**
     * 维度key
     */
    private String dimKey;
    /**
     * 维度名称
     */
    private String dimName;

}
