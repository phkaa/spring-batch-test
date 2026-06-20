package com.example.batch.config

import com.example.batch.dto.RawUser
import com.example.batch.dto.User
import com.example.batch.listener.RawUserFailureListener
import com.example.batch.listener.RawUserSuccessListener
import com.example.batch.partitioner.UserIdRangePartitioner
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.infrastructure.item.ItemProcessor
import org.springframework.batch.infrastructure.item.ItemReader
import org.springframework.batch.infrastructure.item.ItemWriter
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter
import org.springframework.batch.infrastructure.item.database.JdbcPagingItemReader
import org.springframework.batch.infrastructure.item.database.Order
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder
import org.springframework.batch.infrastructure.item.database.support.H2PagingQueryProvider
import org.springframework.batch.infrastructure.item.support.CompositeItemWriter
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import javax.sql.DataSource

@Configuration
@EnableBatchProcessing
class PartitionUserMigrationBatchConfig {
    private val PAGE_SIZE: Int = 1000
    private val CHUNK_SIZE: Int = 100
    private val MAX_ID_STEP_NAME = "max-id-step"
    private val MASTER_STEP_NAME = "master-step";
    private val WORKER_STEP_NAME = "worker-step";
    private val THREAD_NAME_PREFIX = "partition-";

    // =========================
    // MAX ID STEP
    // =========================
    @Bean
    fun maxIdStep(
        jobRepository: JobRepository,
        dataSource: DataSource
    ): Step {

        return StepBuilder(MAX_ID_STEP_NAME, jobRepository)
            .tasklet { _, chunkContext ->

                val jdbc = JdbcTemplate(dataSource)

                val maxId = jdbc.queryForObject(
                    "SELECT COALESCE(MAX(id), 0) FROM raw_users WHERE process_status IS NULL",
                    Long::class.java
                ) ?: 0L

                chunkContext.stepContext
                    .stepExecution
                    .jobExecution
                    .executionContext
                    .putLong("maxId", maxId)

                RepeatStatus.FINISHED
            }
            .build()
    }

    // =========================
    // READER
    // =========================
    @Bean
    @StepScope
    fun partitionReader(
        dataSource: DataSource,
        @Value("#{stepExecutionContext[minValue]}") minValue: Long,
        @Value("#{stepExecutionContext[maxValue]}") maxValue: Long
    ): JdbcPagingItemReader<RawUser> {

        val provider = H2PagingQueryProvider()
        provider.setSelectClause("SELECT id, username, email, password, status")
        provider.setFromClause("FROM raw_users")
        provider.setWhereClause(
            "WHERE process_status IS NULL AND id BETWEEN $minValue AND $maxValue"
        )
        provider.setSortKeys(mapOf("id" to Order.ASCENDING))

        val reader = JdbcPagingItemReader<RawUser>(dataSource, provider)
        reader.pageSize = PAGE_SIZE

        reader.setRowMapper { rs, _ ->
            RawUser(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("email"),
                rs.getString("password"),
                rs.getString("status")
            )
        }

        return reader
    }

    // =========================
    // PROCESSOR
    // =========================
    @Bean
    fun partitionProcessor(): ItemProcessor<RawUser, User> {
        return ItemProcessor { raw ->
            User(
                rawId = raw.id,
                username = raw.username,
                email = raw.email,
                password = raw.password,
                status = raw.status
            )
        }
    }

    // =========================
    // WRITER
    // =========================
    @Bean
    fun partitionUserWriter(dataSource: DataSource): JdbcBatchItemWriter<User> {
        return JdbcBatchItemWriterBuilder<User>()
            .dataSource(dataSource)
            .sql("""
                INSERT INTO users (username, email, password, status)
                VALUES (:username, :email, :password, :status)
            """.trimIndent())
            .beanMapped()
            .build()
    }

    // =========================
    // RAW STATUS WRITER
    // =========================
    @Bean
    fun partitionRawUserStatusWriter(
        dataSource: DataSource
    ): JdbcBatchItemWriter<User> {

        return JdbcBatchItemWriterBuilder<User>()
            .dataSource(dataSource)
            .sql("""
                UPDATE raw_users
                SET process_status = 'PROCESSING'
                WHERE id = :rawId
            """)
            .beanMapped()
            .build()
    }

    @Bean
    fun partitionCompositeWriter(
        partitionRawUserStatusWriter: JdbcBatchItemWriter<User>,
        partitionUserWriter: JdbcBatchItemWriter<User>
    ): CompositeItemWriter<User> {

        return CompositeItemWriter<User>().apply {
            setDelegates(listOf(partitionRawUserStatusWriter, partitionUserWriter))
        }
    }

    // =========================
    // WORKER STEP
    // =========================
    @Bean
    fun workerStep(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
        partitionReader: ItemReader<RawUser>,
        partitionProcessor: ItemProcessor<RawUser, User>,
        partitionCompositeWriter: ItemWriter<User>,
        rawUserSuccessListener: RawUserSuccessListener,
        rawUserFailureListener: RawUserFailureListener
    ): Step {

        return StepBuilder(WORKER_STEP_NAME, jobRepository)
            .chunk<RawUser, User>(CHUNK_SIZE)
            .reader(partitionReader)
            .processor(partitionProcessor)
            .writer(partitionCompositeWriter)
            .listener(rawUserSuccessListener)
            .listener(rawUserFailureListener)
            .faultTolerant()
            .skip(Exception::class.java)
            .skipLimit(Long.MAX_VALUE)
            .transactionManager(transactionManager)
            .build()
    }

    // =========================
    // MASTER STEP (PARTITION)
    // =========================
    @Bean
    fun masterStep(
        jobRepository: JobRepository,
        workerStep: Step,
        userIdRangePartitioner: UserIdRangePartitioner
    ): Step {

        return StepBuilder(MASTER_STEP_NAME, jobRepository)
            .partitioner(WORKER_STEP_NAME, userIdRangePartitioner)
            .step(workerStep)
            .gridSize(10)
            .taskExecutor(taskExecutor())
            .build()
    }

    // =========================
    // TASK EXECUTOR
    // =========================
    @Bean
    fun taskExecutor(): TaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 5
        executor.maxPoolSize = 10
        executor.setThreadNamePrefix(THREAD_NAME_PREFIX)
        executor.initialize()
        return executor
    }
}