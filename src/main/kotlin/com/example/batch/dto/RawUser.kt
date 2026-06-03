package com.example.batch.dto

data class RawUser(
    val id: Long,
    val username: String,
    val email: String,
    val password: String,
    val status: String,
)
