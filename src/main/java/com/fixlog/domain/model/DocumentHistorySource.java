package com.fixlog.domain.model;

/** 히스토리 버전이 만들어진 계기. UI에서 버전 배지로 구분해 표시한다. */
public enum DocumentHistorySource {
    /** 사용자가 문서를 저장해 이전 내용이 밀려난 경우 */
    MANUAL,
    /** 과거 버전으로 복원하면서 복원 직전 내용이 밀려난 경우 */
    RESTORE
}
