---
name: 校园活动报名
description: 深夜教学楼走廊里的那块电子公告栏
colors:
  ink-900: "#07080D"
  ink-800: "#0C0E16"
  ink-700: "#131624"
  glass-fill: "rgba(255,255,255,0.045)"
  glass-line: "rgba(255,255,255,0.10)"
  text-primary: "#F2F4F9"
  text-secondary: "#A6AEC4"
  text-tertiary: "#6E768C"
  accent: "#38E1C8"
  accent-deep: "#1FAF9C"
  violet: "#7C6BFF"
  success: "#46D98B"
  warning: "#FFB454"
  danger: "#FF5F6D"
typography:
  display:
    fontFamily: "'PingFang SC', 'Microsoft YaHei', 'Noto Sans SC', system-ui, sans-serif"
    fontSize: "28px"
    fontWeight: 600
    lineHeight: 1.25
    letterSpacing: "-0.01em"
  body:
    fontFamily: "'PingFang SC', 'Microsoft YaHei', 'Noto Sans SC', system-ui, sans-serif"
    fontSize: "14px"
    fontWeight: 400
    lineHeight: 1.6
    letterSpacing: "normal"
  mono:
    fontFamily: "'JetBrains Mono', ui-monospace, SFMono-Regular, Consolas, monospace"
    fontSize: "13px"
    fontWeight: 500
    lineHeight: 1.4
    letterSpacing: "0.02em"
rounded:
  sm: "6px"
  md: "10px"
  lg: "14px"
  pill: "999px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "16px"
  lg: "24px"
  xl: "40px"
components:
  button-primary:
    backgroundColor: "{colors.accent}"
    textColor: "{colors.ink-900}"
    rounded: "{rounded.sm}"
    padding: "10px 20px"
    height: "40px"
  button-primary-hover:
    backgroundColor: "{colors.accent-deep}"
  card-surface:
    backgroundColor: "{colors.glass-fill}"
    textColor: "{colors.text-primary}"
    rounded: "{rounded.lg}"
    padding: "20px"
  input:
    backgroundColor: "{colors.ink-700}"
    textColor: "{colors.text-primary}"
    rounded: "{rounded.sm}"
    height: "40px"
---

# Design System: 校园活动报名

## Overview

**Creative North Star: "深夜教学楼的电子公告栏"**

想象晚上十点空无一人的教学楼走廊：墙是哑光的、几乎不反光，唯一的光源是那块背光玻璃公告栏——
海报在里面发着光，字浮在玻璃后面。这个系统就是那块板子：深色是环境，玻璃是介质，光是内容本身。

所以暗色不是"深色主题"，而是一个具体场所：**底色负责退后，玻璃负责分层，荧光只负责"这条信息要你看见"**。
界面里没有任何装饰性发光——每一处青色都在指一个可操作或需要注意的东西。

这个方向服务于工具属性：学生单手刷活动，管理员白天批量处理。
氛围来自材质（哑光底、磨砂玻璃、发丝边）而不是动效和渐变，
长时间盯屏不会累，白天在户外也能看清。

**Key Characteristics:**
- 哑光近黑底 + 半透明玻璃面 + 1px 发丝边
- 单一荧光青作为唯一强调色，用在主操作与"正在进行"的状态上
- 数字用等宽字体、等宽数字（tabular-nums），名额/时间永远对齐
- 状态同时用颜色 + 文字 + 图形表达，不做纯色语义
- 动效只表达状态变化，160ms 为主

## Colors

深色底上一套克制的荧光语汇：青色负责"可以做"，琥珀负责"要注意"，红色只用于错误。

### Primary
- **电光青** (#38E1C8)：主按钮、当前选中的筛选项、进行中的活动状态、可报名标记。
  在深底上它是最亮的东西，所以一个屏幕里只允许出现在真正需要行动的地方。

### Secondary
- **暮紫** (#7C6BFF)：只用于不可交互的强调——数据图表的第二序列、活动卡片封面的占位渐变。
  不与青色同屏竞争，出现位置永远比青色低一层。

### Neutral
- **墨黑** (#07080D)：页面底色。全局唯一的背景层。
- **深墨** (#0C0E16)：内容区之外的次级面（侧栏、工具栏）。
- **石墨** (#131624)：输入框、表格斑马纹这类"凹进去"的容器。
- **正文** (#F2F4F9)：主文字。
- **次要** (#A6AEC4)：说明、时间、辅助信息（对比度约 9:1）。
- **三级** (#6E768C)：占位符、禁用态，绝不承载唯一的状态说明。

### Named Rules
**The One Light Rule.** 荧光青在一屏内的覆盖面积不超过 10%。它是"手电筒"不是"墙纸"——
按钮、选中态、一条进行中的状态线，够了。

**The No Halo Rule.** 不允许零偏移的彩色光晕充当阴影。深度只用偏移 + 大模糊的黑影，
发光只允许出现在文字的 text-shadow（≤ 8px 模糊）和 1px 描边上。

## Typography

**Display / Body Font:** PingFang SC → Microsoft YaHei → Noto Sans SC（系统栈）
**Label / Mono Font:** JetBrains Mono → 系统等宽（仅数字、时间、ID、表格数值）

**Character:** 中文用系统黑体，不加花体——工具界面里字形的任务是"读得快"。
性格由等宽字体承担：名额、倒计时、活动时间用等宽，天然对齐，也暗示"这些是数据不是文案"。

### Hierarchy
- **Display**（600，28px，1.25）：页面主标题，如「近期活动」。不用于卡片内。
- **Headline**（600，20px，1.35）：卡片标题、区块标题。
- **Title**（500，15px，1.4）：列表项标题、表单分组名。
- **Body**（400，14px，1.6）：正文与说明，单行不超过 72ch。
- **Label**（500，12px，0.04em 字距）：字段名、状态标签、表头。中文不加字距。
- **Mono**（500，13px，tabular-nums）：名额 `12 / 50`、时间 `11-05 18:00`。

### Named Rules
**The Numbers Are Data Rule.** 任何会随数据变化的数字（名额、人数、倒计时、数量）必须等宽 + tabular-nums，
不允许因为数字变化导致布局跳动。

## Layout

12 栏栅格，内容最大宽度 1180px，两侧留白 24px（移动端 16px）。
页面纵向节奏：区块间距 40px，卡片之间 16px，卡片内 20px。

- **PC 优先**：≥1200 三列，≥768 两列，<768 单列；
- **活动列表**：卡片栅格，卡片等高，底部操作对齐；
- **活动详情**：左侧内容 8 栏 + 右侧操作栏 4 栏（≥1024 时 sticky，窄屏时操作栏沉到底部固定条）；
- **我的报名**：单列列表，行高 72px，状态列固定宽度；
- **管理后台**：左侧 200px 固定导航 + 内容区，表格行高 44px，工具条 sticky 在表格上方。

响应式是结构变化（列数、侧栏折叠、表格横向滚动），不是字号缩放。

## Elevation & Depth

不用传统的"卡片浮起"模型，用**层级 = 距离背景场的远近**：

底层是固定的背景场（两团低透明度的大半径径向渐变 + 极轻噪点），
中层是玻璃面（`rgba(255,255,255,.045)` + `backdrop-filter: blur(18px) saturate(120%)` + 1px 发丝边），
顶层是浮层（菜单、弹窗），用更实的 `#0C0E16` + 强阴影，不再用玻璃——浮层需要绝对清晰。

玻璃只有在"背后有东西"时才成立。这就是为什么背景场是系统的一部分，不是可选的装饰。

### Shadow Vocabulary
- **卡片静止**（`0 1px 0 rgba(255,255,255,.04) inset, 0 18px 40px -28px rgba(0,0,0,.9)`）：贴着背景，几乎不浮。
- **卡片 hover**（`0 1px 0 rgba(255,255,255,.06) inset, 0 24px 56px -28px rgba(0,0,0,.95)`）：只加深投影，上移不超过 2px。
- **浮层**（`0 32px 80px -24px rgba(0,0,0,.9), 0 0 0 1px rgba(255,255,255,.08)`）。

### Named Rules
**The Glass Needs Ground Rule.** 玻璃面板背后必须能看到背景场。放在纯色平背景上的毛玻璃等于一块脏抹布，
这种情况下直接用石墨色实底。

## Shapes

圆角克制：卡片 14px，按钮与输入框 6px，标签胶囊 999px。
描边一律 1px，颜色 `rgba(255,255,255,.10)`；强调态换成 `rgba(56,225,200,.45)`，不加粗。
不使用切角、斜切、六边形这类"赛博朋克几何"，那是另一个世界的语言。

## Components

### Buttons
- **Shape:** 6px 圆角，高度 40px（小号 32px），水平内边距 20px。
- **Primary:** 电光青实底 + 墨黑文字。一屏最多一个显性主按钮（列表里每张卡片各一个属于同一动作，例外）。
- **Hover / Focus:** hover 压到 accent-deep；focus 用 2px 外扩 `rgba(56,225,200,.25)` 环。
- **Secondary:** 玻璃底 + 发丝边 + 主文字色；hover 边框提亮到 18% 白。
- **Ghost:** 无底无边，仅文字；用于卡片内的次要动作。

### Chips
- **Style:** 玻璃底，12px 字，左侧一个 6px 圆点 + 文字。
- **State:** 报名中=青点，待确认=琥珀点，已满/已截止=灰点，已确认=青点+实心。
  颜色永远伴随文字，不允许只有颜色。

### Cards / Containers
- **Corner Style:** 14px
- **Background:** 玻璃面（见 Elevation）
- **Shadow Strategy:** 见 Shadow Vocabulary
- **Border:** 1px `rgba(255,255,255,.10)`
- **Internal Padding:** 20px
- **活动卡片的结构**：标题 → 时间（mono）→ 名额进度条（3px 轨道，青色填充）→ 状态 + 主操作。
  不做"图标 + 标题 + 两行简介"的三段式模板卡。

### Inputs / Fields
- **Style:** 石墨底（#131624）、1px 发丝边、6px 圆角、高度 40px、内边距 12px。
- **Focus:** 边框转青色 45%，外加 3px 柔光环。
- **Error:** 边框转危险色，下方 12px 错误文字（说明"错在哪、怎么改"，不写"输入有误"）。

### Navigation
- **Style:** 顶部 56px 玻璃条，左侧产品名，右侧用户与操作；移动端收成底部标签栏。
- 当前项用青色文字 + 2px 底部短横线（32px 宽），不用整块高亮。

### Signature Component
**名额进度条（Quota Meter）**：3px 高，圆角，轨道 `rgba(255,255,255,.08)`，填充青色；
剩余 ≤10% 时转琥珀，并伴随文字「仅剩 N 个」。它是这个产品唯一"配得上"的图表——
因为它对应用户唯一的真实焦虑。

## Do's and Don'ts

### Do:
- **Do** 每个数字都用 `font-variant-numeric: tabular-nums`，等宽对齐。
- **Do** 状态同时给颜色、文字和图形，任意去掉一个仍然能读懂。
- **Do** 动效控制在 160ms，`cubic-bezier(.2,0,0,1)`，并且尊重 `prefers-reduced-motion`。
- **Do** 焦点环始终可见，键盘 Tab 走完整个列表页能看出自己在哪。
- **Do** 空状态说清楚"为什么空、下一步做什么"，而不是"暂无数据"。

### Don't:
- **Don't** 用渐变文字强调任何东西；强调只靠字重和字号。
- **Don't** 在标题上方加小标签（eyebrow / kicker）。
- **Don't** 给卡片加超过 1px 的彩色左边框。
- **Don't** 用零偏移的彩色光晕当阴影，或用发光代替层级。
- **Don't** 在同一个屏幕里让青色和紫色同时出现并互相竞争。
- **Don't** 用 emoji 或 Unicode 符号充当图标；图标统一用一套线性图标。
- **Don't** 把毛玻璃铺在纯色底上——没有背景场就不用玻璃。
