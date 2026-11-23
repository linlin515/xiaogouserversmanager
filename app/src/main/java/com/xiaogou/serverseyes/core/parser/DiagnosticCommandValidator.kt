package com.xiaogou.serverseyes.core.parser

object DiagnosticCommandValidator {

    private val ALLOWED_BASE_COMMANDS = setOf(
        "uname", "nproc", "hostname", "uptime", "whoami", "id", "date", "timedatectl",
        "free", "vmstat", "mpstat", "iostat", "sar", "top",
        "df", "du", "lsblk", "blkid", "mount", "findmnt",
        "ps", "pgrep", "pidof", "lsof",
        "ss", "netstat", "ip", "ifconfig", "ping", "traceroute", "tracepath", "dig", "nslookup", "host", "arp", "ethtool",
        "cat", "head", "tail", "grep", "egrep", "fgrep", "wc", "sort", "uniq", "cut", "awk", "sed",
        "ls", "stat", "file", "readlink", "env", "printenv", "dmesg", "journalctl",
        "systemctl", "docker", "nvidia-smi", "sensors", "chronyc", "ntpq", "echo"
    )

    private val ALLOWED_SYSTEMCTL_SUBCOMMANDS = setOf(
        "status", "is-active", "is-enabled", "is-failed", "list-units", "list-unit-files", "list-timers", "show"
    )

    private val ALLOWED_DOCKER_SUBCOMMANDS = setOf(
        "ps", "stats", "logs", "inspect", "images", "info", "version", "top", "port", "df", "network", "volume"
    )

    private val SENSITIVE_PATH_TOKENS = listOf("/etc/shadow", "/etc/gshadow")

    fun validate(rawCommand: String): Result<String> = runCatching {
        val cmd = rawCommand.trim()
        require(cmd.isNotEmpty()) { "请输入只读诊断命令" }
        require(cmd.length <= 512) { "诊断命令过长（最多 512 字符）" }
        require(!cmd.contains('\n') && !cmd.contains('\r')) { "不允许包含多行脚本或换行符" }

        checkDangerousSyntax(cmd)

        val segments = splitCommandSegments(cmd)
        require(segments.isNotEmpty()) { "无效的诊断命令" }
        segments.forEach { validateSegment(it) }
        cmd
    }

    private fun checkDangerousSyntax(cmd: String) {
        var inSingle = false
        var inDouble = false
        var i = 0
        while (i < cmd.length) {
            val ch = cmd[i]
            if (ch == '\\' && !inSingle && i + 1 < cmd.length) {
                i += 2
                continue
            }
            if (ch == '\'' && !inDouble) {
                inSingle = !inSingle
                i++
                continue
            }
            if (ch == '"' && !inSingle) {
                inDouble = !inDouble
                i++
                continue
            }

            if (!inSingle) {
                if (ch == '`') {
                    throw IllegalArgumentException("出于安全限制，不允许使用反引号命令替换")
                }
                if (ch == '$' && i + 1 < cmd.length && (cmd[i + 1] == '(' || cmd[i + 1] == '{')) {
                    throw IllegalArgumentException("出于安全限制，不允许使用 \$(...) 或变量替换")
                }
            }

            if (!inSingle && !inDouble) {
                if ((ch == '<' || ch == '>') && i + 1 < cmd.length && cmd[i + 1] == '(') {
                    throw IllegalArgumentException("不允许使用进程替换语法")
                }
                if (ch == '>') {
                    val isSafeDevNull = cmd.startsWith("2>/dev/null", startIndex = i - 1) && i > 0 && cmd[i - 1] == '2'
                    val isSafeStderrStdout = cmd.startsWith("2>&1", startIndex = i - 1) && i > 0 && cmd[i - 1] == '2'
                    if (!isSafeDevNull && !isSafeStderrStdout) {
                        throw IllegalArgumentException("仅允许只读诊断命令，禁止文件输出重定向 (> )")
                    }
                }
                if (ch == '&') {
                    val prev = cmd.getOrNull(i - 1)
                    val next = cmd.getOrNull(i + 1)
                    val isLogicalAnd = next == '&' || prev == '&'
                    val isFdDup = prev == '>'
                    if (!isLogicalAnd && !isFdDup) {
                        throw IllegalArgumentException("禁止使用后台执行符 (&)")
                    }
                }
            }
            i++
        }
        require(!inSingle && !inDouble) { "命令引号未闭合" }
    }

    private fun splitCommandSegments(cmd: String): List<String> {
        val segments = mutableListOf<String>()
        val sb = StringBuilder()
        var inSingle = false
        var inDouble = false
        var i = 0
        while (i < cmd.length) {
            val ch = cmd[i]
            if (ch == '\\' && !inSingle && i + 1 < cmd.length) {
                sb.append(ch).append(cmd[i + 1])
                i += 2
                continue
            }
            if (ch == '\'' && !inDouble) {
                inSingle = !inSingle
                sb.append(ch)
                i++
                continue
            }
            if (ch == '"' && !inSingle) {
                inDouble = !inDouble
                sb.append(ch)
                i++
                continue
            }
            if (!inSingle && !inDouble) {
                if (cmd.startsWith("&&", i) || cmd.startsWith("||", i)) {
                    segments += sb.toString().trim()
                    sb.setLength(0)
                    i += 2
                    continue
                }
                if (ch == '|' || ch == ';') {
                    segments += sb.toString().trim()
                    sb.setLength(0)
                    i++
                    continue
                }
            }
            sb.append(ch)
            i++
        }
        val tail = sb.toString().trim()
        if (tail.isNotEmpty()) segments += tail
        return segments
    }

    private fun validateSegment(segment: String) {
        val cleaned = segment
            .replace("2>/dev/null", "")
            .replace("2>&1", "")
            .trim()
        require(cleaned.isNotEmpty()) { "存在空命令片段" }

        val tokens = tokenizeWhitespace(cleaned)
        require(tokens.isNotEmpty()) { "存在空命令片段" }

        val rawBin = tokens[0]
        if (rawBin.contains('/')) {
            throw IllegalArgumentException("不允许通过绝对或相对路径执行命令：$rawBin")
        }
        val bin = rawBin.lowercase()
        if (bin !in ALLOWED_BASE_COMMANDS) {
            throw IllegalArgumentException("命令「$rawBin」不在只读诊断白名单内")
        }

        val args = tokens.drop(1)
        args.forEach { arg ->
            val lower = arg.lowercase()
            if (SENSITIVE_PATH_TOKENS.any { lower.contains(it) }) {
                throw IllegalArgumentException("禁止访问敏感凭据文件：$arg")
            }
        }

        when (bin) {
            "sed" -> {
                if (args.any { it == "-i" || it.startsWith("-i") || it.startsWith("--in-place") }) {
                    throw IllegalArgumentException("禁止使用 sed 原地修改参数 (-i)")
                }
            }
            "systemctl" -> {
                val sub = args.firstOrNull { !it.startsWith("-") }?.lowercase()
                if (sub == null || sub !in ALLOWED_SYSTEMCTL_SUBCOMMANDS) {
                    throw IllegalArgumentException("systemctl 仅允许只读子命令（如 status / list-units）")
                }
            }
            "docker" -> {
                val sub = args.firstOrNull { !it.startsWith("-") }?.lowercase()
                if (sub == null || sub !in ALLOWED_DOCKER_SUBCOMMANDS) {
                    throw IllegalArgumentException("docker 仅允许只读子命令（如 ps / logs / inspect / stats）")
                }
            }
            "find" -> {
                throw IllegalArgumentException("命令「find」不在只读诊断白名单内")
            }
        }
    }

    private fun tokenizeWhitespace(input: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inSingle = false
        var inDouble = false
        var i = 0
        while (i < input.length) {
            val ch = input[i]
            if (ch == '\'' && !inDouble) {
                inSingle = !inSingle
                i++
                continue
            }
            if (ch == '"' && !inSingle) {
                inDouble = !inDouble
                i++
                continue
            }
            if (!inSingle && !inDouble && ch.isWhitespace()) {
                if (sb.isNotEmpty()) {
                    tokens += sb.toString()
                    sb.setLength(0)
                }
            } else {
                sb.append(ch)
            }
            i++
        }
        if (sb.isNotEmpty()) tokens += sb.toString()
        return tokens
    }
}
