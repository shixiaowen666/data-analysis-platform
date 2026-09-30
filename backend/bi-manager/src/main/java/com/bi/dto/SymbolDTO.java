package com.bi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author zhuhongliang
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SymbolDTO implements Serializable {

    private static final long serialVersionUID = -5106634954826787549L;
    private String key;
    private String value;
}
