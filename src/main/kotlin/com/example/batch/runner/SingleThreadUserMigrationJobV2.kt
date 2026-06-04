package com.example.batch.runner

import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.job.parameters.RunIdIncrementer
import org.springframework.batch.core.launch.JobOperator
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SingleThreadUserMigrationJobV2(
) {
    private val JOB_NAME: String = "single-thread-user-mig-job-v2"

    @Bean
    fun singleThreadJobV2(
        jobRepository: JobRepository,
        markProcessingStep: Step,
        singleThreadStepV2: Step
    ): Job {
        return JobBuilder(JOB_NAME, jobRepository)
            .incrementer(RunIdIncrementer())
            .start(markProcessingStep)
            .next(singleThreadStepV2)
            .build()
    }
}