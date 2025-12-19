package com.inkflow.module.session.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class SessionCreateRequest {
    private String deviceInfo;
    private String ipAddress;
    private String userAgent;
    private UUID currentProjectId;
}
