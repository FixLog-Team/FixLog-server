package com.fixlog.presentation.dto.request;

/** 부분 갱신. null인 항목은 건드리지 않는다. */
public record SecurityPolicyRequest(
        Boolean allowSharing,
        Boolean allowDownload,
        Boolean enforceWatermark,
        Integer auditRetentionDays,
        Integer trashRetentionDays
) {
}
