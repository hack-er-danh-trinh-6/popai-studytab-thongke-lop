package com.health.pop.data.local

import com.health.pop.domain.model.AssessmentGroup
import com.health.pop.domain.model.AssessmentPhase
import com.health.pop.domain.model.AssessmentQuestion

object MockDataProvider {
    // Bám sát 3.PHIẾUKHẢO SÁT.docx: PRE có 20 câu; POST có 20 câu VN/PT/PB/TT và 5 câu DG.
    private val common = listOf(
        AssessmentQuestion("VN1", AssessmentGroup.VN, "Tôi thường xuyên xem TikTok, Reels hoặc Shorts mỗi ngày."),
        AssessmentQuestion("VN2", AssessmentGroup.VN, "Tôi có thói quen xem liên tục nhiều video ngắn trong một lần sử dụng."),
        AssessmentQuestion("VN3", AssessmentGroup.VN, "Tôi thường mở các ứng dụng video ngắn khi cảm thấy rảnh rỗi."),
        AssessmentQuestion("VN4", AssessmentGroup.VN, "Tôi ưu tiên xem video ngắn hơn đọc các bài viết hoặc tài liệu dài."),
        AssessmentQuestion("VN5", AssessmentGroup.VN, "Tôi khó dừng lại khi đang xem các video ngắn."),
        AssessmentQuestion("PT1", AssessmentGroup.PT, "Tôi thường xuyên kiểm tra điện thoại ngay cả khi không có thông báo mới."),
        AssessmentQuestion("PT2", AssessmentGroup.PT, "Tôi cảm thấy khó chịu khi không có điện thoại hoặc Internet bên cạnh."),
        AssessmentQuestion("PT3", AssessmentGroup.PT, "Tôi thường sử dụng điện thoại trong lúc học tập hoặc làm bài tập."),
        AssessmentQuestion("PT4", AssessmentGroup.PT, "Tôi có thói quen chuyển qua lại giữa nhiều ứng dụng trong thời gian ngắn."),
        AssessmentQuestion("PT5", AssessmentGroup.PT, "Tôi cảm thấy buồn chán khi không sử dụng thiết bị số."),
        AssessmentQuestion("PB1", AssessmentGroup.PB, "Tôi dễ bị phân tâm khi học tập hoặc thực hiện nhiệm vụ quan trọng."),
        AssessmentQuestion("PB2", AssessmentGroup.PB, "Tôi thường mất kiên nhẫn khi phải đọc hoặc xem nội dung dài."),
        AssessmentQuestion("PB3", AssessmentGroup.PB, "Tôi khó duy trì sự tập trung vào một việc trong thời gian dài."),
        AssessmentQuestion("PB4", AssessmentGroup.PB, "Tôi thường chuyển sang hoạt động khác khi cảm thấy nội dung không đủ hấp dẫn."),
        AssessmentQuestion("PB5", AssessmentGroup.PB, "Tôi cảm thấy bồn chồn khi không được truy cập mạng xã hội hoặc video ngắn."),
        AssessmentQuestion("TT1", AssessmentGroup.TT, "Tôi có thể tập trung học tập liên tục từ 30 phút trở lên mà không bị phân tâm."),
        AssessmentQuestion("TT2", AssessmentGroup.TT, "Tôi có thể hoàn thành nhiệm vụ học tập mà không cần kiểm tra điện thoại."),
        AssessmentQuestion("TT3", AssessmentGroup.TT, "Tôi duy trì được sự chú ý khi đọc tài liệu hoặc sách có nội dung dài."),
        AssessmentQuestion("TT4", AssessmentGroup.TT, "Tôi có thể tập trung vào một nhiệm vụ cho đến khi hoàn thành."),
        AssessmentQuestion("TT5", AssessmentGroup.TT, "Tôi chủ động kiểm soát các yếu tố gây xao nhãng trong quá trình học tập.")
    )

    private val dg = listOf(
        AssessmentQuestion("DG1", AssessmentGroup.DG, "Mô hình P.O.P giúp tôi hiểu rõ hơn về hội chứng Popcorn Brain."),
        AssessmentQuestion("DG2", AssessmentGroup.DG, "Các hoạt động của mô hình giúp tôi giảm thời gian sử dụng video ngắn không cần thiết."),
        AssessmentQuestion("DG3", AssessmentGroup.DG, "Trợ lý ảo POP-AI hỗ trợ tôi theo dõi và cải thiện khả năng tập trung sâu."),
        AssessmentQuestion("DG4", AssessmentGroup.DG, "Tôi nhận thấy khả năng tập trung của mình được cải thiện sau quá trình tham gia dự án."),
        AssessmentQuestion("DG5", AssessmentGroup.DG, "Tôi mong muốn tiếp tục áp dụng mô hình P.O.P và sử dụng POP-AI trong thời gian tới.")
    )

    fun questions(phase: AssessmentPhase): List<AssessmentQuestion> = if (phase == AssessmentPhase.POST) common + dg else common
    val assessmentQuestions: List<AssessmentQuestion> = questions(AssessmentPhase.PRE)
}
