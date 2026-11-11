# BalanceMeal 통합 인수인계

기준일: 2026-11-11 · 팀원: 정연호 / 최서윤 · 패키지: `com.example.balancemeal_advanced`

실행·설치·데이터 출처·역할분담·vibe coding 내역은 [README](../README.md)에 있습니다. 이 문서는 기존 개발 범위·공통 계약·진행 기록·검증·시연 문서에서 후속 작업에 필요한 내용을 모아 현재 코드에 맞게 정리한 **유일한 제출용 인수인계 문서**입니다. 이전 단계 로그·캡처·중복 명세는 로컬에 보존하고 Git 업로드에서 제외합니다.

## 현재 구현 상태

| 영역 | 구현된 동작 | 남은 작업 |
| --- | --- | --- |
| 공통 | Compose 테마·카드·입력란·영양 표시, 상단바, 5개 탭, 창 너비에 따른 하단바/레일/다열 배치 | 새 화면에서도 공통 컴포넌트 재사용 |
| 홈 | 오늘 저장한 네 끼니의 합계, 영양 진행 막대, 목표 범위·상태·피드백, 신체 정보 설정 이동 | 목록·분석 통합 후 회귀 검증 |
| 기록 | 날짜·끼니별 조회, 음식 검색·추가·삭제·섭취량 변경, 사진 선택·촬영·삭제, 식단 저장·변경·삭제 확인, 미저장 이동 확인 | 실제 외부 갤러리·카메라 앱 조합별 수동 확인 |
| 마이 | 신체 정보 저장·유효성·성인 제한, 계산 적용 토글, 사진 탭으로 선택, 사진·닉네임 일괄 저장, 미저장 이동 확인 | 프로필 변경 흐름의 외부 앱 수동 확인 |
| 목록·상세 | 목록·상세 참고 UI, UiState·이벤트 계약, Preview 예시 | **기간 조회·펼침·상세 실제 조회·상세 Route 미연결** |
| 분석 | 평균·기간 총량·끼니별 카드 UI, UiState·이벤트 계약, Preview 예시 | **기간 집계·선택 이벤트·실제 평균·목표 비교 미연결** |

목록·분석은 현재 `BalanceMealApp.kt`에서 기본 상태와 빈 이벤트 콜백을 받습니다. 표시 UI가 있다는 이유로 실제 기능이 완성된 것으로 간주하지 않습니다. 로그인·서버·커뮤니티·공유·사용자 정의 음식 생성은 현재 범위에 없습니다.

## 파일과 담당 경계

소스 기준 경로: `app/src/main/java/com/example/balancemeal_advanced/`

| 경로 | 책임 |
| --- | --- |
| `MainActivity.kt`, `AppContainer.kt` | 앱 진입과 Application 소유 공통 의존성 |
| `core/model/Models.kt` | 불변 도메인 모델·단위 |
| `core/nutrition/` | 식단 합계·EER·권장 범위·섭취 상태 판정 |
| `core/validation/SaveValidation.kt`, `core/time/TimeSource.kt` | 저장 직전 검증·교체 가능한 로컬 시계 |
| `data/repository/` | Repository 인터페이스·MyDependencies |
| `data/local/` | Room DB·식단 트랜잭션·Preferences DataStore |
| `data/food/`, `data/image/` | 음식 JSON 파싱·캐시, 내부 사진·카메라·사진 수명 |
| `ui/BalanceMealApp.kt`, `ui/navigation/` | 화면 연결·내비게이션·미저장 이동 경계 |
| `ui/home/`, `ui/record/`, `ui/my/` | 정연호 담당 기능 |
| `ui/meallist/`, `ui/analysis/` | 최서윤 후속 구현 영역 |
| `ui/components/`, `ui/theme/`, `ui/presentation/` | 공통 UI·색·타이포·표시 전용 모델 |
| `app/src/debug/.../ui/preview/` | Preview 예시. 실제 저장소에 넣지 않음 |
| `app/src/test/`, `app/src/androidTest/` | 로컬 단위 테스트 / 실제 Android 테스트 |
| `app/schemas/` | Room 스키마 이력. 빌드 결과물과 달리 Git에 포함 |
| `app/src/main/assets/foods_sample.json` | 앱과 테스트가 읽는 음식 데이터 원본 |

정연호는 공통 구조와 홈·기록·마이를 담당하고, 최서윤은 목록·상세·분석의 실제 데이터와 동작을 연결합니다. 공통 계약 변경은 기존 화면·테스트와 함께 확인합니다.

## 구조와 의존성 사용

`BalanceMealApplication`이 `MyDependencies`를 구현하고 lazy `AppContainer` 하나를 공유합니다.

```kotlin
val dependencies = application as MyDependencies
val meals = dependencies.meals
val foods = dependencies.foods
val settings = dependencies.settings
// time, images, photos, camera도 같은 Application 범위에서 제공
```

실제 획득·주입 예시는 `BalanceMealApp.kt`를 따릅니다. 현재 공통 프로필·기록·마이 ViewModel은 NavHost 밖에서 생성하고, 홈 ViewModel은 홈 목적지에서 생성합니다. 화면에서 별도의 Room·DataStore·AppContainer를 만들지 않습니다.

Composable은 상태를 표시하고 이벤트를 전달합니다. ViewModel은 외부에 읽기 전용 StateFlow를 노출합니다. DB 조회·영양 합계·기간 집계는 화면 함수 밖에서 수행합니다.

## 도메인 모델과 데이터 규칙

| 타입 | 필드·단위·의미 |
| --- | --- |
| `FoodUnit` | `G("g")`, `ML("ml")`. ml→g 변환 없음 |
| `NutrientAmounts` | `carbohydrateG, proteinG, fatG, energyKcal: Double`. 유한·0 이상 |
| `Food` | `code, name, unit, nutrientsPer100`; 기준량 getter는 100 |
| `MealType` | `BREAKFAST, LUNCH, DINNER, SNACK` |
| `MealItem` | `foodCode, amount, id`. amount는 양수·유한값, 해당 음식의 g/ml |
| `MealRecord` | `id, date: LocalDate, mealType, items, photoReference: String?` |
| `LocalProfile` | `nickname = "First Account", photoReference: String? = null` |
| `BodySettings` | `birthDate, sex, heightCm, weightKg, activityLevel, calculationEnabled = true` |
| `Sex / ActivityLevel` | `MALE/FEMALE`; `INACTIVE/LOW_ACTIVE/ACTIVE/VERY_ACTIVE` |
| `NutrientRange` | `lowerG, upperG` 양 끝을 포함 |

- `mealId`는 MealRecord.id, `itemId`는 MealItem.id, `foodCode`는 Food.code입니다. 같은 음식을 여러 번 추가해도 서로 다른 itemId로 유지합니다.
- 날짜+끼니당 식단은 하나입니다. 날짜·끼니 선택은 다른 식단을 조회하는 동작이며 기존 식단을 이동시키지 않습니다.
- 식사 시간 필드는 제거되었습니다. 모델·DB·저장 API·새 화면에 시간을 다시 추가하지 않습니다.
- 날짜는 로컬 달력의 LocalDate이며 ISO 문자열로 보관합니다. 미래 날짜는 저장할 수 없습니다. 실제 시간은 `dependencies.time`으로 읽습니다.
- null 식단=미기록, null 신체 정보=미설정, null 사진=사진 없음입니다. 표시값 `—`는 미조회이며 실제 0과 구분합니다.

## Repository 공개 계약

정의 원본은 `data/repository/Repositories.kt`입니다.

```kotlin
interface FoodRepository {
    suspend fun getAll(): List<Food>
    suspend fun getByCode(code: String): Food?
    suspend fun searchByName(query: String): List<Food>
}
interface MealRepository {
    fun observeOnDate(date: LocalDate): Flow<List<MealRecord>>
    fun observeMeal(date: LocalDate, mealType: MealType): Flow<MealRecord?>
    suspend fun getMeal(date: LocalDate, mealType: MealType): MealRecord?
    suspend fun getMeal(id: String): MealRecord?
    suspend fun createMeal(
        date: LocalDate, mealType: MealType,
        items: List<MealItem>, photoReference: String? = null,
    ): MealRecord
    suspend fun updateMeal(record: MealRecord): MealRecord
    suspend fun deleteMeal(id: String): Boolean
}
interface SettingsRepository {
    val profile: Flow<LocalProfile>
    val bodySettings: Flow<BodySettings?>
    suspend fun saveProfile(profile: LocalProfile)
    suspend fun updateNickname(nickname: String)
    suspend fun updateProfilePhoto(photoReference: String?)
    suspend fun saveBodySettings(settings: BodySettings)
}
```

- Flow는 초기값과 저장 후 변경을 방출합니다. 읽기 오류를 빈 목록·미기록으로 바꾸지 않습니다.
- FoodDataException=음식 읽기/파싱 실패, MealConflictException=유일성 충돌, MealNotFoundException=수정 대상 없음, StoredDataException=저장값 오류입니다. 입력 검증은 IllegalArgumentException으로 실패할 수 있습니다.
- deleteMeal은 실제 삭제 true / 대상 없음 false를 반환합니다.
- **기간 조회 API는 현재 없습니다.** 날짜별 observeOnDate를 ViewModel에서 조합하거나 기간 API를 공통 저장소·테스트와 함께 확장합니다.
- SettingsRepository의 saveProfile 기본 구현은 두 기존 갱신 함수를 순서대로 호출하는 호환 경로입니다. 실제 앱의 DataStoreSettingsRepository는 saveProfile을 재정의하여 닉네임·사진을 **한 번의 DataStore edit**로 저장합니다. 새 구현도 원자 저장을 보장해야 합니다.

## 저장소·초안·프로필·사진

### 영구 저장

- DB: `balance_meal.db`, Room **버전 2**.
- `meal_records(date, mealType)` UNIQUE, `meal_items.id` PRIMARY KEY, 부모 삭제 시 CASCADE.
- 신규 식단은 UUID를 발급하고, 수정은 기존 id를 유지합니다. 부모·자식 쓰기는 트랜잭션이며 실패 시 롤백합니다.
- v1→v2 마이그레이션은 식사 시간 열을 제거하면서 식단·행·사진 참조를 보존합니다. 파괴적 마이그레이션이나 데이터 초기화로 대체하지 않습니다.
- Preferences DataStore: `balance_meal_settings.preferences_pb`. 프로필, 신체 5개 값, 계산 적용 여부를 저장합니다.
- 신체 정보는 만 19세 이상·미래가 아닌 생일·양수 유한 키/체중을 검증해 저장합니다. 계산 OFF는 정보를 지우지 않고 목표·판정 적용만 끕니다.

### 초안과 이동 보호

- 기록의 original/start는 저장값/비교 기준, draft는 작성 중 값입니다. 저장 확정 전에는 홈 합계와 DB를 바꾸지 않습니다.
- 식단 저장·변경·삭제는 확인 후 처리합니다. 취소·실패 시 초안과 기존 저장값을 보존합니다.
- 프로필 사진 선택은 draftPhoto/draftBitmap 미리보기만 만듭니다. 마이 화면의 **변경 사항 저장**에서 닉네임과 함께 확정합니다. 상단바는 저장된 사진을 표시합니다.
- MyViewModel의 프로필 저장·사진 변경 여부·사진 작업 여부·폐기 콜백은 BalanceMealApp에서 ProfileViewModel과 연결합니다.
- 기록·신체 정보·닉네임·프로필 사진 변경 후 다른 화면으로 이동하면 같은 UnsavedChangesDialog를 사용합니다. 계속 작성=이동 취소, 이동하기=초안 폐기입니다.
- 회전·창 크기 변경은 ViewModel 초안을 유지합니다. 프로세스 종료 후 미저장 초안 복원은 제공하지 않습니다.

### 사진 보관

- 외부 갤러리는 GetContent, 카메라는 TakePicture + 비공개 FileProvider를 사용합니다. 촬영 임시는 `cache/captures`, 영구 사진은 `files/images/<UUID>.jpg`입니다.
- 외부 URI를 영구 참조로 쓰지 않고 내부로 복사·EXIF 보정·최대 1024px로 저장합니다. DB/설정에는 내부 경로를 저장합니다.
- PhotoLifecycle/PhotoFiles가 초안 lease와 실제 저장 참조를 보호하고 미사용 파일을 정리합니다. 새 사진 준비 → 저장 확정 → 이전 참조 해제 순서를 지킵니다.
- PhotoSafeMeals/PhotoSafeSettings는 공유 경계입니다. 이미 사진 잠금을 사용하는 API를 다시 photos.withReference로 감싸지 않습니다.
- 표시 화면은 images.load를 사용하고 사진 파일을 직접 삭제하지 않습니다. 다른 기기로 백업된 절대 경로의 복원은 보장하지 않습니다.
- 외부 카메라·선택 앱의 취소/거부/실패 시 기존 사진을 유지합니다.

## 계산과 UI 규칙

- 제공 음식은 100개(G 80개 / ML 20개)입니다. 성공적으로 읽은 카탈로그는 CachedFoodRepository가 캐시합니다.
- `food.nutrientsPer100.forPortion(amount)`와 `MealNutrition.total(items, foodsByCode)`를 재사용합니다.
- 영양소·칼로리 = 기준값 × 섭취량/100. 원본 energyKcal를 사용하고, 탄단지에서 kcal를 역산하지 않습니다.
- 합계·비교는 반올림 전 Double로 수행합니다. NutritionUiState가 g/% 소수 1자리, kcal 정수로 표시합니다.
- 중량 비율 차트는 C/P/F의 g 합을 분모로 사용합니다. 에너지 비율과 구분합니다.
- EnergyTargets는 성별·만 나이·cm→m·체중·활동 계수로 성인 EER를 계산합니다. 영양소 범위는 EER의 C 50~65%/4, P 10~20%/4, F 15~30%/9입니다.
- DailyNutritionAssessment는 칼로리 EER 85~115%, 영양소 g 하한~상한을 양 끝 포함으로 적정 판정합니다. evaluate의 문구는 홈 전용입니다.
- 홈의 막대 시각 최댓값은 영양소 500g, 칼로리 4000kcal입니다. 초과값은 막대만 제한하고 실제 수치·과다 판정은 유지합니다.
- 섭취량·키·체중이 유효한 양수 유한값이 아니면 입력 글자와 윤곽선을 빨간색으로 표시합니다. 섭취량 입력란 아래 개별 오류 문구는 표시하지 않습니다.
- 생년월일 미선택 시 글자·윤곽선·달력 아이콘이 빨간색입니다. 삭제 버튼과 삭제 확인 버튼도 빨간 계열을 사용합니다.
- 공통 WheelDatePicker: 식사 날짜=올해-1년~올해, 생년월일=1930년~올해. 월별 일수·윤년과 미래 선택 제한은 WheelDateRules를 재사용합니다.
- AdaptivePage는 창 너비 <600 / 600~839 / 840dp 이상 기준을 사용합니다. 공통 테마·입력란·차트를 새로 중복 구현하지 않습니다.

## 최서윤: 목록·상세 연결

구현 순서: 저장소 읽기 → ViewModel·UiState 매핑 → 이벤트 처리 → 상세 Route → 검증.

1. `ui/meallist/MealListScreen.kt`와 `MealListContract.kt`를 사용해 별도 목록 ViewModel을 만듭니다.
2. 일=1일, 주=7일, 월=30일입니다. 오늘 D 기준 D~D, D-6~D, D-29~D로 양 끝을 포함하고, 다른 범위 선택에도 해당 길이를 유지합니다.
3. 오래된 날짜가 위에 오도록 날짜 카드를 생성합니다. 미기록 날짜도 0kcal·0끼 요약을 유지합니다. 끼니 수는 음식 행 수가 아니라 식단 종류 수입니다.
4. 날짜 안 순서는 조식→중식→석식→간식·음료입니다. 음식 코드를 카탈로그와 연결해 영양과 단위를 표시합니다.
5. `MealExpansionRequested(mealId)`는 펼침 상태를 바꿉니다. `MealSelected(mealId)`는 getMeal(id)로 실제 상세를 조회하도록 새 목적지를 연결합니다.
6. 상세에는 날짜·끼니·사진·음식별 섭취량/영양·총합을 표시합니다. 식사 시간은 없습니다. Back 시 기간·스크롤·펼침 상태를 유지합니다.
7. `RecordForDateRequested(date)`를 연결하려면 매개변수가 없는 현재 Routes.Record의 날짜 진입 계약을 확장합니다. 기존 requestSelection과 미저장 guard를 재사용합니다.

현재 주요 상태는 MealListUiState(period, content, dates), MealDateUiState(date, label, summary, energyLabel, meals), MealRowUiState(mealId, label, foodSummary, energyLabel, nutrientSummary, expanded)입니다. 상세 참고 상태는 MealDetailUiState(dateLabel, mealLabel, nutrition, items)입니다. 사진·로딩·오류 상태는 필요에 따라 계약을 확장합니다.

## 최서윤: 분석 연결

1. `ui/analysis/AnalysisScreen.kt`와 `AnalysisContract.kt`를 사용해 별도 분석 ViewModel을 만듭니다. 기간 길이는 목록과 같은 1/7/30일입니다.
2. 유효 날짜 수 n은 식단이 하나라도 저장된 날짜 수입니다. 간식만 기록한 날도 포함하고, 미기록 날은 평균 분모에서 제외합니다.
3. 하루 평균 g/kcal = 기간 합계 ÷ n입니다. n=0이면 빈 차트·0kcal·미기록 안내를 표시하며 부족으로 평가하지 않습니다.
4. 탄단지 비율은 **기간 합산 g**로 계산합니다. 날짜별 퍼센트를 단순 평균하지 않습니다. 기간 총 kcal는 n으로 나누지 않습니다.
5. 조식·중식·석식·간식별 영양 합계·kcal·기록 수를 표시합니다. 네 종류의 합은 전체 합과 일치해야 합니다.
6. 현재 저장된 신체 정보와 계산 적용 여부를 사용해 **하루 평균**을 하루 목표와 비교합니다. 과거 기간도 현재 신체 정보를 기준으로 평가합니다.
7. 신체 정보가 없거나 계산 OFF이면 목표 평가 대신 설정/적용 안내를 표시합니다. BodySettingsRequested는 MY/BODY_SETTINGS 이동 요청에 연결합니다.
8. 칼로리 문구: `칼로리가 목표보다 부족해요.` / `목표 칼로리 달성했어요!` / `목표 칼로리를 초과했어요.`
9. 보조 문구에는 '내일은'을 붙이지 않습니다. 탄수화물→단백질→지방 순으로 조합하며 모두 적정일 때 `완벽한 영양 밸런스입니다!`를 표시합니다. 혼합은 `{과다}의 섭취를 줄이고 {부족} 섭취에 신경 써보세요.`, 과다만은 `{과다} 섭취에 주의해주세요.`, 부족만은 `{부족} 섭취에 신경 써보세요.`를 사용합니다.
10. 현재 신체 정보가 변경되면 목표와 코멘트가 달라질 수 있다는 bodyNotice를 유지합니다. 신체 변경으로 실제 음식 섭취 합계가 바뀌면 안 됩니다.

현재 AnalysisUiState는 period, average, averageBasis, commentTitle, commentBody, showBodySettingsCta, periodEnergyLabel, meals, bodyNotice를 제공합니다. 이벤트는 PeriodSelected, CalendarRequested, RangeSubmitted, BodySettingsRequested입니다. 위 항목들은 후속 구현 정책이며 이번 제출에서 실제 데이터 연결을 완료했다는 의미가 아닙니다.

## 화면 통합 경계

- `BalanceMealApp.kt`의 기본 목록/분석 상태와 빈 onEvent 자리를 실제 ViewModel로 교체합니다.
- 다른 화면으로 이동할 때는 `requests.request(NavigationRequest.Open(...))`를 사용합니다. 직접 navigate하여 미저장 확인을 우회하지 않습니다.
- 시스템 Back도 같은 경계를 사용합니다. 구성 폐기 시 cancelPendingNavigation으로 오래된 목적지 콜백을 취소합니다.
- 상세 Route에는 객체 전체 대신 mealId만 전달합니다.
- 빠른 기간 변경에서 오래된 결과 취소, 읽기 실패·재시도·미기록 구분, 회전·창 변경·상세 Back 상태 복원을 확인합니다.
- Preview 숫자를 실제 DB에 넣거나 고정 성공 응답으로 기능 연결을 대체하지 않습니다.

## 검증과 시연

2026-11-11 제출 정리 시 다음 명령을 실행했습니다.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
```

**결과: 빌드·lint·기기 테스트 APK 빌드 성공, 로컬 단위 테스트 83개 통과 / 실패 0 / 건너뜀 0.** 이번 정리에서는 실제 기기 테스트를 실행하지 않았습니다. 기기 테스트 소스와 실행 명령을 포함하며 이전 단계 결과를 이번 실행 결과로 합산하지 않습니다.

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

기기 테스트 중 프로필 사진을 선택 즉시 저장하던 이전 동작을 가정한 검증은 새 일괄 저장 동작에 맞춰 확인·갱신해야 합니다. 새 목표 화면을 연결한 뒤 전체 UI/저장소 테스트를 실행합니다.

시연 순서:

1. 마이에서 유효한 성인 생년월일·성별·키·체중·활동 수준을 저장하고 홈의 권장 범위를 확인합니다.
2. 기록에서 오늘 끼니를 선택하고 쌀밥 150g을 추가합니다. 원본 166kcal/100g이므로 249kcal인지 확인합니다.
3. 저장 확인에서 취소한 뒤 다시 확정합니다. 홈 합계, 재진입한 식단의 id·섭취량을 확인합니다.
4. 수량 변경·행 삭제·식단 삭제의 취소/확정, 미저장 상태의 날짜/끼니/탭/시스템 Back 이동 확인을 점검합니다.
5. 프로필 사진과 닉네임을 변경한 뒤 저장 전에는 상단바 저장값이 유지되는지, 이동 취소 시 초안이 남는지, 저장 후 함께 반영되는지 확인합니다.
6. 키·체중·섭취량의 빈값/0/음수/비숫자, 생년월일 미선택 표시를 확인합니다.
7. 계산 토글 OFF/ON 시 저장 정보는 유지되고 목표·피드백만 바뀌는지 확인합니다.
8. 앱 재실행 후 저장한 식단·사진·프로필·신체 정보가 유지되는지 확인합니다.
9. 현재 목록·분석은 UI 준비 상태임을 설명합니다. 통합 후 아래 항목을 추가 검증합니다.

| 후속 검증 | 기대값 |
| --- | --- |
| 7일 중 4일 기록, 총 6000kcal | n=4, 하루 평균 1500kcal, 기간 총량 6000kcal |
| 간식만 있는 날 / 미기록 날 | 간식 날은 분모 포함 / 미기록은 제외 |
| 일·주·월·윤년·월 경계 | 양 끝 포함 1/7/30일 |
| 기간별 탄단지 비율 | 날짜별 % 평균 대신 기간 합산 g 비율 |
| 끼니별 합계 | 네 종류 합계=기간 전체 합계 |
| 목표 판정 경계 | kcal 85/115%, 영양소 하한/상한 포함 |
| 신체 정보 변경 | 섭취값 불변, 목표·코멘트만 갱신 |
| 목록→상세→Back | 정확한 mealId·g/ml·사진·합계, 기간/위치 복원 |

외부 갤러리·카메라의 실제 선택/촬영/취소/거부, 다양한 OS·화면 크기·확대 글자·키보드, 백업 이관은 별도 확인 대상입니다.

## 제출 파일 관리

- README와 본 문서, 앱 실행 소스·리소스·음식 데이터, Preview·테스트, Room 스키마, Gradle 설정·Wrapper, 정연호 프롬프트 워드 원본을 포함합니다.
- `.gitignore`는 허용 목록 방식입니다. 빌드 캐시, APK/AAB, 개인 SDK 경로, IDE 설정, 서명키, 단계별 로그·캡처·과거 문서는 제외합니다.
- 새 루트 문서나 새 소스 세트를 추가할 때는 필요에 맞게 허용 목록을 갱신합니다. 최서윤 프롬프트 파일을 추가할 때도 업로드 경로를 허용하고 README에 연결합니다.
- 프롬프트 워드 원본은 `app/모바일소프트웨어_프롬프트_정연호.docx`입니다. 그 안의 과거 요청 중 식사 시간·프로필 즉시 저장 등은 이력이며 현재 동작은 위 설명과 실행 코드를 따릅니다.
- 최서윤 프롬프트와 vibe coding 사용 내역은 추후 제공받아 기록합니다.
