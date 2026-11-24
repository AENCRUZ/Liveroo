package com.group4.liveroo.testsupport

import com.group4.liveroo.data.WriteResult
import com.group4.liveroo.rules.DenyReason

/** The id of a task that was saved; fails the test if the write was refused. */
fun WriteResult<String>.id(): String = when (this) {
    is WriteResult.Ok -> value
    is WriteResult.Denied -> error("expected the write to succeed but it was refused: $reason")
}

/** Fails the test unless the write went through. */
fun WriteResult<*>.assertOk() {
    if (this is WriteResult.Denied) error("expected the write to succeed but it was refused: $reason")
}

fun WriteResult<*>.denial(): DenyReason? = (this as? WriteResult.Denied)?.reason
