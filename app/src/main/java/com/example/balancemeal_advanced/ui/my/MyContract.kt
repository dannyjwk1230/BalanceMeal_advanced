package com.example.balancemeal_advanced.ui.my

import com.example.balancemeal_advanced.core.model.*

data class BodyDraft(val birth: String = "", val sex: Sex? = null, val height: String = "", val weight: String = "", val activity: ActivityLevel? = null)
data class TargetDisplay(val energy: String, val carbohydrate: String, val protein: String, val fat: String)
sealed interface MyDialog {
    data class Nickname(val value: String) : MyDialog
    data class Body(val value: BodySettings) : MyDialog
    data object UnderAge : MyDialog
    data object Discard : MyDialog
    data class InvalidBody(val message: String) : MyDialog
}
data class MyUiState(
    val profile: LocalProfile = LocalProfile(), val loaded: Boolean = false,
    val nicknameDraft: String = "", val draft: BodyDraft = BodyDraft(),
    val savedBody: BodySettings? = null, val target: TargetDisplay? = null,
    val dialog: MyDialog? = null, val busy: Boolean = false, val error: String? = null,
)
object MyMessages {
    const val UNDER_AGE = "권장 섭취량 설정은 만 19세 이상 성인을 대상으로 합니다. 생년월일을 확인해주세요."
    const val ESTIMATE = "권장 섭취량은 참고용 추정치이며, 개인에 따라 실제 필요량은 달라질 수 있습니다."
    const val BASIS = "성인 기준의 에너지 필요량 추정식을 사용합니다."
    const val SOURCE = "참고 출처: 보건복지부·한국영양학회, 한국인 영양소 섭취기준"
}
fun ActivityLevel.label() = when (this) {
    ActivityLevel.INACTIVE -> "비활동적"
    ActivityLevel.LOW_ACTIVE -> "저활동적"
    ActivityLevel.ACTIVE -> "활동적"
    ActivityLevel.VERY_ACTIVE -> "매우 활동적"
}
fun ActivityLevel.description() = when (this) {
    ActivityLevel.INACTIVE -> "대부분 앉아서 생활하고 운동은 거의 하지 않음"
    ActivityLevel.LOW_ACTIVE -> "일상에서 걷는 시간이 조금 있고 가벼운 운동을 함"
    ActivityLevel.ACTIVE -> "평소 많이 움직이고 중강도 운동을 꾸준히 함"
    ActivityLevel.VERY_ACTIVE -> "평소 육체활동이 많거나 고강도 운동을 자주 함"
}
