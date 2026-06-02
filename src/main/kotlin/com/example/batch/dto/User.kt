package com.example.batch.dto

data class User(
    val rawId: Long,
    val username: String,
    val email: String,
    val password: String,
    val status: String
)
