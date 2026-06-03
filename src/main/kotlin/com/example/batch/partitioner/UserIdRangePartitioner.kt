package com.example.batch.partitioner

import org.springframework.batch.core.partition.Partitioner
import org.springframework.batch.core.scope.context.StepSynchronizationManager
import org.springframework.batch.infrastructure.item.ExecutionContext

class UserIdRangePartitioner(): Partitioner {
    override fun partition(gridSize: Int): MutableMap<String, ExecutionContext> {

        val context = StepSynchronizationManager.getContext()
            ?: throw IllegalStateException("StepContext is null")

        val jobExecution = context.stepExecution.jobExecution

        val minId = jobExecution.jobParameters.getLong("minId") ?: 1L
        val maxId = jobExecution.executionContext.getLong("maxId")

        val result = mutableMapOf<String, ExecutionContext>()

        val targetSize = ((maxId - minId + 1) / gridSize).coerceAtLeast(1L)

        var start = minId
        var end = start + targetSize - 1

        for (i in 0 until gridSize) {

            if (i == gridSize - 1) {
                end = maxId
            }

            val ec = ExecutionContext()
            ec.putLong("minValue", start)
            ec.putLong("maxValue", end)

            result["partition-$i"] = ec

            start = end + 1
            end = start + targetSize - 1
        }

        return result
    }
}