import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.lirui.oneline"
    compileSdk = 36

    // Load signing config from keystore.properties (kept out of VCS)
    val keystoreProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    val storeFileVal = keystoreProps["storeFile"] as? String
    val storePasswordVal = keystoreProps["storePassword"] as? String
    val keyAliasVal = keystoreProps["keyAlias"] as? String
    val keyPasswordVal = keystoreProps["keyPassword"] as? String

    signingConfigs {
        create("release") {
            if (storeFileVal != null) {
                storeFile = rootProject.file(storeFileVal)
                storePassword = storePasswordVal
                keyAlias = keyAliasVal
                keyPassword = keyPasswordVal
            }
        }
    }

    defaultConfig {
        applicationId = "com.lirui.oneline"
        minSdk = 26
        targetSdk = 36
        // 0.4 = 四导航项（关于独立）+ 右滑归档 + 通知标题合并为模板 + 检查更新/下载安装
        //       + 统一卡片色体系（Tokens）+ 归档状态机修复（快速滑动不再丢归档）。
        // 0.4.1 = 板块拖拽去掉 spring 过冲 + 边界 clamp、提醒文案可编辑、沉浸式顶栏（带开关）。
        // 0.4.2 = 拖拽第三轮（临界阻尼 + 底色过渡）、沉浸式顶栏重写（固定 padding + 不消费滚动）、
        //         文案框滚动隔离、删关于页底部说明。
        // 0.5.0 = 提醒可靠性：升级/改时间重建（修「升级后提醒全丢」）、12h 兜底巡检、
        //         设置页四条自检（通知/精确闹钟/电池白名单/自启动）+ 可选后台常驻服务。
        // 0.5.1 = 修沉浸式顶栏（滑满整条才离场，标题不再压在状态栏上；关于页不再遮住
        //         scroll state 导致顶栏收不起来）、电池白名单与厂商「后台高耗电」文案分开、
        //         自启动改为可手动确认。
        // 0.5.2 = 顶栏底色不再跟着标题一起淡出（不再"上面一片透明"）、电池优化跳转在已加入
        //         白名单时也能打开页面（原来直接 return，点了没反应）、可靠性行只保留「前往」。
        // 0.5.3 = 去掉标题单独淡出：标题和底色必须同时离场，否则会出现
        //         "文字没了、下面还留一条空底色"（用户报的"导航栏还在，只是文字消失"）。
        // 0.5.4 = 修顶栏不动的真因：graphicsLayer 写在 background 之后，层只包住内部内容，
        //         底色画在层外 → translationY 永远动不了底色。调整修饰符顺序（层→底色→裁剪）。
        // 0.6.0 = 设置页重排：提醒可靠性(含后台常驻)置顶并带状态徽章、逾期/默认时刻并入「提醒」、
        //         通知标题+文案合并「通知样式」、自动顺延进「列表与待办」；关于页隐藏浮层顶栏（hero 即页头）；
        //         「沉浸式顶栏」开关退役。
        // 0.6.1 = 修后台常驻关不掉（setEnabled 关闭分支误返 true）；归档撤销条不再被浮层顶栏挡住
        //         （移到顶栏下方并跟随其位移）；关于页排序改 开发者→应用→项目（两字标题）。
        // 0.6.2 = 添加弹层预览卡片收紧（14/12/8，与输入框同宽同重）；「通知样式」改名「通知」，
        //         提醒文案真正并入同卡，新增「常驻通知文案」编辑（保存即原地重建常驻通知）。
        // 每次发版：versionCode 必须单调递增（Android 用它判断升级），versionName 给人看。
        // 0.7.0 = 新功能：预览卡片可直接选日期/时间（M3 DatePicker/TimePicker），覆盖句子解析值；
        //         多日期待办各自独立覆盖；时间可改不可清，日期可改「无日期」；
        //         提交链路改为 UI 侧合并成 List<ParsedTodo>（vm.addParsed / editParsed）。
        // 0.7.1 = 标题（事件）与详情也可编辑：点预览行弹对话框，同属手动覆盖；
        //         标题清空时禁用提交（VM 也会判 Blank）。
        // 0.7.2 = 预览 chip 样式统一（去掉空心描边变体，一律实底）；去掉标题/详情行的编辑铅笔图标。
        // 0.7.3 = 设置页文案精简（全部说明压到一行）。⚠️ 这一版漏改版本号，包发出去还是 0.7.2，
        //         从 0.8.0 起修正 —— 发版必须版本号与代码同批提交。
        // 0.8.0 = 桌面小组件：一个 provider、纵横可拉；2×2 紧凑行 4 条 / 拉高标准行 6 条；
        //         行首圆圈点击直接完成（广播 → Repository.archive → 刷新）；
        //         系统 30 分钟兜底 + App 内 6 处写操作即时刷新。
        // 0.8.1 = 澎湃3/vivo 组件列表搜不到组件（Launcher 缓存 + previewImage 支持差）→
        //         补 previewLayout（API 31+ 静态布局，Launcher 直接 inflate）+
        //         设置→外观→「添加小组件到桌面」（requestPinAppWidget 一键钉桌面，不走列表）。
        // 0.8.2 = 小组件重做：不再按尺寸分档（vivo 报的尺寸恒为 152px，2×2/2×4 一样），
        //         日期显示交给四个开关（显示日期 / 日期位置 / 只显示日期 / 隐藏头部）；
        //         圆圈跟随主题色、点击直接归档（两帧反馈：勾亮起 → 行消失）；
        //         首页归档补齐"打勾"（原来只在进度 100% 时画，与行移除同帧，肉眼看不到）；
        //         恢复「沉浸式顶栏」开关（默认关）；首装默认配置按 zuige 当前习惯设定。
        // 0.8.3 = 国内更新兜底源：检查更新主源 GitHub、blog 静态源（blog.zuiges.com）兜底，
        //         APK 下载主源（GitHub 资产 CDN）失败时向 blog 源重试镜像直链。
        // 0.8.4 = 时间段待办：一条待办跨多天（「周一到周五出差」「10月21日到23日」「3天内交方案」），
        //         dueAtMillis 语义改为「最后一天」，因此逾期判定零改动 —— 最后一天过完才变红；
        //         新增 startAtMillis（Room v3→v4），列表按「进行中→今日」归档、显示 还剩N天/最后一天。
        // 0.8.5 = 修 0.8.4 的三个真机问题：
        //         ① 编辑待办回填句子带星期（「10月26日 周一」），被解析成两条（多出一条本周一 10/19）
        //            —— 回填改成无星期、时间段回填成「10月24日到26日」，并在 stripLeadingDate 里
        //            把「与已解析日期同一个星期几」的周几当注解，不再覆盖日期；
        //         ② 「下周一 到周三」里「到」开区间锚点取了 today，把前面的 10/26 覆盖成今天
        //            —— 改为取本句已解析到的日期，且右半是周几且落在开始日之前时顺延一周；
        //         ③ APK 下载默认走 blog 静态源，GitHub 资产 CDN 改为兜底。
        // 0.8.6 = 0.8.5 未发布（只装了内部验证），直接并入本版：
        //         ④ 时间段右侧不再显示「未开始」（未开始就该安静待在 明天/本周 里）；
        //         ⑤ 修「编辑只写日期的待办会把默认 9 点当成用户设的时刻」——
        //            保存后 hasExactTime 变 true，「最后一天结束才算逾期」退化成 9 点逾期；
        //         ⑥ 开始日 == 结束日 不再落成退化区间（避免一直挂着「最后一天」）；
        //            只有开始日没有结束日的脏数据也不会从列表消失。
        versionCode = 24
        versionName = "0.8.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)

    // core (cSdk36 compatible)
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3:1.5.0-alpha17")

    // small set of core icons (Add / Check / Close)
    implementation("androidx.compose.material:material-icons-core")

    // Room
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // WorkManager + coroutines
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // test
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}