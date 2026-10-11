package com.terminus.root

import java.io.Serializable
import java.util.UUID

data class Session(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "会话",
    var shell: String = "/bin/sh",
    var useSu: Boolean = false,
    var useShizuku: Boolean = false,
    var cwd: String = "/",
    var chroot: String? = null,
    var extraArgs: String = "",
    var guid: String = UUID.randomUUID().toString(),
    var templateId: String? = null
) : Serializable
