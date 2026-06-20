# Spring Batch Test Project

이 프로젝트는 Spring Batch의 처리 방식과 성능 개선 방법을 검증하기 위한 테스트 프로젝트입니다.

실무에서 단일 스레드 기반 배치를 운영하던 중 데이터 처리량 증가로 인해 배치 실행 시간이 길어지는 문제가 발생했습니다.

해결 방법으로 동일한 배치를 여러 개 생성하여 Kubernetes CronJob에 여러 개 등록하는 방식으로 병렬 처리를 구성할 수 있지만,
이는 배치 관리 포인트 증가와 운영 복잡도를 높이는 구조라고 판단했습니다.

이에 하나의 배치 애플리케이션에서 Spring Batch가 제공하는 Partitioning 기반 병렬 처리 방식을 적용하여,
단일 배치를 유지하면서도 처리 성능을 개선할 수 있는 구조를 검증하고 비교하기 위해 작성한 프로젝트입니다.

구현 내용:

- Single Thread 기반 배치 처리
- Chunk 기반 Batch Insert 처리
- 상태 변경을 포함한 데이터 Migration 배치
- Partitioning 기반 Multi Thread 배치 처리
- 처리 방식별 성능 비교 및 분석
---

# Tech Stack

- Spring Boot
- Spring Batch
- Kotlin
- H2 Database (In-Memory)
- JDBC

---

# Project Purpose

- Spring Batch 내부 동작 구조 이해
- Single-thread vs Parallel processing 비교
- Partition 기반 병렬 처리 구조 학습
- Chunk 기반 트랜잭션 처리 이해
- Skip / Fault tolerance 테스트
- JobExecutionContext 활용 방법 학습

---

# Batch Processing Types

## 1. Single Thread Batch

단일 스레드 기반으로 순차적으로 데이터를 처리하는 배치입니다.

### 특징
- Chunk 기반 처리
- process_status 기반 상태 관리
- SUCCESS / FAILURE 처리 구조
- 단일 실행 흐름

### Flow
Reader → Processor → Writer

---
## 2. Early Status Change & Single Thread Batch

배치 실행 전 처리 대상 데이터를 한 번에 `PROCESSING` 상태로 변경한 후, 단일 스레드 기반으로 순차 처리하는 배치입니다.

### 특징
- 배치 시작 시 미처리 데이터를 `PROCESSING` 상태로 선점
- 단일 스레드 기반 순차 처리
- Chunk 단위 트랜잭션 처리
- `process_status` 기반 처리 상태 관리
- 처리 성공 시 `SUCCESS` 상태로 변경
- 처리 실패 시 `FAILURE` 상태 및 오류 메시지 기록
- Reader, Processor, Writer 역할 분리

### Flow
MarkProcessingStep → Reader → Processor → Writer

---

## 3. Partitioned Batch

ID 범위를 기준으로 데이터를 분할하여 여러 스레드에서 병렬 처리하는 방식입니다.

### 특징
- max(id) 기반 partition 생성
- gridSize 기반 데이터 분할
- TaskExecutor를 통한 병렬 실행
- Worker Step chunk 처리

### Flow
Master Step → Partitioner → Worker Steps → Reader → Processor → Writer

---

# 배치 테스트 결과

| 데이터량 | Partition Batch | Early Status Change Single Thread | Single Thread |
| ---- |----------------:|---------------------:|--------------:|
| 1만   |           0.27s |                0.36s |         0.48s |
| 5만   |           1.33s |                1.80s |         2.38s |
| 10만  |           2.65s |                3.61s |         4.76s |
| 50만  |          11.26s |               14.05s |        19.81s |
| 100만 |          21.52s |               32.09s |        43.61s |

---

# Database Console (H2)

URL: http://localhost:8080/h2-console  
JDBC URL: jdbc:h2:mem:testdb

---

# How to Run

## Single Thread Batch
--spring.batch.job.name=single-thread-user-mig-job

## Early Status Change & Single Thread Batch
--spring.batch.job.name=single-thread-user-mig-job-v2

## Partition Batch
--spring.batch.job.name=partition-user-mig-job

---

# Key Features

- Spring Batch Chunk 기반 처리 구조 구현
- Single Thread vs Partition 병렬 처리 비교
- JobExecutionContext 기반 데이터 전달
- process_status 기반 재처리 제어
- skip 기반 fault tolerance 처리
- TaskExecutor 기반 멀티스레드 처리

---

# Learning Goals

- Spring Batch Execution Flow 이해
- Chunk size vs Page size 차이 이해
- Partition vs Single-thread 성능 차이 이해
- Transaction boundary 이해
- Retry / Skip 전략 이해
