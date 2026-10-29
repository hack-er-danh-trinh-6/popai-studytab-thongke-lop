package com.health.pop.domain.model

data class School(
    val id: String,
    val name: String,
    val classCount: Int
)

data class ClassOption(
    val id: String,
    val name: String,
    val teacherName: String
)

enum class MembershipStatus { NONE, PENDING, APPROVED, REJECTED }

data class ClassMembership(
    val status: MembershipStatus = MembershipStatus.NONE,
    val classId: String = "",
    val className: String = "",
    val schoolName: String = "",
    val teacherName: String = ""
)

data class ClassNotification(
    val id: String,
    val title: String,
    val body: String,
    val className: String,
    val senderName: String,
    val createdAt: String,
    val read: Boolean
)

data class ClassInbox(
    val unreadCount: Int,
    val items: List<ClassNotification>
)

data class ClassMember(
    val name: String,
    val isMe: Boolean
)

/** Thông tin lớp và danh sách thành viên đã được duyệt (chỉ có tên). */
data class ClassRoster(
    val className: String,
    val schoolName: String,
    val teacherName: String,
    val members: List<ClassMember>
)

enum class TimetableSession { MORNING, AFTERNOON }

/** Một tiết học. [weekday] theo cách gọi của trường: 2 = Thứ 2 ... 6 = Thứ 6. */
data class TimetableEntry(
    val weekday: Int,
    val session: TimetableSession,
    val period: Int,
    val subject: String
)
