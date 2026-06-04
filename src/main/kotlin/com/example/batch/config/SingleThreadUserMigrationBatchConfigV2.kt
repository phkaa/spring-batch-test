package com.example.batch.config

import com.example.batch.dto.RawUser
import com.example.batch.dto.User
import com.example.batch.listener.RawUserFailureListener
import com.example.batch.listener.RawUserSuccessListener
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing
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
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import javax.sql.DataSource

@Configuration
@EnableBatchProcessing
class SingleThreadUserMigrationBatchConfigV2 {
    private val PAGE_SIZE: Int = 1000
    private val CHUNK_SIZE: Int = 100
    private val STEP_NAME: String = "single-thread-user-mig-step-v2"
    private val MARK_STEP_NAME: String = "mark-processing-step"

    @Bean
    fun markProcessingStep(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
        dataSource: DataSource
    ): Step {
        val jdbcTemplate = JdbcTemplate(dataSource)

        return StepBuilder(MARK_STEP_NAME, jobRepository)
            .tasklet({ _, _ ->

                val count = jdbcTemplate.update("""
                    UPDATE raw_users
                    SET process_status = 'PROCESSING'
                    WHERE process_status IS NULL
                """.trimIndent())

                println("PROCESSING 변경 건수 : $count")

                RepeatStatus.FINISHED
            }, transactionManager)
            .build()
    }

    @Bean
    fun singleThreadReaderV2(dataSource: DataSource): JdbcPagingItemReader<RawUser> {
        val provider = H2PagingQueryProvider()
        provider.setSelectClause("SELECT id, username, email, password, status")
        provider.setFromClause("FROM raw_users")
        provider.setWhereClause("WHERE process_status = 'PROCESSING'")
        provider.setSortKeys(mapOf("id" to Order.ASCENDING))

        val reader = JdbcPagingItemReader<RawUser>(dataSource, provider)
        reader.pageSize = PAGE_SIZE
        reader.setRowMapper { rs, rowNum ->
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

    @Bean
    fun singleThreadProcessorV2(): ItemProcessor<RawUser, User> {
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

    @Bean
    fun singleThreadStepV2(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
        singleThreadReaderV2: ItemReader<RawUser>,
        singleThreadProcessorV2: ItemProcessor<RawUser, User>,
        singleThreadCompositeWriter: ItemWriter<User>,
        rawUserSuccessListener: RawUserSuccessListener,
        rawUserFailureListener: RawUserFailureListener
    ): Step {
        return StepBuilder(STEP_NAME, jobRepository)
            .chunk<RawUser, User>(CHUNK_SIZE)
            .reader(singleThreadReaderV2)
            .processor(singleThreadProcessorV2)
            .writer(singleThreadCompositeWriter)
            .listener(rawUserSuccessListener)
            .listener(rawUserFailureListener)
            .faultTolerant()
            .skip(Exception::class.java)
            .skipLimit(Long.MAX_VALUE)
            .transactionManager(transactionManager)
            .build()
    }
}