package com.lirui.oneline.util

import android.content.Context

/**
 * 测试通知「详情」里那句文案。
 *
 * 为什么是随机一条：这条通知的作用是**让你在通知栏里看到真实排版**，
 * 每次点「发送测试通知」都换一句，才能顺带看出不同长度会不会被截断。
 *
 * 收录标准两条：
 * 1. **够短** —— 一行 / 最多两行能读完。长句一律不进这个池子：通知的 BigText 展开区在
 *    国产 ROM 上经常不给你点开的机会（小米/OPPO 折叠状态只显示 contentText），放长等于白放。
 * 2. **离开上下文能读懂** —— 所以「阳莽憾陨谁无败？卷土重来再称王」只收了后半句。
 *
 * v0.5 起这个池子是**可编辑的**（设置 → 提醒文案）：先读 SharedPreferences，
 * 没自定义过才回落到 [defaults]。所以任何取文案的地方都必须带 Context，
 * 不要再写死访问 [defaults] —— 那样用户改了半天不生效。
 *
 * 完整分组与长句见仓库根目录 `QUOTES.md`。
 */
object Quotes {

    /** 没有可用文案时的兜底正文。空池子不该让通知正文变成空白。 */
    const val FALLBACK = "该办了。"

    /** 内置默认池（通知详情用的短句）。用户在设置里改过就不再读它。 */
    val defaults: List<String> = listOf(
        "岂不闻天无绝人之路？只要我想走，路就在脚下。",
        "不过些许风霜罢了。",
        "男儿不展凌云志，空负天生八尺躯。",
        "今日暂且展翼去，明朝登仙笞凤凰。",
        "卷土重来再称王。",
        "千磨万击心铸铁，殚精竭虑铸一剑。",
        "早岁已知世事艰，仍许飞鸿荡云间。",
        "落魄谷中寒风吹，春秋蝉鸣少年归。",
        // 「坚持」这一组刻意分成三条：合成一段太长，通知里读不完。
        "我曾经呐喊过，渐渐地，我不发出声音……",
        "而如今！我只剩下面无表情，我的目光如磐石般坚硬，我的心中剩下坚持。",
        "这就是我，一个小人物的坚持。",
        "坚持蛊没有凡蛊，因为坚持本就不凡。",
        "希望蛊没有仙蛊，因为凡事都有希望。",
        "面对困境，就把心交给希望。",
        "有心的人，态度就是一张面具；无心的人，戴不上态度面具。",
        "自己才是世间最可靠的。",
        "最大的失败就是失去自我。",
        "人啊，即便弱小如我，也有生存的方法。",
        "规矩要在黑暗中摸索。"
    )

    /** 当前生效的文案池：用户自定义优先，否则 [defaults]。 */
    fun pool(context: Context): List<String> =
        SettingsRepo.customQuotes(context) ?: defaults

    /** 随机取一条（测试通知用）。 */
    fun random(context: Context): String =
        pool(context).ifEmpty { listOf(FALLBACK) }.random()

    /**
     * 按待办 id 取一条 —— **同一条待办永远是同一句**。
     *
     * 提醒通知的「完成 / 撤回」是同一个 notification id 的原地重发，
     * 用随机的话每点一次按钮详情就换一条文案，看起来像出了 bug。
     * 这里用 id 取模，天然稳定，也不用把文案塞进 PendingIntent 的 extra。
     *
     * 用户中途改了文案池，取模的基数会变 → 同一条待办的句子会换一次。
     * 这个取舍是接受的：改了文案还坚持用旧的，才是更奇怪的行为。
     */
    fun forTodo(context: Context, todoId: Long): String {
        val quotes = pool(context)
        if (quotes.isEmpty()) return FALLBACK
        val size = quotes.size
        val index = ((todoId % size) + size) % size
        return quotes[index.toInt()]
    }
}
