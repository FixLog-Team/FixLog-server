package com.fixlog.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 주기 작업 활성화.
 *
 * <p>현재는 이미지 정비(고아 정리, 보류 분석 재시도)가 유일한 사용처다.
 * 서버를 여러 대로 늘리면 인스턴스마다 같은 작업이 돌므로 잠금이나 전용 노드 지정이 필요하다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
