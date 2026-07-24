package com.autoapcls.model.dto;

import lombok.Data;

@Data
public class SessionSelectRequest {
    private Integer respId;
    private String respName;
    private Integer orgId;
    private String orgCode;
    private String periodName;
}
