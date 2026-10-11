package com.terminus.root

import java.io.Serializable
import java.util.UUID

data class Template(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "模板",
    var guid: String = UUID.randomUUID().toString(),
    var pathCwd: String = "/",
    var chrootView: String? = null,
    var shell: String = "/system/bin/sh",
    var useSu: Boolean = false,
    var useShizuku: Boolean = false,
    var extraArgs: String = ""
) : Serializable
