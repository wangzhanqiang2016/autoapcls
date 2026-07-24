package com.autoapcls.model.dto;

import lombok.Data;
import java.util.Map;

@Data
public class StepExecuteRequest {
    private Map<String, Object> params;
}
