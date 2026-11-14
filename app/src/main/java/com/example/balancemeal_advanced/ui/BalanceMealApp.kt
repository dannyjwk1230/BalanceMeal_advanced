package com.example.balancemeal_advanced.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.*
import androidx.navigation.toRoute
import com.example.balancemeal_advanced.R
import com.example.balancemeal_advanced.ui.analysis.*
import com.example.balancemeal_advanced.ui.components.*
import com.example.balancemeal_advanced.ui.meallist.*
import com.example.balancemeal_advanced.ui.navigation.*
import com.example.balancemeal_advanced.ui.presentation.NutritionUiState
import com.example.balancemeal_advanced.ui.theme.BalanceColors
import java.util.UUID
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.balancemeal_advanced.ui.home.*
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.balancemeal_advanced.data.repository.MyDependencies
import com.example.balancemeal_advanced.ui.my.*
import com.example.balancemeal_advanced.ui.record.*
import androidx.compose.runtime.saveable.rememberSaveable
import java.time.LocalDate
import android.net.Uri

@Composable
fun BalanceMealApp(
    navigationPermission: NavigationPermission = AllowNavigation,
    onExit: () -> Unit = {},
    dependenciesOverride:MyDependencies?=null,
) {
    val dependencies = dependenciesOverride ?: LocalContext.current.applicationContext as MyDependencies
    val profileViewModel: ProfileViewModel = viewModel(factory = featureFactory { ProfileViewModel(dependencies.settings, dependencies.images,dependencies.photos) })
    val recordViewModel:RecordViewModel = viewModel(key="record",factory=featureFactory {RecordViewModel(dependencies.meals,dependencies.foods,dependencies.time,images=dependencies.images,photos=dependencies.photos,camera=dependencies.camera)})
    val myViewModel: MyViewModel = viewModel(key = "my", factory = featureFactory { MyViewModel(dependencies.settings, dependencies.time) })
    myViewModel.saveProfile = profileViewModel::saveProfile
    myViewModel.hasPhotoChanges = { profileViewModel.state.value.draftPhoto != null }
    myViewModel.photoBusy = { profileViewModel.state.value.savingPhoto }
    myViewModel.discardPhoto = profileViewModel::discardPhoto
    val profile by profileViewModel.state.collectAsStateWithLifecycle()
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> profileViewModel.selectPhoto(uri?.toString()) }
    val recordGallery=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {uri -> recordViewModel.galleryResult(uri?.toString())}
    val recordCamera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) {success -> recordViewModel.cameraResult(success)}
    LaunchedEffect(recordViewModel) {recordViewModel.photoRequests.collect {request ->
        try {when(request){RecordPhotoRequest.Gallery->recordGallery.launch("image/*");is RecordPhotoRequest.Camera->recordCamera.launch(Uri.parse(request.uri))}}
        catch(e:RuntimeException){recordViewModel.photoLaunchFailed()}
    }}
    val controller = rememberNavController()
    val entry by controller.currentBackStackEntryAsState()
    val selected = when {
        entry?.destination?.hasRoute<Routes.Record>() == true -> AppDestination.RECORD
        entry?.destination?.hasRoute<Routes.MealList>() == true -> AppDestination.LIST
        entry?.destination?.hasRoute<Routes.Analysis>() == true -> AppDestination.ANALYSIS
        entry?.destination?.hasRoute<Routes.My>() == true -> AppDestination.MY
        else -> AppDestination.HOME
    }
    val latestPermission = rememberUpdatedState(navigationPermission)
    val navigationActive=remember {java.util.concurrent.atomic.AtomicBoolean(true)}
    DisposableEffect(Unit) {onDispose {navigationActive.set(false);recordViewModel.cancelPendingNavigation();myViewModel.cancelPendingNavigation()}}
    val latestRecordSelected=rememberUpdatedState(selected==AppDestination.RECORD)
    val latestMySelected=rememberUpdatedState(selected==AppDestination.MY)
    val latestEntryId=rememberUpdatedState(entry?.id)
    val latestExit = rememberUpdatedState(onExit)
    val requests = remember(controller) {
        NavigationRequests(
            permission = NavigationPermission { request, decide ->
                val origin=latestEntryId.value
                latestPermission.value.check(request) {allowed ->
                    when {
                        !allowed || !navigationActive.get() || origin!=latestEntryId.value -> decide(false)
                        latestRecordSelected.value && request is NavigationRequest.Open && request.destination==AppDestination.RECORD -> decide(false)
                        latestRecordSelected.value -> recordViewModel.guardLeave(decide)
                        latestMySelected.value && request is NavigationRequest.Open && request.destination == AppDestination.MY -> decide(false)
                        latestMySelected.value -> myViewModel.guardLeave { proceed -> decide(proceed && navigationActive.get() && origin == latestEntryId.value) }
                        else -> decide(true)
                    }
                }
            },
            perform = { request ->
                if(navigationActive.get()) when (request) {
                    NavigationRequest.Back -> if (!controller.popBackStack()) latestExit.value()
                    is NavigationRequest.Open -> {
                        val bodyEntry = request.destination == AppDestination.MY && request.mySection == MySection.BODY_SETTINGS
                        val options: NavOptionsBuilder.() -> Unit = {
                            popUpTo(controller.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = !bodyEntry
                        }
                        when (request.destination) {
                            AppDestination.HOME -> controller.navigate(Routes.Home, options)
                            AppDestination.RECORD -> controller.navigate(Routes.Record, options)
                            AppDestination.LIST -> controller.navigate(Routes.MealList, options)
                            AppDestination.ANALYSIS -> controller.navigate(Routes.Analysis, options)
                            AppDestination.MY -> controller.navigate(
                                Routes.My(request.mySection.name, if (bodyEntry) UUID.randomUUID().toString() else ""), options,
                            )
                        }
                    }
                }
            },
        )
    }
    val requestBack: () -> Unit = { requests.request(NavigationRequest.Back) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val useRail = AppWindowClass.fromWidth(maxWidth)!=AppWindowClass.COMPACT
        CompositionLocalProvider(LocalAppWindowWidth provides maxWidth) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = { AppTopBar(
                onProfile = { requests.request(NavigationRequest.Open(AppDestination.MY)) },
                profile = profile,
            ) },
            bottomBar = { if (!useRail) AppNavigation(selected, false) { requests.request(NavigationRequest.Open(it)) } },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding).balanceBackground()) {
                if (useRail) AppNavigation(selected, true) { requests.request(NavigationRequest.Open(it)) }
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                    NavHost(controller, startDestination = Routes.Home, modifier = Modifier.fillMaxSize()) {
                        composable<Routes.Home> {
                            val homeViewModel:HomeViewModel=viewModel(factory=featureFactory {HomeViewModel(dependencies.meals,dependencies.settings,dependencies.foods,dependencies.time)})
                            val home by homeViewModel.state.collectAsStateWithLifecycle()
                            HomeScreen(home,
                            onRecord = { requests.request(NavigationRequest.Open(AppDestination.RECORD)) },
                            onBody = { requests.request(NavigationRequest.Open(AppDestination.MY, MySection.BODY_SETTINGS)) },
                            onRetry={ homeViewModel.retry() },
                            onEnableBody={ homeViewModel.enableBodyCalculation() },
                        )
                            // Destination handlers compose after NavHost's own predictive handler.
                            // Keep system Back behind the same permission boundary as app controls.
                            BackHandler(onBack = requestBack)
                        }
                        composable<Routes.Record> {
                            LaunchedEffect(Unit) {recordViewModel.onEnter()}
                            val record by recordViewModel.state.collectAsStateWithLifecycle()
                            var showMealDatePicker by rememberSaveable { mutableStateOf(false) }
                            RecordScreen(record,
                                onDate = { showMealDatePicker = true },
                                onMeal = { recordViewModel.requestSelection(RecordSelection.Meal(it)) },
                                onAmount={ id, amt -> recordViewModel.setAmount(id, amt) },
                                onRemove={ id -> recordViewModel.remove(id) },
                                onOpenPicker={ recordViewModel.openPicker() },
                                onClosePicker={ recordViewModel.closePicker() },
                                onSearch={ q -> recordViewModel.search(q) },
                                onChoose={ code -> recordViewModel.choose(code) },
                                onAddAmount={ amt -> recordViewModel.addAmount(amt) },
                                onAdd={ recordViewModel.addSelected() },
                                onRetry={ recordViewModel.retry() },
                                onPersist={ recordViewModel.requestPersist() },
                                onConfirmPersist={ recordViewModel.confirmPersist() },
                                onDismissDialog={ recordViewModel.dismissDialog() },
                                onDiscard={ recordViewModel.confirmDiscard() },
                                onGallery={ recordViewModel.beginGallery() },
                                onCamera={ recordViewModel.beginCamera() },
                                onRemovePhoto={ recordViewModel.removePhoto() }
                            )
                            if (showMealDatePicker) WheelDatePicker(
                                mode = DatePickerMode.MEAL,
                                initialDate = record.key.date,
                                today = dependencies.time.now().toLocalDate(),
                                onSelect = { recordViewModel.requestSelection(RecordSelection.Date(LocalDate.parse(it))) },
                                onDismissRequest = { showMealDatePicker = false },
                            )
                            BackHandler(enabled = !showMealDatePicker, onBack = requestBack)
                        }
                        composable<Routes.MealList> {
                            val listViewModel: MealListViewModel = viewModel(factory = featureFactory { MealListViewModel(dependencies.meals, dependencies.foods, dependencies.time) })
                            val listState by listViewModel.state.collectAsStateWithLifecycle()
                            
                            MealListScreen(listState, onEvent = listViewModel::handleEvent)
                            
                            if (listState.showDatePicker) {
                                WheelDatePicker(
                                    mode = DatePickerMode.MEAL,
                                    initialDate = dependencies.time.now().toLocalDate(),
                                    today = dependencies.time.now().toLocalDate(),
                                    onSelect = { dateStr -> 
                                        val d = LocalDate.parse(dateStr)
                                        listViewModel.handleEvent(MealListEvent.RangeSubmitted(d, d)) 
                                    },
                                    onDismissRequest = listViewModel::dismissDatePicker,
                                )
                            }
                            
                            BackHandler(onBack = requestBack)
                        }
                        composable<Routes.Analysis> {
                            val analysisViewModel: AnalysisViewModel = viewModel(factory = featureFactory { AnalysisViewModel(dependencies.meals, dependencies.foods, dependencies.settings, dependencies.time) })
                            val analysisState by analysisViewModel.state.collectAsStateWithLifecycle()
                            
                            AnalysisScreen(analysisState, onEvent = { event ->
                                if (event == AnalysisEvent.BodySettingsRequested) {
                                    requests.request(NavigationRequest.Open(AppDestination.MY, MySection.BODY_SETTINGS))
                                } else {
                                    analysisViewModel.handleEvent(event)
                                }
                            })
                            
                            if (analysisState.showDatePicker) {
                                WheelDatePicker(
                                    mode = DatePickerMode.MEAL,
                                    initialDate = dependencies.time.now().toLocalDate(),
                                    today = dependencies.time.now().toLocalDate(),
                                    onSelect = { dateStr ->
                                        val d = LocalDate.parse(dateStr)
                                        analysisViewModel.handleEvent(AnalysisEvent.RangeSubmitted(d, d))
                                    },
                                    onDismissRequest = analysisViewModel::dismissDatePicker,
                                )
                            }
                            
                            BackHandler(onBack = requestBack)
                        }
                        composable<Routes.My> { backStack ->
                            val route = backStack.toRoute<Routes.My>()
                            val state by myViewModel.state.collectAsStateWithLifecycle()
                            val lifecycleOwner = LocalLifecycleOwner.current
                            DisposableEffect(lifecycleOwner, myViewModel) {
                                val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) myViewModel.refreshTargets() }
                                lifecycleOwner.lifecycle.addObserver(observer)
                                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                            }
                            MyScreen(state, profile.copy(bitmap = profile.draftBitmap ?: profile.bitmap), 
                                onNickname = { myViewModel.nickname(it) }, 
                                onDraft = { myViewModel.edit(it) },
                                onRequestNickname = { myViewModel.requestNickname() }, 
                                onRequestBody = { myViewModel.requestBody() }, 
                                onConfirm = { myViewModel.confirm() }, 
                                onDismiss = { myViewModel.dismiss() },
                                onDiscard = { myViewModel.confirmDiscard() }, 
                                onPhoto = { photoPicker.launch("image/*") }, 
                                focusBody = route.section == MySection.BODY_SETTINGS.name, 
                                entry = route.entry,
                                onCalculationEnabled = { myViewModel.setCalculationEnabled(it) },
                                today = dependencies.time.now().toLocalDate()
                            )
                            BackHandler(onBack = requestBack)
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
internal fun AppTopBar(onProfile: () -> Unit, profile: ProfileUiState = ProfileUiState()) {
    Row(
        Modifier.fillMaxWidth().background(BalanceColors.HeaderSurface).statusBarsPadding()
            .heightIn(min = 64.dp).padding(horizontal = 12.dp).testTag("top_bar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).testTag("top_brand").padding(8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(
                painterResource(R.drawable.bm_logo_mark), null,
                Modifier.size(32.dp).testTag("top_logo"), contentScale = ContentScale.Fit,
            )
            Text(buildAnnotatedString {
                withStyle(SpanStyle(color = BalanceColors.Ink)) { append("Balance") }
                withStyle(SpanStyle(color = BalanceColors.Mint)) { append("Meal") }
            }, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-.45).sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).testTag("top_wordmark"))
        }
        IconButton(onClick = onProfile, modifier = Modifier.size(48.dp).testTag("top_profile")) {
            ProfileAvatar(profile, Modifier.size(32.dp))
        }
    }
}

private fun <T : ViewModel> featureFactory(create: () -> T) = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <V : ViewModel> create(modelClass: Class<V>): V = create() as V
}

@Composable
internal fun AppNavigation(selected: AppDestination, rail: Boolean, onSelect: (AppDestination) -> Unit) {
    @Composable fun icon(destination: AppDestination) {
        val resource = when (destination) {
            AppDestination.HOME -> R.drawable.bm_ic_home
            AppDestination.RECORD -> R.drawable.bm_ic_record
            AppDestination.LIST -> R.drawable.bm_ic_list
            AppDestination.ANALYSIS -> R.drawable.bm_ic_analysis
            AppDestination.MY -> R.drawable.bm_ic_profile
        }
        Icon(painterResource(resource), null, Modifier.size(24.dp))
    }
    if (rail) NavigationRail(modifier=Modifier.verticalScroll(rememberScrollState()).testTag("navigation_rail"),containerColor = BalanceColors.Background.copy(alpha = .85f)) {
        AppDestination.entries.forEach { destination ->
            NavigationRailItem(
                selected = destination == selected, onClick = { onSelect(destination) },
                icon = { icon(destination) }, label = { Text(destination.label, style = MaterialTheme.typography.labelSmall) },
                colors=NavigationRailItemDefaults.colors(selectedIconColor=BalanceColors.Mint,selectedTextColor=BalanceColors.MintDark,indicatorColor=BalanceColors.Mint.copy(alpha=.12f)),
                modifier = Modifier.testTag("nav_" + destination.name),
            )
        }
    } else NavigationBar(modifier=Modifier.testTag("navigation_bar"),containerColor = BalanceColors.Background.copy(alpha = .85f), tonalElevation = 0.dp) {
        AppDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = destination == selected, onClick = { onSelect(destination) },
                icon = { icon(destination) }, label = { Text(destination.label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = BalanceColors.Mint, selectedTextColor = BalanceColors.MintDark,
                    indicatorColor = BalanceColors.Mint.copy(alpha = .12f),
                ),
                modifier = Modifier.testTag("nav_" + destination.name),
            )
        }
    }
}


