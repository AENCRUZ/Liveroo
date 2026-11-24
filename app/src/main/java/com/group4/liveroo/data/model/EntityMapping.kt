package com.group4.liveroo.data.model

import com.group4.liveroo.data.local.GroupEntity
import com.group4.liveroo.data.local.TaskEntity

/** The group's leadership chain, oldest first. Index = epoch. */
fun GroupEntity.chain(): List<LeaderEntry> = ModelJson.decodeChain(leaderChain)

/** Current leadership epoch (number of validated transfers so far). */
fun GroupEntity.epoch(): Int = chain().size - 1

fun TaskEntity.regs(): TaskRegisters = ModelJson.decodeRegisters(registers)
