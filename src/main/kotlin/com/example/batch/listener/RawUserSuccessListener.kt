package com.example.batch.listener

import com.example.batch.dto.User
import org.springframework.batch.core.listener.ItemWriteListener
import org.springframework.batch.infrastructure.item.Chunk
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class RawUserSuccessListener(
    private val jdbcTemplate: JdbcTemplate
): ItemWriteListener<User> {
    override fun afterWrite(items: Chunk<out User>) {
        items.forEach { user ->
            jdbcTemplate.update("""
                UPDATE raw_users
                SET process_status = 'SUCCESS',
                    processed_at = NOW()
                WHERE id = ?
            """.trimIndent(), user.rawId)
        }
    }
}