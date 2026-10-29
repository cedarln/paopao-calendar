package com.paopao.calendar.domain.export

import java.time.ZoneId

/**
 * JSON 里每张表长什么样。
 *
 * 字段名与实体属性逐字对齐（`recurrenceText` 在文件里写作 `recurrence` 是唯一的一处改名，
 * 因为它在备份里读起来更该像名词），这样将来写导入时可以把 map 的 key 直接当列名用。
 * 时间字段同时给 `epochDay` 与 ISO 字符串两份：前者是给程序复原用的真值，
 * 后者是给用户在自己电脑上打开看一眼就能读懂的。
 */
internal object JsonTables {

    fun counts(snapshot: ExportSnapshot): Map<String, Any> = linkedMapOf(
        "events" to snapshot.events.size,
        "inventories" to snapshot.inventories.size,
        "tags" to snapshot.tags.size,
        "inventoryTags" to snapshot.tagLinks.size,
        "categories" to snapshot.categories.size,
        "habits" to snapshot.habits.size,
        "habitLogs" to snapshot.habitLogs.size,
        "focusSessions" to snapshot.focusSessions.size,
        "memorials" to snapshot.memorials.size,
        "workoutPlans" to snapshot.workoutPlans.size,
        "planActions" to snapshot.planActions.size,
        "workoutLogs" to snapshot.workoutLogs.size,
    )

    fun tables(root: MutableMap<String, Any?>, snapshot: ExportSnapshot, zone: ZoneId) {
        root["events"] = snapshot.events.map { event ->
            linkedMapOf<String, Any?>(
                "id" to event.id,
                "title" to event.title,
                "note" to event.note,
                "startEpochDay" to event.startEpochDay,
                "startDate" to JsonExport.day(event.startEpochDay),
                "endEpochDay" to event.endEpochDay,
                "endDate" to JsonExport.day(event.endEpochDay),
                "startMillis" to event.startMillis,
                "startTime" to JsonExport.stamp(event.startMillis, zone),
                "endMillis" to event.endMillis,
                "endTime" to JsonExport.stamp(event.endMillis, zone),
                "allDay" to event.allDay,
                "recurrence" to event.recurrenceText,
                "repeatUntilEpochDay" to event.repeatUntilEpochDay,
                "repeatUntilDate" to JsonExport.day(event.repeatUntilEpochDay),
                "remindMinutes" to event.remindMinutes,
                "colorArgb" to event.colorArgb,
                "categoryId" to event.categoryId,
                "createdAtMillis" to event.createdAtMillis,
            )
        }
        root["inventories"] = snapshot.inventories.map { item ->
            linkedMapOf<String, Any?>(
                "id" to item.id,
                "title" to item.title,
                "note" to item.note,
                "importance" to item.importance,
                "urgency" to item.urgency,
                "quadrantIndex" to item.quadrantIndex,
                "done" to item.done,
                "doneAtMillis" to item.doneAtMillis,
                "dueEpochDay" to item.dueEpochDay,
                "dueDate" to JsonExport.day(item.dueEpochDay),
                "dueMinuteOfDay" to item.dueMinuteOfDay,
                "recurrence" to item.recurrenceText,
                "categoryId" to item.categoryId,
                "parentId" to item.parentId,
                "sortOrder" to item.sortOrder,
                "createdAtMillis" to item.createdAtMillis,
                "updatedAtMillis" to item.updatedAtMillis,
                "deletedAtMillis" to item.deletedAtMillis,
            )
        }
        root["tags"] = snapshot.tags.map { linkedMapOf<String, Any?>("id" to it.id, "name" to it.name) }
        root["inventoryTags"] = snapshot.tagLinks.map {
            linkedMapOf<String, Any?>("inventoryId" to it.inventoryId, "tagId" to it.tagId)
        }
        root["categories"] = snapshot.categories.map {
            linkedMapOf<String, Any?>(
                "id" to it.id,
                "name" to it.name,
                "colorArgb" to it.colorArgb,
                "iconKey" to it.iconKey,
                "scope" to it.scope,
                "sortOrder" to it.sortOrder,
                "isSystem" to it.isSystem,
            )
        }
        root["habits"] = snapshot.habits.map { habit ->
            linkedMapOf<String, Any?>(
                "id" to habit.id,
                "name" to habit.name,
                "colorArgb" to habit.colorArgb,
                "iconKey" to habit.iconKey,
                "targetPerWeek" to habit.targetPerWeek,
                "startEpochDay" to habit.startEpochDay,
                "startDate" to JsonExport.day(habit.startEpochDay),
                "endEpochDay" to habit.endEpochDay,
                "endDate" to JsonExport.day(habit.endEpochDay),
                "categoryId" to habit.categoryId,
                "archivedAtMillis" to habit.archivedAtMillis,
            )
        }
        root["habitLogs"] = snapshot.habitLogs.map {
            linkedMapOf<String, Any?>(
                "habitId" to it.habitId,
                "epochDay" to it.epochDay,
                "date" to JsonExport.day(it.epochDay),
                "note" to it.note,
            )
        }
        root["focusSessions"] = snapshot.focusSessions.map { session ->
            linkedMapOf<String, Any?>(
                "id" to session.id,
                "startedAtMillis" to session.startedAtMillis,
                "startedAt" to JsonExport.stamp(session.startedAtMillis, zone),
                "plannedMinutes" to session.plannedMinutes,
                "actualMinutes" to session.actualMinutes,
                "mode" to session.mode,
                "completed" to session.completed,
                "inventoryId" to session.inventoryId,
            )
        }
        root["memorials"] = snapshot.memorials.map { memorial ->
            linkedMapOf<String, Any?>(
                "id" to memorial.id,
                "name" to memorial.name,
                "anchorEpochDay" to memorial.anchorEpochDay,
                "anchorDate" to JsonExport.day(memorial.anchorEpochDay),
                "isLunar" to memorial.isLunar,
                "repeatYearly" to memorial.repeatYearly,
                "colorArgb" to memorial.colorArgb,
                "iconKey" to memorial.iconKey,
                "note" to memorial.note,
                "remindDaysBefore" to memorial.remindDaysBefore,
                "createdAtMillis" to memorial.createdAtMillis,
            )
        }
        root["workoutPlans"] = snapshot.workoutPlans.map { plan ->
            linkedMapOf<String, Any?>(
                "id" to plan.id,
                "name" to plan.name,
                "bodyPart" to plan.bodyPart,
                "weekMask" to plan.weekMask,
                "estimatedMinutes" to plan.estimatedMinutes,
                "colorArgb" to plan.colorArgb,
                "iconKey" to plan.iconKey,
                "note" to plan.note,
                "sourceId" to plan.sourceId,
                "createdAtMillis" to plan.createdAtMillis,
                "archivedAtMillis" to plan.archivedAtMillis,
            )
        }
        root["planActions"] = snapshot.planActions.map { action ->
            linkedMapOf<String, Any?>(
                "id" to action.id,
                "planId" to action.planId,
                "sortOrder" to action.sortOrder,
                "name" to action.name,
                "sets" to action.sets,
                "reps" to action.reps,
                "durationSec" to action.durationSec,
                "restSec" to action.restSec,
                "exerciseKey" to action.exerciseKey,
                "note" to action.note,
            )
        }
        root["workoutLogs"] = snapshot.workoutLogs.map { log ->
            linkedMapOf<String, Any?>(
                "id" to log.id,
                "epochDay" to log.epochDay,
                "date" to JsonExport.day(log.epochDay),
                "planId" to log.planId,
                "planName" to log.planName,
                "durationSec" to log.durationSec,
                "startedAtMillis" to log.startedAtMillis,
                "sourceId" to log.sourceId,
                "note" to log.note,
            )
        }
    }
}
