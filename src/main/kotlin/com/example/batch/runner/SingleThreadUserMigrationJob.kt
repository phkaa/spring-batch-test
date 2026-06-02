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
class SingleThreadUserMigrationJob(
) {
    private val JOB_NAME: String = "single-thread-user-mig-job"

    @Bean
    fun userMigrationJob(
        jobRepository: JobRepository,
        step: Step
    ): Job {
        return JobBuilder(JOB_NAME, jobRepository)
            .incrementer(RunIdIncrementer())
            .start(step)
            .build()
    }
}