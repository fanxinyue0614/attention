# 调研笔记:安卓 app 选型 —— Kotlin(+ Jetpack Compose)vs React Native(通常配 Expo)

> **说明**:这是个人选型调研笔记,不是项目 ADR,不代表 attention 项目的技术决策。
> 调研日期:2026-11-01。撰写原则:关键论断追溯一手来源(官方文档、官方博客、官方发布说明、官方仓库/源码),证据等级逐条标注;找不到官方说法的点明确标注「未找到官方说法」。
>
> **证据等级图例**
> - 【一手】= 官方文档 / 官方博客 / 官方发布说明 / 官方仓库
> - 【二手】= 社区统计或第三方转述,仅作参考
> - 【未找到官方说法】= 检索后未定位到拥有该事实的一手来源

---

## 0. 先澄清:React ≠ React Native

提问中的「React(前端语言)」需要先纠正:

- **React 本身只是 web UI 库**(react.dev 定位为 "The library for web and native user interfaces" 中的 web 部分),它**不能直接构建安卓原生 app**。React 是纯 JavaScript 库,不包含任何安卓打包、原生组件渲染能力。
- 与「Kotlin + Jetpack Compose」对应的选项是 **JS/TS + React Native(通常再配 Expo 框架)**。React Native 官方定义:*"React Native allows developers who know React to create native apps."*(【一手】[reactnative.dev/docs/environment-setup](https://reactnative.dev/docs/environment-setup))
- 因此本文对比的是:**Kotlin + Jetpack Compose vs React Native + Expo**。

---

## 1. 两个方案的官方定位与现状速览

| | Kotlin + Jetpack Compose | React Native + Expo |
| --- | --- | --- |
| 官方定位 | *"Kotlin is Android's recommended programming language for modern android development."*【一手】 | 官方推荐经由框架使用:*"if you're building a new app with React Native, we recommend using a Framework"*(即 Expo)【一手】 |
| 当前版本/状态 | Compose 是 *"Android's UI app development toolkit"*【一手】;Jetpack 库 KMP 支持表持续更新(2026-10-12 版) | 官方文档当前版本 0.87(2026-08 更新);0.88 已在发布支持表中【一手】 |
| 语言 | Kotlin(Android 官方推荐语言) | JavaScript / TypeScript |
| 渲染 | 直接编译为原生 UI(平台 SDK 同一套体系) | New Architecture(0.76 起默认开启):JSI 直连 + C++ 共享渲染器 |
| 多平台 | 业务逻辑可经 KMP 共享(Google 官方支持);UI 可经 Compose Multiplatform 共享(iOS 已 stable) | 一套 JS 代码同时出 Android / iOS(官方路线) |

来源:
- [developer.android.com/kotlin](https://developer.android.com/kotlin)(页面 meta 与正文原句:*"Kotlin is Android's recommended programming language for modern android development"*)
- [reactnative.dev/docs/environment-setup](https://reactnative.dev/docs/environment-setup)
- [developer.android.com/develop/ui/compose](https://developer.android.com/develop/ui/compose)

---

## 2. 逐维度事实对比

### 2.1 语言与团队背景

**Android 侧(Kotlin)**
- Android 官方:"Kotlin is Android's recommended programming language";*"used by over 60% of professional Android developers"*;*"Android apps that use Kotlin are 20% less likely to crash"*【一手·官方声明,后两条为 Google 自述统计】([developer.android.com/kotlin](https://developer.android.com/kotlin))
- Android Studio *"provides first-class support for Kotlin"*【一手】(同上)
- Java 仍然可用:Kotlin 与 Java *"interoperable with ... the existing Android runtime"*;平台 SDK 自 API 28 起带 nullability 注解【一手】([developer.android.com/kotlin](https://developer.android.com/kotlin))

**RN 侧(JS/TS)**
- 前置知识是 JavaScript:*"To work with React Native, you will need to have an understanding of JavaScript fundamentals."*【一手】([reactnative.dev/docs/getting-started](https://reactnative.dev/docs/getting-started))
- **但 RN 项目并不豁免安卓原生知识**:
  - 访问平台 API 需写原生代码(Kotlin/Java):*"Sometimes a React Native app needs to access a native platform API that is not available by default in JavaScript"*;原生模块系统 *"exposes instances of Java/Objective-C/C++ (native) classes to JavaScript"*【一手】([reactnative.dev/docs/next/native-modules-intro](https://reactnative.dev/docs/next/native-modules-intro))
  - 走 bare 路线(或任何需要碰原生工程的时候)仍要用 Android Studio / Xcode:*"you can make a React Native app without a Framework using Android Studio, Xcode"*【一手】([reactnative.dev/docs/environment-setup](https://reactnative.dev/docs/environment-setup))
  - Android 16 的 edge-to-edge 适配通过 Gradle 属性 `edgeToEdgeEnabled` 控制【一手】([reactnative.dev/blog/2025/08/12/react-native-0.81](https://reactnative.dev/blog/2025/08/12/react-native-0.81))——说明 Gradle、manifest、签名、上架这套安卓工程链路在 RN 项目中依然存在。
- Expo 也一样:Expo Modules API 写原生模块用的是 *"Swift 和 Kotlin DSL"*;第三方原生库要配 config plugins / development builds【一手】([docs.expo.dev/bare/overview](https://docs.expo.dev/bare/overview))

**生态规模(二手参考)**
- Stack Overflow Developer Survey 2025(所有受访者):JavaScript 66%、TypeScript 43.6%、Kotlin 10.8%(专业开发者:68.8% / 48.8% / 11.5%)【二手·社区统计】([survey.stackoverflow.co/2025/technology](https://survey.stackoverflow.co/2025/technology))
- 解读:前端人才池(按语言使用率)远大于 Kotlin,这是 RN 的核心吸引力之一;但官方层面 Android 侧人才与文档以 Kotlin 为中心。

### 2.2 UI 与性能

**Compose 侧**
- Compose 是官方 UI toolkit:*"Android's modern UI toolkit is built on Kotlin"*【一手】([developer.android.com/kotlin](https://developer.android.com/kotlin));*"Discover Jetpack Compose, Android's UI app development toolkit"*【一手】([developer.android.com/develop/ui/compose](https://developer.android.com/develop/ui/compose))
- Compose 与平台渲染体系同源,不存在跨语言桥接层(平台事实,无单独官方页面专门声明,见上两句官方定位)。

**RN 侧(官方承认旧桥瓶颈 + New Architecture 重写)**
- RN 官方博客直接承认旧架构瓶颈:*"serializing function calls over the bridge quickly became a bottleneck, especially for frequent updates or large objects. This made it hard for apps to achieve 60+ FPS reliably."*【一手·官方博客】([reactnative.dev/blog/2024/10/23/the-new-architecture-is-here](https://reactnative.dev/blog/2024/10/23/the-new-architecture-is-here))
- **New Architecture 自 RN 0.76(2024-10-23)起默认开启**:*"When you upgrade to 0.76, the New Architecture and React 18 are enabled by default."* 要点:JSI 同步直连(去 bridge)、C++ 共享渲染器、事件循环、原生模块懒加载、Codegen 类型安全【一手·官方博客】(同上)
- 旧 bridge 仍保留兼容并将被移除:*"The bridge remains for backward compatibility ... In the future, we will remove the bridge code completely."*;旧架构自 0.80 起冻结(0.80 博客宣布 Legacy Architecture freeze)【一手·官方博客】
- Meta 生产验证声明:*"It is already used at scale at Meta in the Facebook app"*,以及 Expensify / Kraken / Bluesky 案例【一手·官方博客】(同上)

**性能对比基准**
- **【未找到官方说法】Compose 与 RN 的官方性能对比基准不存在**——Google 与 Meta 各自只发布自家性能声明(如 Compose Multiplatform 博客称 iOS 滚动 *"on par with SwiftUI"*;RN 博客称 Reanimated 4 与新渲染器协同),双方均未发布与对方的对比数据。任何「谁更快」的结论都只能来自第三方基准,本文不采信二手基准做定量结论。

### 2.3 原生能力与平台 API 覆盖(时间差)

**Kotlin 侧**:新平台 API 随 Android SDK 发布即可在 Kotlin 中直接调用(Kotlin 与平台 runtime 互通【一手】,见 2.1)。Predictive Back、权限模型、后台限制、Widget、通知等全部第一方可用。

**RN 侧的跟进机制与实测时间差(以 Android 16 为例)**:
- Android 16(API 36)平台 2025 年 6 月发布(社区讨论提及,官方 RN 博客未单列平台发布日);**RN 0.81(2025-08-12)默认 target API 36**:*"Android apps built with React Native 0.81 will now default to targeting Android 16 (API level 36)."*;*"Predictive back gesture is now enabled by default for apps targeting Android 16."*;16KB 页面大小:*"React Native is already 16KB page size compliant."*(2025-11-01 起 Play 上架硬性要求)【一手·官方博客】([reactnative.dev/blog/2025/08/12/react-native-0.81](https://reactnative.dev/blog/2025/08/12/react-native-0.81))
- 即:主流 targetSdk/行为变更,RN 大约在平台发布后 2 个月内跟进(单个样本观察,非官方承诺——**RN 没有发布「新 Android 特性支持 SLA」类官方说法,未找到官方说法**)。
- 未被 RN core 暴露为 JS API 的平台能力(Widget、部分通知/后台细节等),需要自写原生模块或依赖 Expo SDK 包。Expo SDK 官方范围:*"The Expo SDK is a collection of packages that provide access to device and system functionality such as camera, contacts, location, sensors, haptics, and more."*【一手】([docs.expo.dev/versions/latest/](https://docs.expo.dev/versions/latest/))。**Expo SDK 是否逐项覆盖 Widget、前台服务类型、精确后台策略等细分类目,官方页面未给出完整清单,未找到官方说法**;结论是:越贴近平台底层特性,RN 侧的自写原生模块概率越高(官方承认 native modules 不是日常主流但必备:*"While we don't expect this feature to be part of the usual development process, it is essential that it exists."*【一手】([reactnative.dev/docs/next/native-modules-intro](https://reactnative.dev/docs/next/native-modules-intro)))

### 2.4 minSdk / 设备兼容

- **RN 的硬性要求**:0.75 是最后一个支持 minSdk 23 的版本;*"Starting from React Native 0.76, the minSdk version will be 24 (Android 7.0)"*【一手·官方博客】([reactnative.dev/blog/2024/08/12/release-0.75](https://reactnative.dev/blog/2024/08/12/release-0.75))。官方发布支持表中 0.76–0.88 全部为 "Android 7.0"【一手·官方仓库】([github.com/reactwg/react-native-releases/blob/main/docs/support.md](https://github.com/reactwg/react-native-releases/blob/main/docs/support.md))
- 对比本项目:attention 是 **minSdk 26**(项目事实),高于 RN 要求的 24 → RN 在 minSdk 维度对这类项目**不构成障碍**;反过来 RN 的 minSdk 下限意味着要覆盖 Android 5.x/6.x 用户时不可用。
- Kotlin/Compose 侧:minSdk 由项目自定(Compose 属于 Jetpack 库,随项目走);attention 现有 minSdk 26 + Compose 已实际运行(项目事实)。

### 2.5 工具链与开发体验

**IDE 与工程**
- Android Studio:Kotlin 一等支持【一手】([developer.android.com/kotlin](https://developer.android.com/kotlin));KMP 项目可直接在 Android Studio 打开运行:*"You can open, edit, and run multiplatform projects in Android Studio."*【一手】([developer.android.com/kotlin/multiplatform](https://developer.android.com/kotlin/multiplatform))
- Android 官方对 React Native **没有也不需要有支持声明**【未找到官方说法/不适用】;RN 官方确认 bare 路线用 Android Studio/Xcode【一手】(见 2.1)。

**热更新与即时反馈**
- RN:Fast Refresh 官方文档:*"Fast Refresh is a React Native feature that allows you to get near-instant feedback for changes in your React components. Fast Refresh is enabled by default"*;编辑大多 *"visible within a second or two"*;仅函数组件保状态,类组件不保【一手】([reactnative.dev/docs/fast-refresh](https://reactnative.dev/docs/fast-refresh))
- Compose:Android Studio 提供 Live Edit 与 Preview(官方 KMP 页确认 Live Edit 亦覆盖 Compose Multiplatform 代码)【一手】([developer.android.com/kotlin/multiplatform](https://developer.android.com/kotlin/multiplatform))

**新建项目路线**
- RN:官方推荐框架路线 Expo;`react-native init` 已于 2024-12-31 停用;模板移出核心包【一手·官方博客】([reactnative.dev/blog/2024/08/12/release-0.75](https://reactnative.dev/blog/2024/08/12/release-0.75));官方页原话:*"We believe that the best way to experience React Native is through a Framework"*、Expo 与 Meta 团队 *"works in close collaboration"*【一手】([reactnative.dev/docs/environment-setup](https://reactnative.dev/docs/environment-setup))
- Expo 工具对已有 bare RN 项目是渐进式采用:*"All tools and services provided by Expo work great in any React Native app"*;EAS(Build/Update/Submit)为官方推荐但可选服务【一手】([docs.expo.dev/bare/overview](https://docs.expo.dev/bare/overview))

**OTA 动态更新(CodePush 现状)与商店政策**
- 微软官方:App Center 2025-03-31 退役;CodePush 转为开源自托管(*"We have prepared a special version of CodePush ... run independently from App Center"*,指向 [github.com/microsoft/code-push-server](https://github.com/microsoft/code-push-server))【一手】([learn.microsoft.com/en-us/appcenter/retirement](https://learn.microsoft.com/en-us/appcenter/retirement))
- Expo 官方(2024-08):*"On March 31, 2025 Microsoft App Center shuts down. At that same time CodePush as we know it will disappear as well."*;给出三条替代路线:自托管 CodePush / 开源 expo-updates 自建服务器 / EAS Update【一手·官方博客】([expo.dev/blog/what-to-do-without-codepush](https://expo.dev/blog/what-to-do-without-codepush))
- Google Play 政策(Device and Network Abuse,原文):*"An app distributed via Google Play may not modify, replace, or update itself using any method other than Google Play's update mechanism. Likewise, an app may not download executable code (such as dex, JAR, .so files) from a source other than Google Play. This restriction does not apply to code that runs in a virtual machine or an interpreter where either provides indirect access to Android APIs (such as JavaScript in a webview or browser)."* 以及 *"Apps or third-party code, like SDKs, with interpreted languages (JavaScript, Python, Lua, etc.) loaded at run time ... must not allow potential violations of Google Play policies."*【一手】([support.google.com/googleplay/android-developer/answer/9888379](https://support.google.com/googleplay/android-developer/answer/9888379))
- **政策结论**:RN 式 OTA(只更新 JS bundle 与资源,运行在解释型运行时中)落在政策豁免范围内,这是 RN 相对原生的独特能力;但 **OTA 永远不能更新原生代码**——下载 dex/JAR/.so 即违规。Kotlin 应用没有等价的合法 OTA 通道(Play 内更新只能走商店机制)。
- 16KB 页面大小(2025-11-01 起 targetSdk 35+ 上架要求):RN 官方声明已合规【一手】(0.81 博客);Kotlin/Compose 项目使用 AGP 构建时按 AGP 版本处理(官方 Play 政策页面向所有 app,不区分技术栈)。

### 2.6 长期维护与风险

**Google 对 Kotlin/Compose 的投入(一手证据)**
- Kotlin 是官方推荐语言;Compose 是官方 UI toolkit;**KMP 获得 Google 官方支持**:*"Kotlin Multiplatform (KMP) is officially supported by Google for sharing business logic between Android and iOS. Kotlin Multiplatform is stable and production-ready."*【一手】([developer.android.com/kotlin/multiplatform](https://developer.android.com/kotlin/multiplatform))
- Jetpack 核心库已 KMP 化(2026-10-12 更新的官方支持表):annotation、collection、compose、datastore、lifecycle、viewModel、navigation、navigation3、navigationevent、paging、**room(2.8.5)**、savedstate、sqlite;Tier 1 平台 = Android / JVM / iOS(Room 不含 Web)【一手】(同上)
- 大厂背书(官方页列举):Google Docs、Cash App、Duolingo、Forbes、Zomato 等【一手】(同上)

**Meta 对 RN 的投入(一手证据)**
- New Architecture 是 *"a ground-up rewrite of React Native we've been working on since 2018"*,已在 Facebook/Instagram(Quest)规模化使用【一手】([reactnative.dev/blog/2024/10/23/the-new-architecture-is-here](https://reactnative.dev/blog/2024/10/23/the-new-architecture-is-here))
- **发布节奏与支持承诺**:官方发布支持政策:*"the level of support ... is weighted to focus on the newer versions, while maintaining the previous two minors"*——即只维护最近两个 minor,再往前即 Unsupported【一手·官方仓库】([reactwg/react-native-releases/docs/support.md](https://github.com/reactwg/react-native-releases/blob/main/docs/support.md))
- 节奏观察(基于官方博客/支持表日期的归纳,非官方承诺):0.75(2024-08)→ 0.76(2024-10)→ 0.80(2025-06)→ 0.81(2025-08)→ 文档当前 0.87(2026-08)→ 0.88 在列,大致每 2–3 个月一个 minor。**含义:选 RN 就把「跟版本升级」当作常态化运维成本**(unsupported 版本不再发补丁)。
- 破坏性变更实际发生过:0.76 默认切 New Architecture 并预告将移除 interop 层(*"In a future release, we will remove the interop layer"*)【一手】(0.76 博客)。

**KMP 作为第三选项的成熟度(一手证据)**
- KMP 自 Kotlin 1.9.20(2023-11)起 stable:*"Kotlin Multiplatform Is Stable and Production-Ready"*、*"now 100% ready for use in production"*【一手·官方博客】([blog.jetbrains.com/kotlin/2023/11/kotlin-multiplatform-stable/](https://blog.jetbrains.com/kotlin/2023/11/kotlin-multiplatform-stable/))
- **Compose Multiplatform(CMP)在 iOS 上已 stable**:CMP 1.8.0(2025-05)宣布 *"Compose Multiplatform for iOS Is Stable and Production-Ready"*,官方称启动时间与原生相当、滚动性能 *"on par with SwiftUI"*【一手·官方博客】([blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0/](https://blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0/))
- Google 侧对 Jetpack KMP 库的平台支持分级:Tier 1(Android/JVM/iOS,全量 CI 测试 + 源/二进制兼容追踪)、Tier 2(macOS/Linux)、Tier 3(watchOS/tvOS/Windows/JS/WASM,不承诺)【一手】([developer.android.com/kotlin/multiplatform](https://developer.android.com/kotlin/multiplatform))

### 2.7 与本项目(attention)相关的具体注意点

- **纯 Kotlin/Jetpack 依赖在 RN 侧不可直接使用**:RN 的 JS 运行时(Hermes)中,Java 代码必须经 NativeModule 桥接暴露给 JS(*"The NativeModule system exposes instances of Java/Objective-C/C++ (native) classes to JavaScript"*)【一手】([reactnative.dev/docs/next/native-modules-intro](https://reactnative.dev/docs/next/native-modules-intro))。attention 现有的 Room、Compose、coroutines/Flow 业务逻辑在 RN 项目里没有直接对应物:要么重写为 JS/TS(配合 expo-sqlite 等替代),要么整段包装成原生模块——后者等于维护两套语言实现。
- **这些库恰好都在 Google 的 Jetpack KMP 支持表里**(room 2.8.5、compose、lifecycle、datastore、savedstate)【一手】([developer.android.com/kotlin/multiplatform](https://developer.android.com/kotlin/multiplatform)):如果未来 attention 想复用业务逻辑去 iOS,KMP 是官方路径,现有代码不用重写;走 RN 则复用率为零。
- **本地优先、无账号无网络的产品形态**削弱了 RN 的招牌优势:OTA 更新(EAS Update)在无网络场景无意义;本地优先重度依赖存储、后台、Widget 等平台细节(见 2.3),恰是 RN 侧覆盖最薄的部分。
- minSdk 26(项目事实)与 RN 要求的 minSdk 24 兼容,不构成迁移障碍(见 2.4)。

---

## 3. 「怎么选」决策框架(分情况,不替你拍板)

按四个问题依次过:

**Q1:团队是什么背景?**
- 已有 Kotlin/安卓团队,或主力是原生工程师 → 默认 Kotlin + Compose。Android 官方文档、模板、新特性示例都以 Kotlin/Compose 为中心【一手】([developer.android.com/kotlin](https://developer.android.com/kotlin));团队学习成本最低。
- 团队主要是 JS/TS 前端、几乎没有原生工程师 → RN + Expo 是官方认可的默认路线【一手】([reactnative.dev/docs/environment-setup](https://reactnative.dev/docs/environment-setup));但要预算「仍需安卓原生知识」的成本(Gradle、签名、上架、必要时写 Kotlin 原生模块,见 2.1/2.7)。

**Q2:是否需要同时出 iOS?**
- 只做安卓 → **Kotlin + Compose 优势区间**:平台 API 零等待(2.3)、官方推荐栈、无跨层桥接。
- 双平台同步发布、团队偏前端 → RN(一套 JS 代码出两端)或 KMP/CMP(一套 Kotlin 出两端)。两者都可用:RN 由 Meta 官方推荐 + Expo 生态;CMP 自 2025-05 起 iOS stable【一手】(JetBrains 博客)。KMP 路线可只共享业务逻辑、UI 仍各自原生,这正是 Google 官方对 KMP 的定位(*"for sharing business logic between Android and iOS"*)【一手】([developer.android.com/kotlin/multiplatform](https://developer.android.com/kotlin/multiplatform))。

**Q3:app 是否「重原生特性」?**
- 重度依赖平台新特性(新权限模型、后台限制、Widget、系统动画/Predictive Back 的首发适配、无网络本地优先)→ **Kotlin + Compose**(平台能力第一天可用,RN 侧要等 core 暴露或自写模块,见 2.3/2.7)。
- 以内容展示、表单、列表、网络请求为主(电商、社区、工具面板类),对平台深水区依赖低 → RN + Expo 的覆盖足够,OTA 更新(JS 层)是加分项(注意:OTA 只能更新 JS/资源,且需持续跟 RN 版本,见 2.5/2.6)。

**Q4:能不能接受各自的长尾成本?**
- 选 RN 的长尾成本:每 2–3 个月一个 minor、官方只维护最近两个 minor【一手】(support.md);New Architecture 迁移尾巴(interop 层未来移除);第三方库的新架构兼容性需逐个核对(官方 reactnative.directory 提供核对入口)。
- 选 Kotlin/Compose 的长尾成本:Gradle/AGP/Kotlin 升级周期;iOS 需求出现时才启动 KMP/CMP 迁移(但 Room/Lifecycle 等已 KMP 化,迁移面较小,见 2.6)。

**结合提问者现状的一句话归纳**(仅供参考,不是结论):你已在维护一个纯 Kotlin/Compose/Room 的安卓项目(minSdk 26,本地优先)——若新 app 形态与 attention 相似(重平台细节、无网络依赖),Kotlin + Compose 的边际成本最低、且与现有项目共享心智;若新 app 是需要快速双端迭代、由前端团队接手的内容型产品,才值得认真评估 RN + Expo;若核心诉求是「Kotlin 代码复用到 iOS」,先看 KMP(业务逻辑)而不是 RN。

---

## 4. 参考来源清单(全部为本次实际引用的 URL)

**Android 官方(developer.android.com)**
1. https://developer.android.com/kotlin —— Kotlin 官方推荐地位、"60% professional developers"、"20% less likely to crash"、Compose 定位、Android Studio 一等支持
2. https://developer.android.com/kotlin/multiplatform —— KMP 由 Google 官方支持、stable、Jetpack 库 KMP 支持表(含 Room 2.8.5)、Tier 1/2/3 平台分级、Android Studio 工具链(页面更新于 2026-10-12)
3. https://developer.android.com/develop/ui/compose —— Compose 官方定位("Android's UI app development toolkit")

**React Native 官方(reactnative.dev)**
4. https://reactnative.dev/docs/environment-setup —— 官方推荐 Framework(Expo)路线、EAS 定位、bare 路线用 Android Studio/Xcode(文档版本 0.87)
5. https://reactnative.dev/docs/getting-started —— JS 前置知识定位
6. https://reactnative.dev/blog/2024/10/23/the-new-architecture-is-here —— New Architecture 自 0.76 默认开启、旧桥瓶颈官方承认、bridge/interop 移除计划、Meta 规模化使用
7. https://reactnative.dev/blog/2024/08/12/release-0.75 —— minSdk 23→24 变更(0.76 起)、官方推荐框架、`react-native init` sunset(2024-12-31)
8. https://reactnative.dev/blog/2025/08/12/react-native-0.81 —— 默认 target Android 16(API 36)、edge-to-edge、Predictive back 默认开启、16KB 合规声明、Legacy Architecture 冻结(0.80 博客亦有)
9. https://reactnative.dev/docs/next/native-modules-intro —— 原生模块机制与门槛(Java/Kotlin/Swift/C++ 经 NativeModule 暴露)
10. https://reactnative.dev/docs/fast-refresh —— Fast Refresh 官方说明
11. https://github.com/reactwg/react-native-releases/blob/main/docs/support.md —— 官方发布支持政策(维护最近两个 minor)与各版本 minSdk/JDK/Node 依赖表(0.76–0.88 均为 Android 7.0)

**Expo 官方(docs.expo.dev / expo.dev)**
12. https://docs.expo.dev/bare/overview —— Expo 工具对任意 RN app 渐进可用、Expo Modules API(Swift/Kotlin DSL)、prebuild/CNG、EAS
13. https://docs.expo.dev/versions/latest/ —— Expo SDK 覆盖范围官方定义
14. https://expo.dev/blog/what-to-do-without-codepush —— CodePush 退役应对与 EAS Update 路线(2024-08-22)

**Microsoft 官方**
15. https://learn.microsoft.com/en-us/appcenter/retirement —— App Center 2025-03-31 退役;CodePush 服务端开源自托管(github.com/microsoft/code-push-server)

**JetBrains 官方(blog.jetbrains.com)**
16. https://blog.jetbrains.com/kotlin/2023/11/kotlin-multiplatform-stable/ —— KMP stable(Kotlin 1.9.20,2023-11)
17. https://blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0/ —— Compose Multiplatform for iOS stable(CMP 1.8.0,2025-05)

**Google Play 政策**
18. https://support.google.com/googleplay/android-developer/answer/9888379 —— Device and Network Abuse:自更新与可执行代码下载限制、解释型运行时豁免条款

**二手统计(仅参考)**
19. https://survey.stackoverflow.co/2025/technology —— JS/TS/Kotlin 使用率(SO 官方调查,社区统计性质)

**明确标注「未找到官方说法」的点**
- Compose vs RN 的官方性能对比基准(不存在,任何对比均为第三方)
- RN 对「新 Android 特性支持时限」的官方承诺(无 SLA 类文档;仅能以 0.81 与 Android 16 的跟进时序作单样本观察)
- Expo SDK 对 Widget、前台服务类型、后台策略等细分类目的逐项覆盖清单(官方页面未提供完整清单)
