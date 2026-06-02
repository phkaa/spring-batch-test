package com.example.batch.config

import com.example.batch.dto.RawUser
import com.example.batch.dto.User
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
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.transaction.PlatformTransactionManager
import java.io.Writer
import javax.sql.DataSource

@Configuration
@EnableBatchProcessing
class SingleThreadUserMigrationBatchConfig {
    private val PAGE_SIZE: Int = 1000
    private val CHUNK_SIZE: Int = 1000
    private val STEP_NAME: String = "single-thread-user-mig-step"

    @Bean
    fun reader(dataSource: DataSource): JdbcPagingItemReader<RawUser> {
        val provider = H2PagingQueryProvider()
        provider.setSelectClause("SELECT id, username, email, password, status")
        provider.setFromClause("FROM raw_users")
        provider.setWhereClause("WHERE processed = false")
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
    fun processor(): ItemProcessor<RawUser, User> {
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
    fun userWriter(dataSource: DataSource): JdbcBatchItemWriter<User> {
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
    fun rawUpdateWriter(dataSource: DataSource): JdbcBatchItemWriter<User> {
        return JdbcBatchItemWriterBuilder<User>()
            .dataSource(dataSource)
            .sql("""
                UPDATE raw_users
                SET processed = true
                WHERE id = :rawId
            """.trimIndent())
            .beanMapped()
            .build()
    }

    @Bean
    fun compositeWriter(
        userWriter: JdbcBatchItemWriter<User>,
        rawUpdateWriter: JdbcBatchItemWriter<User>
    ): CompositeItemWriter<User> {
        val writer = CompositeItemWriter<User>()
        writer.setDelegates(listOf(userWriter, rawUpdateWriter))
        return writer
    }

    @Bean
    fun step(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
        reader: ItemReader<RawUser>,
        processor: ItemProcessor<RawUser, User>,
        @Qualifier("compositeWriter") writer: ItemWriter<User>
    ): Step {
        return StepBuilder(STEP_NAME, jobRepository)
            .chunk<RawUser, User>(CHUNK_SIZE)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .transactionManager(transactionManager)
            .build()
    }
}