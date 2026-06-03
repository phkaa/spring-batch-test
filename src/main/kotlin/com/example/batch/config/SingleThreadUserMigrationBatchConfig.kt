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
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import javax.sql.DataSource

@Configuration
@EnableBatchProcessing
class SingleThreadUserMigrationBatchConfig {
    private val PAGE_SIZE: Int = 1000
    private val CHUNK_SIZE: Int = 100
    private val STEP_NAME: String = "single-thread-user-mig-step"

    @Bean
    fun singleThreadReader(dataSource: DataSource): JdbcPagingItemReader<RawUser> {
        val provider = H2PagingQueryProvider()
        provider.setSelectClause("SELECT id, username, email, password, status")
        provider.setFromClause("FROM raw_users")
        provider.setWhereClause("WHERE process_status IS NULL")
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
    fun singleThreadProcessor(dataSource: DataSource): ItemProcessor<RawUser, User> {
        val jdbc = JdbcTemplate(dataSource)

        return ItemProcessor { raw ->
            jdbc.update("""
                UPDATE raw_users
                SET process_status = 'PROCESSING'
                WHERE id = ?
            """.trimIndent(), raw.id)

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
    fun singleThreadUserWriter(dataSource: DataSource): JdbcBatchItemWriter<User> {
        return JdbcBatchItemWriterBuilder<User>()
            .dataSource(dataSource)
            .sql("""
                INSERT INTO users (username, email, password, status)
                VALUES (:username, :email, :password, :status)
            """.trimIndent())
            .beanMapped()
            .build()
    }

    @Bean
    fun singleThreadCompositeWriter(
        singleThreadUserWriter: JdbcBatchItemWriter<User>,
    ): CompositeItemWriter<User> {
        val writer = CompositeItemWriter<User>()
        writer.setDelegates(listOf(singleThreadUserWriter))
        return writer
    }

    @Bean
    fun step(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
        singleThreadReader: ItemReader<RawUser>,
        singleThreadProcessor: ItemProcessor<RawUser, User>,
        singleThreadCompositeWriter: ItemWriter<User>,
        rawUserSuccessListener: RawUserSuccessListener,
        rawUserFailureListener: RawUserFailureListener
    ): Step {
        return StepBuilder(STEP_NAME, jobRepository)
            .chunk<RawUser, User>(CHUNK_SIZE)
            .reader(singleThreadReader)
            .processor(singleThreadProcessor)
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