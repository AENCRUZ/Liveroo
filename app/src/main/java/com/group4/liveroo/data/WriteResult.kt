package com.group4.liveroo.data

import com.group4.liveroo.rules.DenyReason

/** Outcome of a repository write: it was done, or it was refused (nothing was changed) and why. */
sealed interface WriteResult<out T> {
    data class Ok<T>(val value: T) : WriteResult<T>
    data class Denied(val reason: DenyReason) : WriteResult<Nothing>
}

/** The reason a write was refused, or null if it went through. */
fun WriteResult<*>.deniedReason(): DenyReason? = (this as? WriteResult.Denied)?.reason
