# Spring Batch Test Project

이 프로젝트는 Spring Batch의 다양한 실행 전략을 학습하고 테스트하기 위한 예제 프로젝트입니다.

싱글 스레드 처리와 파티셔닝 기반 병렬 처리를 구현하여 성능 및 구조 차이를 비교할 수 있도록 구성되어 있습니다.

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

## 2. Partitioned Batch

ID 범위를 기준으로 데이터를 분할하여 여러 스레드에서 병렬 처리하는 방식입니다.

### 특징
- max(id) 기반 partition 생성
- gridSize 기반 데이터 분할
- TaskExecutor를 통한 병렬 실행
- Worker Step chunk 처리

### Flow
Master Step → Partitioner → Worker Steps → Reader → Processor → Writer

---

# Database Console (H2)

URL: http://localhost:8080/h2-console  
JDBC URL: jdbc:h2:mem:testdb

---

# How to Run

## Single Thread Batch
--spring.batch.job.name=single-thread-user-mig-job

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
