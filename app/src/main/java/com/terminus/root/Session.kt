package com.terminus.root

import java.io.Serializable
import java.util.UUID

data class Session(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "会话",
    var shell: String = "/system/bin/sh",
    var useSu: Boolean = false,
    var useShizuku: Boolean = false,
    var cwd: String = "/",
    var chroot: String? = null,
    var extraArgs: String = "",
    var uid: String = "",
    var gid: String = "",
    var templateId: String? = null,
    var detached: Boolean = false,
    var detachedVia: String? = null,
    var lastCwd: String? = null,
    var lastEnv: String? = null,
    var createdAt: Long = System.currentTimeMillis(),
    var lastUsedAt: Long = 0L
) : Serializable
