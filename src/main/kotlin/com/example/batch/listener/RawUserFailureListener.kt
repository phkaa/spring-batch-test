package com.example.batch.listener

import com.example.batch.dto.RawUser
import com.example.batch.dto.User
import org.springframework.batch.core.listener.SkipListener
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class RawUserFailureListener(
    private val jdbcTemplate: JdbcTemplate
) : SkipListener<RawUser, User> {

    override fun onSkipInProcess(item: RawUser, t: Throwable) {
        jdbcTemplate.update("""
            UPDATE raw_users
            SET process_status = 'FAILURE',
                error_message = ?,
                processed_at = NOW()
            WHERE id = ?
        """.trimIndent(), t.message, item.id)
    }

    override fun onSkipInWrite(item: User, t: Throwable) {
        jdbcTemplate.update("""
            UPDATE raw_users
            SET process_status = 'FAILURE',
                error_message = ?,
                processed_at = NOW()
            WHERE id = ?
        """.trimIndent(), t.message, item.rawId)
    }
}