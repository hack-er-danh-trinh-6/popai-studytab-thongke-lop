package com.health.pop.data.remote

// Gson có thể gán null cho trường bị thiếu, nên các trường đều nullable và được chuẩn hoá ở repository.
data class SchoolDto(
    val id: String? = null,
    val name: String? = null,
    val classCount: Int? = null
)

data class ClassOptionDto(
    val id: String? = null,
    val name: String? = null,
    val teacherName: String? = null
)

data class ClassMembershipDto(
    val status: String? = null,
    val classId: String? = null,
    val className: String? = null,
    val schoolName: String? = null,
    val teacherName: String? = null
)

data class JoinClassRequestDto(
    val classId: String
)

data class ClassNotificationDto(
    val id: String? = null,
    val title: String? = null,
    val body: String? = null,
    val className: String? = null,
    val senderName: String? = null,
    val linkUrl: String? = null,
    val createdAt: String? = null,
    val read: Boolean = false
)

data class ClassInboxDto(
    val unreadCount: Int? = null,
    val items: List<ClassNotificationDto>? = null
)

data class MarkReadRequestDto(
    val ids: List<String>? = null,
    val all: Boolean? = null
)

data class ClassMemberDto(
    val name: String? = null,
    val isMe: Boolean = false
)

data class ClassRosterDto(
    val className: String? = null,
    val schoolName: String? = null,
    val teacherName: String? = null,
    val memberCount: Int? = null,
    val members: List<ClassMemberDto>? = null
)

data class TimetableEntryDto(
    val weekday: Int? = null,
    val session: String? = null,
    val period: Int? = null,
    val subject: String? = null
)

data class TimetableDto(
    val className: String? = null,
    val entries: List<TimetableEntryDto>? = null
)

data class UsageEntryDto(
    val date: String,
    val app: String,
    val minutes: Int
)

data class UploadUsageRequestDto(
    val entries: List<UsageEntryDto>
)
