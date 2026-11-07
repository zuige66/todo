package com.lirui.oneline.update

/**
 * 语义化版本号比较。
 *
 * 为什么单独抽出来：检查更新里最容易出错、又最值得单测的就是它。
 * 放在 Compose / Android 之外，[VersionTest] 可以直接跑，不需要 Robolectric。
 *
 * 规则：
 * - 允许前缀 `v` / `V`，允许 `1.2.3-beta.1`、`1.2.3+20260101` 这类后缀（后缀**不参与**比较，
 *   `1.2.3-beta` 与 `1.2.3` 视为同版本 —— 这是发版 tag 的实际形态决定的）；
 * - 段数不同按 0 补齐：`1.2` == `1.2.0`；
 * - 任何一段不是纯数字、或整体不匹配 → 返回 null，让调用方给出"版本号格式异常"而不是瞎判。
 */
object Version {

    private val PATTERN = Regex("""^[vV]?\s*(\d+(?:\.\d+)*)\s*(?:[-+_].*)?$""")

    /**
     * 解析成数字段列表。
     * @return null = 无法解析（空串、"latest"、纯后缀等）
     */
    fun parse(raw: String): List<Int>? {
        val match = PATTERN.matchEntire(raw.trim()) ?: return null
        val parts = match.groupValues[1].split('.')
        val numbers = ArrayList<Int>(parts.size)
        for (part in parts) {
            // 段内出现非数字（正则已挡住）或超出 Int 范围时视为解析失败
            numbers += part.toIntOrNull() ?: return null
        }
        return numbers
    }

    /**
     * 比较两个版本号。
     * @return 正数 = [a] 更新；负数 = [b] 更新；0 = 相同；null = 任一无法解析
     */
    fun compare(a: String, b: String): Int? {
        val left = parse(a) ?: return null
        val right = parse(b) ?: return null
        val size = maxOf(left.size, right.size)
        for (index in 0 until size) {
            val l = left.getOrElse(index) { 0 }
            val r = right.getOrElse(index) { 0 }
            if (l != r) return l.compareTo(r)
        }
        return 0
    }

    /**
     * [remote] 是否比 [current] 新。
     * @return null = 无法比较（不是"否"，调用方必须区分）
     */
    fun isNewer(remote: String, current: String): Boolean? =
        compare(remote, current)?.let { it > 0 }
}
