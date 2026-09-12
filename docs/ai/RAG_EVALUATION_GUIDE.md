# RAG 검색 평가 가이드

## 목적

`similarity-threshold`, `top-k`, 후보 배수, 문서별 최대 청크 수를 감으로 결정하지 않고
FixLog의 실제 트러블슈팅 문서를 기준으로 조정한다.

## 평가 데이터 작성

`rag-evaluation-template.csv`를 복사해 최소 30개, 권장 50개 이상의 질문을 작성한다.

- `question`: 사용자가 실제로 입력할 질문
- `expected_document_ids`: 반드시 검색되어야 하는 문서 ID. 여러 개면 `|`로 구분
- `should_find_reference`: 문서 근거가 있어야 하면 `true`, 무관 질문이면 `false`
- `notes`: 정답 판단 근거

평가 데이터에는 다음 유형을 고르게 포함한다.

1. 오류명이나 라이브러리명이 명시된 질문
2. 자연어로 원인이나 증상만 설명한 질문
3. 후속 질문처럼 보이지만 독립적인 질문
4. 저장된 문서로 답할 수 없는 질문
5. 비슷한 문서가 여러 개 존재하는 질문

## 측정 지표

- `Recall@K`: 기대 문서 중 상위 K개 안에 검색된 비율
- `Precision@K`: 상위 K개 중 기대 문서의 비율
- `No-reference accuracy`: 무관 질문에서 결과를 제외한 비율
- `Document diversity`: 최종 컨텍스트에 포함된 고유 문서 수

## 조정 순서

1. `top-k=5`, `similarity-threshold=0.35`를 기준값으로 측정한다.
2. threshold를 `0.25`, `0.35`, `0.45`, `0.55` 순서로 비교한다.
3. top-k를 `3`, `5`, `8`로 비교한다.
4. Recall이 같다면 Precision과 no-reference accuracy가 높은 설정을 선택한다.
5. 선택한 값과 평가 날짜, 임베딩 모델을 이 문서에 기록한다.

임베딩 모델이나 청킹 정책을 변경하면 기존 결과를 그대로 사용하지 말고 전체 평가를 다시 수행한다.

## 평가 이력

| 날짜 | 임베딩 모델 | 청킹 설정 | threshold | top-k | Recall@K | Precision@K | No-reference accuracy |
|---|---|---|---:|---:|---:|---:|---:|
| - | text-embedding-3-small | 400 tokens / 60 overlap | 0.35 | 5 | 미측정 | 미측정 | 미측정 |
