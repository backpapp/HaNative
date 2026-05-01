package com.backpapp.hanative.domain.util

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

interface IdGenerator {
    fun generate(): String
}

@OptIn(ExperimentalUuidApi::class)
class UuidIdGenerator : IdGenerator {
    override fun generate(): String = Uuid.random().toString()
}
