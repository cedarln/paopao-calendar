package com.paopao.calendar.domain.workout

/**
 * 动作库：常见训练动作百科。
 *
 * 内容来自作者自己的 fit-miniapp（`data/exercises.js`，30 条），字段语义原样保留：
 * 名称 / 部位 / 器械 / 难度 / 要领 / 避坑提示。这里是纯静态数据，不依赖 Android，
 * 所以能直接在 JVM 单测里断言条数与筛选结果。
 *
 * [key] 是给 `PlanActionEntity.exerciseKey` 用的稳定标识，刻意与中文名称解耦 ——
 * 将来改文案（比如「臀桥」挪到臀部）不该让已经存在的计划动作找不到对应条目。
 */
enum class BodyPart(val label: String) {
    SHOULDER("肩"),
    BACK("背"),
    CHEST("胸"),
    ARMS("臂"),
    LEGS("腿"),
    CORE("核心"),
    CARDIO("有氧"),
}

enum class Equipment(val label: String) {
    BODYWEIGHT("自重"),
    DUMBBELL("哑铃"),
    BARBELL("杠铃"),
    MACHINE("器械"),
}

enum class Difficulty(val label: String) {
    BEGINNER("初"),
    INTERMEDIATE("中"),
    ADVANCED("高"),
}

data class Exercise(
    val key: String,
    val name: String,
    val part: BodyPart,
    val equipment: Equipment,
    val level: Difficulty,
    /** 动作要领，按步骤拆开存：详情页要按序号逐条渲染。 */
    val steps: List<String>,
    val tips: String,
)

object ExerciseCatalog {

    val all: List<Exercise> = listOf(
        // ===== 肩 =====
        Exercise(
            key = "sh_lateral_raise", name = "哑铃侧平举", part = BodyPart.SHOULDER,
            equipment = Equipment.DUMBBELL, level = Difficulty.BEGINNER,
            steps = listOf("直立，双手握哑铃垂于体侧", "肘微屈，向两侧抬至与肩同高", "缓慢下放还原，不甩腰"),
            tips = "想象用肘去够天花板，不是手",
        ),
        Exercise(
            key = "sh_overhead_press", name = "哑铃推举", part = BodyPart.SHOULDER,
            equipment = Equipment.DUMBBELL, level = Difficulty.BEGINNER,
            steps = listOf("坐姿，哑铃举至耳侧，小臂垂直地面", "垂直向上推至手臂伸直", "控制下落回耳侧"),
            tips = "核心收紧，别顶腰弓背",
        ),
        Exercise(
            key = "sh_front_raise", name = "哑铃前平举", part = BodyPart.SHOULDER,
            equipment = Equipment.DUMBBELL, level = Difficulty.BEGINNER,
            steps = listOf("双手握哑铃垂于大腿前", "单臂或双臂向前抬至与肩平", "顶峰停顿 1 秒慢放"),
            tips = "重量别贪大，控制比重量重要",
        ),
        Exercise(
            key = "sh_rear_fly", name = "俯身飞鸟", part = BodyPart.SHOULDER,
            equipment = Equipment.DUMBBELL, level = Difficulty.INTERMEDIATE,
            steps = listOf("俯身约 45°，背平直", "哑铃向两侧展开至肘略高于背", "慢放还原，不借惯性"),
            tips = "练的是后束，别练成划船",
        ),
        Exercise(
            key = "sh_shrug", name = "哑铃耸肩", part = BodyPart.SHOULDER,
            equipment = Equipment.DUMBBELL, level = Difficulty.BEGINNER,
            steps = listOf("直立，双肩同时向上提至耳侧", "顶峰停 1 秒", "缓慢下沉，肩胛骨放松"),
            tips = "只动肩不动肘",
        ),
        // ===== 背 =====
        Exercise(
            key = "bk_row", name = "哑铃划船", part = BodyPart.BACK,
            equipment = Equipment.DUMBBELL, level = Difficulty.BEGINNER,
            steps = listOf("单手扶凳，另一手持哑铃", "肘部贴身向后上拉至腰侧", "顶峰夹紧肩胛骨 1 秒慢放"),
            tips = "想着肘往后插口袋，不是往上提",
        ),
        Exercise(
            key = "bk_pull_up", name = "引体向上", part = BodyPart.BACK,
            equipment = Equipment.BODYWEIGHT, level = Difficulty.ADVANCED,
            steps = listOf("正握单杠略宽于肩", "背阔肌发力拉起至下巴过杠", "控制下落至手臂伸直"),
            tips = "拉不上去先做澳式引体或弹力带辅助",
        ),
        Exercise(
            key = "bk_lat_pulldown", name = "高位下拉", part = BodyPart.BACK,
            equipment = Equipment.MACHINE, level = Difficulty.BEGINNER,
            steps = listOf("坐实，宽握拉杆", "肩胛下沉，拉杆拉至上胸", "控制回放至手臂伸直"),
            tips = "别后仰太多，别拉到颈后",
        ),
        Exercise(
            key = "bk_bent_over_row", name = "俯身杠铃划船", part = BodyPart.BACK,
            equipment = Equipment.BARBELL, level = Difficulty.INTERMEDIATE,
            steps = listOf("俯身手握杠铃垂于膝前", "杠铃沿大腿拉至下腹", "夹背顶峰停 1 秒慢放"),
            tips = "背全程打平，腰别塌",
        ),
        // ===== 胸 =====
        Exercise(
            key = "ch_bench_press", name = "杠铃卧推", part = BodyPart.CHEST,
            equipment = Equipment.BARBELL, level = Difficulty.INTERMEDIATE,
            steps = listOf("仰卧凳上，握杠略宽于肩", "控制下放至上胸位置", "垂直推起，肘不外展成 90°"),
            tips = "肩胛后缩下沉，像把肩“钉”在凳上",
        ),
        Exercise(
            key = "ch_push_up", name = "俯卧撑", part = BodyPart.CHEST,
            equipment = Equipment.BODYWEIGHT, level = Difficulty.BEGINNER,
            steps = listOf("手略宽于肩，身体成一条直线", "屈肘下降至胸近地面", "推起至手臂伸直"),
            tips = "塌腰撅屁股是最常见错误",
        ),
        Exercise(
            key = "ch_incline_fly", name = "上斜哑铃飞鸟", part = BodyPart.CHEST,
            equipment = Equipment.DUMBBELL, level = Difficulty.INTERMEDIATE,
            steps = listOf("斜凳上仰卧，哑铃举于胸上", "肘微屈向两侧打开画弧", "像抱一棵大树一样合拢"),
            tips = "肘别打太直，否则压肩",
        ),
        Exercise(
            key = "ch_dips", name = "双杠臂屈伸", part = BodyPart.CHEST,
            equipment = Equipment.BODYWEIGHT, level = Difficulty.ADVANCED,
            steps = listOf("撑双杠，身体前倾", "屈肘下降至肩有拉伸感", "撑起至手臂伸直"),
            tips = "前倾练胸，直立练三头",
        ),
        // ===== 臂 =====
        Exercise(
            key = "ar_curl", name = "哑铃弯举", part = BodyPart.ARMS,
            equipment = Equipment.DUMBBELL, level = Difficulty.BEGINNER,
            steps = listOf("直立，大臂贴身固定", "屈肘将哑铃举至肩前", "控制慢放不甩动"),
            tips = "大臂一动，借力就来了",
        ),
        Exercise(
            key = "ar_overhead_extension", name = "颈后臂屈伸", part = BodyPart.ARMS,
            equipment = Equipment.DUMBBELL, level = Difficulty.BEGINNER,
            steps = listOf("双手托一只哑铃举过头顶", "屈肘下放哑铃至颈后", "伸肘举回头顶"),
            tips = "大臂别前后晃",
        ),
        Exercise(
            key = "ar_hammer_curl", name = "锤式弯举", part = BodyPart.ARMS,
            equipment = Equipment.DUMBBELL, level = Difficulty.BEGINNER,
            steps = listOf("大拇指朝上握哑铃", "弯举时保持锤式握法", "慢放还原"),
            tips = "练肱肌，手臂更粗的秘密",
        ),
        // ===== 腿 =====
        Exercise(
            key = "lg_squat", name = "徒手深蹲", part = BodyPart.LEGS,
            equipment = Equipment.BODYWEIGHT, level = Difficulty.BEGINNER,
            steps = listOf("双脚与肩同宽，脚尖微外八", "屈髋屈膝下蹲至大腿平行", "脚跟发力站起"),
            tips = "膝盖跟脚尖方向一致，别内扣",
        ),
        Exercise(
            key = "lg_lunge", name = "箭步蹲", part = BodyPart.LEGS,
            equipment = Equipment.BODYWEIGHT, level = Difficulty.BEGINNER,
            steps = listOf("一脚向前一大步", "下蹲至前腿大腿平行地面", "前脚蹬地站起换边"),
            tips = "前膝别超过脚尖太多",
        ),
        Exercise(
            key = "lg_glute_bridge", name = "臀桥", part = BodyPart.LEGS,
            equipment = Equipment.BODYWEIGHT, level = Difficulty.BEGINNER,
            steps = listOf("仰卧屈膝，脚踩地与髋同宽", "脚跟发力顶髋至肩膝成直线", "顶峰夹臀 2 秒慢放"),
            tips = "顶峰用力夹臀，不是顶腰",
        ),
        Exercise(
            key = "lg_calf_raise", name = "站姿提踵", part = BodyPart.LEGS,
            equipment = Equipment.BODYWEIGHT, level = Difficulty.BEGINNER,
            steps = listOf("直立，脚跟踮到最高", "顶峰停 1 秒感受小腿收缩", "慢放至脚跟近地面"),
            tips = "别弹震，匀速起落",
        ),
        Exercise(
            key = "lg_bulgarian_squat", name = "保加利亚分腿蹲", part = BodyPart.LEGS,
            equipment = Equipment.BODYWEIGHT, level = Difficulty.INTERMEDIATE,
            steps = listOf("后脚搭在凳上，前腿站立", "下蹲至前腿大腿平行", "前脚跟蹬起"),
            tips = "练单腿稳定性，比双腿深蹲更酸爽",
        ),
        // ===== 核心 =====
        Exercise(
            key = "co_crunch", name = "卷腹", part = BodyPart.CORE,
            equipment = Equipment.BODYWEIGHT, level = Difficulty.BEGINNER,
            steps = listOf("仰卧屈膝，手轻放耳侧", "上腹发力卷起肩胛骨离地", "下背起桥不离地，慢放"),
            tips = "别抱头扯脖子",
        ),
        Exercise(
            key = "co_plank", name = "平板支撑", part = BodyPart.CORE,
            equipment = Equipment.BODYWEIGHT, level = Difficulty.BEGINNER,
            steps = listOf("肘撑地，身体成一条直线", "收腹夹臀，别塌腰别撅臀", "保持均匀呼吸"),
            tips = "60 秒标准 > 5 分钟塌腰划水",
        ),
        Exercise(
            key = "co_russian_twist", name = "俄罗斯转体", part = BodyPart.CORE,
            equipment = Equipment.BODYWEIGHT, level = Difficulty.INTERMEDIATE,
            steps = listOf("坐姿，小腿悬空", "躯干左右扭转，手触两侧地面", "保持节奏匀速"),
            tips = "感受侧腹扭转，不是甩手臂",
        ),
        Exercise(
            key = "co_dead_bug", name = "死虫式", part = BodyPart.CORE,
            equipment = Equipment.BODYWEIGHT, level = Difficulty.BEGINNER,
            steps = listOf("仰卧，双臂双腿举空", "对侧手脚缓慢下放近地面", "收回换边，腰全程贴地"),
            tips = "腰贴不住地就先缩小幅度",
        ),
        // ===== 有氧 / 全身 =====
        Exercise(
            key = "cd_jumping_jack", name = "开合跳", part = BodyPart.CARDIO,
            equipment = Equipment.BODYWEIGHT, level = Difficulty.BEGINNER,
            steps = listOf("直立，起跳同时双脚打开、双手头顶击掌", "跳回还原", "保持前脚掌落地"),
            tips = "前脚掌落地缓冲，别砸脚跟",
        ),
        Exercise(
            key = "cd_high_knees", name = "高抬腿", part = BodyPart.CARDIO,
            equipment = Equipment.BODYWEIGHT, level = Difficulty.INTERMEDIATE,
            steps = listOf("原地跑，膝盖抬至与髋同高", "手臂自然摆动", "前脚掌快速交替"),
            tips = "核心收紧别后仰",
        ),
        Exercise(
            key = "cd_burpee", name = "波比跳", part = BodyPart.CARDIO,
            equipment = Equipment.BODYWEIGHT, level = Difficulty.ADVANCED,
            steps = listOf("下蹲撑地 → 双腿后跳成俯卧撑位", "可做一个俯卧撑", "收腿跳起击掌"),
            tips = "新手跳不动就先去掉俯卧撑和跳跃",
        ),
        Exercise(
            key = "cd_mountain_climber", name = "登山跑", part = BodyPart.CARDIO,
            equipment = Equipment.BODYWEIGHT, level = Difficulty.INTERMEDIATE,
            steps = listOf("平板支撑姿势", "左右腿快速交替提膝向胸", "臀部别上下晃"),
            tips = "追求速度，稳定其次",
        ),
        Exercise(
            key = "cd_squat_jump", name = "深蹲跳", part = BodyPart.CARDIO,
            equipment = Equipment.BODYWEIGHT, level = Difficulty.INTERMEDIATE,
            steps = listOf("先做一个深蹲", "爆发向上跳起", "轻柔落地接下一次"),
            tips = "落地屈膝缓冲，别砸关节",
        ),
    )

    /** 百科页的部位顺序：按训练习惯排，而不是按枚举定义序。 */
    val partOrder: List<BodyPart> =
        listOf(BodyPart.SHOULDER, BodyPart.BACK, BodyPart.CHEST, BodyPart.ARMS, BodyPart.LEGS, BodyPart.CORE, BodyPart.CARDIO)

    private val byKey: Map<String, Exercise> = all.associateBy { it.key }
    private val byName: Map<String, Exercise> = all.associateBy { it.name }

    fun find(key: String): Exercise? = byKey[key]

    /** 文案解析与手动输入都靠名称匹配，所以只认精确名，不做模糊 —— 模糊交给解析层的词典。 */
    fun findByName(name: String): Exercise? = byName[name.trim()]

    fun filter(part: BodyPart?, query: String): List<Exercise> = all.filter { exercise ->
        (part == null || exercise.part == part) &&
            (query.isBlank() ||
                exercise.name.contains(query.trim()) ||
                exercise.part.label.contains(query.trim()) ||
                exercise.equipment.label.contains(query.trim()))
    }
}
