package com.example.features.main

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import coil.compose.rememberAsyncImagePainter
import com.example.core.constants.AppConfig
import com.example.data.models.CartItem
import com.example.data.models.SavedOrder
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Force Arabic layout direction (RTL) for standard Middle East intermediary UX
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        var currentTab by remember { mutableStateOf(0) } // Tabs: 0 -> Home, 1 -> Browser, 2 -> Cart, 3 -> History, 4 -> Settings

        val cartItems by viewModel.cartItems.collectAsState()
        val savedOrders by viewModel.savedOrders.collectAsState()
        val currentBrowserUrl by viewModel.currentBrowserUrl.collectAsState()
        val showAddToLocalCartSheetUrl by viewModel.showAddToLocalCartSheet.collectAsState()
        val pendingOrder by viewModel.pendingOrder.collectAsState()

        // WebView reference to command navigation back/forward in UI
        var webViewInstance by remember { mutableStateOf<WebView?>(null) }
        var isWebViewLoading by remember { mutableStateOf(false) }
        var isWebViewNetworkError by remember { mutableStateOf(false) }

        // Dialogue variables for profile form inside checkout
        var showCheckoutProfileDialog by remember { mutableStateOf(false) }

        // Local dynamic states for Add Bottom Sheet inside Composable to ensure lightweight state updates
        var bottomSheetUrl by remember { mutableStateOf("") }
        var bottomSheetName by remember { mutableStateOf("") }
        var bottomSheetSize by remember { mutableStateOf("") }
        var bottomSheetColor by remember { mutableStateOf("") }
        var bottomSheetQuantity by remember { mutableStateOf(1) }
        var bottomSheetPrice by remember { mutableStateOf("") }
        var bottomSheetNotes by remember { mutableStateOf("") }
        var bottomSheetImageUri by remember { mutableStateOf<Uri?>(null) }

        // Photo Picker launcher
        val photoPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
            onResult = { uri ->
                if (uri != null) {
                    bottomSheetImageUri = uri
                }
            }
        )

        // Sync sheet properties whenever ViewModel triggers sheet opening
        LaunchedEffect(showAddToLocalCartSheetUrl) {
            showAddToLocalCartSheetUrl?.let { url ->
                bottomSheetUrl = url
                bottomSheetName = "منتج شي إن"
                bottomSheetSize = ""
                bottomSheetColor = ""
                bottomSheetQuantity = 1
                bottomSheetPrice = ""
                bottomSheetNotes = ""
                bottomSheetImageUri = null
            }
        }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = AppConfig.APP_NAME,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { Toast.makeText(context, "وسيط تسوق شي إن المعتمد", Toast.LENGTH_SHORT).show() }) {
                            Icon(Icons.Default.ShoppingBag, contentDescription = "Logo", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = if (androidx.compose.foundation.isSystemInDarkTheme()) MaterialTheme.colorScheme.surface else Color(0xFFFFFBFF)
                    ),
                    modifier = Modifier.shadowUnderTopBar()
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = if (androidx.compose.foundation.isSystemInDarkTheme()) MaterialTheme.colorScheme.surface else Color(0xFFF7F2FA),
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        icon = { Icon(Icons.Default.Home, contentDescription = "الرئيسية") },
                        label = { Text("الرئيسية", fontSize = 11.sp) }
                    )
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        icon = { Icon(Icons.Default.Language, contentDescription = "التصفح") },
                        label = { Text("تصفح شي إن", fontSize = 11.sp) }
                    )
                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (cartItems.isNotEmpty()) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        ) {
                                            Text(cartItems.size.toString(), color = Color.White)
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.ShoppingCart, contentDescription = "السلة")
                            }
                        },
                        label = { Text("طلباتي", fontSize = 11.sp) }
                    )
                    NavigationBarItem(
                        selected = currentTab == 3,
                        onClick = { currentTab = 3 },
                        icon = { Icon(Icons.Default.History, contentDescription = "السجل") },
                        label = { Text("السابقة", fontSize = 11.sp) }
                    )
                    NavigationBarItem(
                        selected = currentTab == 4,
                        onClick = { currentTab = 4 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "الإعدادات") },
                        label = { Text("الإعدادات", fontSize = 11.sp) }
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Tab Switcher with Fade In
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                    label = "tabChange"
                ) { targetTab ->
                    when (targetTab) {
                        0 -> HomeScreen(onNavigateToBrowser = { currentTab = 1 })
                        1 -> BrowserScreen(
                            viewModel = viewModel,
                            currentUrl = currentBrowserUrl,
                            isWebViewLoading = isWebViewLoading,
                            isWebViewNetworkError = isWebViewNetworkError,
                            onWebViewInstanceAssigned = { webViewInstance = it },
                            onLoadingStateChanged = { isWebViewLoading = it },
                            onConnectionError = { isWebViewNetworkError = it },
                            webViewReference = webViewInstance,
                            cartCount = cartItems.size,
                            onNavigateToCart = { currentTab = 2 }
                        )
                        2 -> CartScreen(
                            viewModel = viewModel,
                            cartItems = cartItems,
                            onStartShopping = { currentTab = 1 },
                            onCheckoutClick = {
                                if (viewModel.preferencesManager.isProfileComplete()) {
                                    // Generate and send!
                                    val text = viewModel.prepareOrderAndGenerateUrl()
                                    viewModel.sendMsgToWhatsApp(context, text)
                                } else {
                                    // Complete profile first
                                    showCheckoutProfileDialog = true
                                }
                            }
                        )
                        3 -> HistoryScreen(viewModel = viewModel, savedOrders = savedOrders)
                        4 -> SettingsScreen(viewModel = viewModel)
                    }
                }

                // Confirm if WhatsApp successfully opened popup Dialog
                pendingOrder?.let { pending ->
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissPendingOrder() },
                        title = { Text("تأكيد إرسال الطلب", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Right) },
                        text = {
                            Text(
                                "هل قمت بإرسال تفاصيل الطلب بنجاح عبر واتساب إلى الرقم ${viewModel.preferencesManager.whatsappNumber}؟\n\nعند الاختيار بنعم، سيتم حفظ نسخة محلية في السجل وإفراغ السلة الحالية.",
                                fontSize = 14.sp,
                                textAlign = TextAlign.Right
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    viewModel.confirmWhatsAppSent()
                                    Toast.makeText(context, "تم حفظ الطلب وتصفير السلة بنجاح!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("نعم، تم الإرسال")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { viewModel.dismissPendingOrder() }) {
                                Text("تعديل أو إلغاء", color = Color.Gray)
                            }
                        }
                    )
                }

                // Custom Checkout Profile Dialog (forces client registration before WhatsApp)
                if (showCheckoutProfileDialog) {
                    Dialog(onDismissRequest = { showCheckoutProfileDialog = false }) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            var clientName by remember { mutableStateOf(viewModel.preferencesManager.customerName) }
                            var clientPhone by remember { mutableStateOf(viewModel.preferencesManager.phone) }
                            var clientCity by remember { mutableStateOf(viewModel.preferencesManager.city) }
                            var clientAddress by remember { mutableStateOf(viewModel.preferencesManager.address) }
                            var clientNotes by remember { mutableStateOf(viewModel.preferencesManager.generalNotes) }

                            Column(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(20.dp)
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("✍️ بيانات العميل الأساسية", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("أدخل بياناتك وسيتم تذكرها تلقائيًا للطلبيات القادمة لتسهيل الشحن والتوزيع.", fontSize = 13.sp, color = Color.Gray, textAlign = TextAlign.Center)
                                Spacer(modifier = Modifier.height(16.dp))

                                OutlinedTextField(
                                    value = clientName,
                                    onValueChange = { clientName = it },
                                    label = { Text("الاسم الكامل (مطلوب)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = clientPhone,
                                    onValueChange = { clientPhone = it },
                                    label = { Text("رقم الهاتف اليمني (مطلوب)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = clientCity,
                                    onValueChange = { clientCity = it },
                                    label = { Text("المدينة (مطلوب - صنعاء، عدن، تعز إلخ)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = clientAddress,
                                    onValueChange = { clientAddress = it },
                                    label = { Text("العنوان المختصر أو المنطقة (مطلوب)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = clientNotes,
                                    onValueChange = { clientNotes = it },
                                    label = { Text("ملاحظات عامة للشحن (اختياري)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 3
                                )

                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "⚠️ الأمان والخصوصية: لا تقم بإدخال بيانات بطاقتك البنكية أو أرقام حساباتك السرية مطلقًا في هذه الشاشات.",
                                    fontSize = 11.sp,
                                    color = Color.Red,
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Spacer(modifier = Modifier.height(20.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    TextButton(onClick = { showCheckoutProfileDialog = false }) {
                                        Text("إلغاء", color = Color.Gray)
                                    }
                                    Button(
                                        onClick = {
                                            if (clientName.trim().isEmpty() || clientPhone.trim().isEmpty() ||
                                                clientCity.trim().isEmpty() || clientAddress.trim().isEmpty()) {
                                                Toast.makeText(context, "يرجى تعبئة جميع الحقول المطلوبة", Toast.LENGTH_SHORT).show()
                                            } else {
                                                // Save profile
                                                viewModel.preferencesManager.customerName = clientName
                                                viewModel.preferencesManager.phone = clientPhone
                                                viewModel.preferencesManager.city = clientCity
                                                viewModel.preferencesManager.address = clientAddress
                                                viewModel.preferencesManager.generalNotes = clientNotes
                                                showCheckoutProfileDialog = false

                                                // Proceed to send!
                                                val text = viewModel.prepareOrderAndGenerateUrl()
                                                viewModel.sendMsgToWhatsApp(context, text)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text("حفظ ومتابعة الطلب")
                                    }
                                }
                            }
                        }
                    }
                }

                // Add to Local Cart Bottom Sheet
                if (showAddToLocalCartSheetUrl != null) {
                    ModalBottomSheet(
                        onDismissRequest = { viewModel.dismissAddToLocalCartSheet() },
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "🛒 إضافة منتج إلى طلبي الحالي",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )

                            // Brief Product URL Card
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Language,
                                        contentDescription = "URL",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = bottomSheetUrl,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                        color = Color.Gray
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            val clip = android.content.ClipData.newPlainText("product_url", bottomSheetUrl)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "تم نسخ الرابط!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Fields
                            OutlinedTextField(
                                value = bottomSheetName,
                                onValueChange = { bottomSheetName = it },
                                label = { Text("اسم المنتج (اختياري)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = bottomSheetSize,
                                    onValueChange = { bottomSheetSize = it },
                                    label = { Text("المقاس (مطلوب: M, L, S إلخ)") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 5.dp),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = bottomSheetColor,
                                    onValueChange = { bottomSheetColor = it },
                                    label = { Text("اللون (مطلوب: أسود، وردي)") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 5.dp),
                                    singleLine = true
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Price field (optional)
                                OutlinedTextField(
                                    value = bottomSheetPrice,
                                    onValueChange = { bottomSheetPrice = it },
                                    label = { Text("السعر في شي إن ($ - اختياري)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .padding(end = 10.dp),
                                    singleLine = true
                                )

                                // Quantity Counter
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .weight(0.8f)
                                        .border(
                                            1.dp,
                                            Color.LightGray,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    IconButton(
                                        onClick = { if (bottomSheetQuantity > 1) bottomSheetQuantity-- },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Minus")
                                    }
                                    Text(
                                        bottomSheetQuantity.toString(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    IconButton(
                                        onClick = { bottomSheetQuantity++ },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Plus")
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = bottomSheetNotes,
                                onValueChange = { bottomSheetNotes = it },
                                label = { Text("ملاحظات خاصة (تفاصيل إضافية / قياسات)") },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 2
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // Attachment picker
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.background)
                                    .clickable {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.PhotoLibrary,
                                    contentDescription = "Attach Gallery Image",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (bottomSheetImageUri != null) "✅ تم إرفاق صورة لقطة الشاشة" else "📸 إرفاق صورة للمنتج (اختياري من الاستوديو)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (bottomSheetImageUri != null) MaterialTheme.colorScheme.primary else Color.DarkGray
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                if (bottomSheetImageUri != null) {
                                    Image(
                                        painter = rememberAsyncImagePainter(bottomSheetImageUri),
                                        contentDescription = "Attached Preview",
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Button(
                                onClick = {
                                    if (bottomSheetSize.trim().isEmpty() || bottomSheetColor.trim().isEmpty()) {
                                        Toast.makeText(context, "الرجاء تحديد المقاس واللون للمنتج!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val p = bottomSheetPrice.toDoubleOrNull()
                                        viewModel.addToCart(
                                            productUrl = bottomSheetUrl,
                                            name = bottomSheetName,
                                            size = bottomSheetSize,
                                            color = bottomSheetColor,
                                            quantity = bottomSheetQuantity,
                                            price = p,
                                            notes = bottomSheetNotes,
                                            localImagePath = bottomSheetImageUri?.toString()
                                        )
                                        Toast.makeText(context, "🎉 تم تجميع المنتج بنجاح في السلة!", Toast.LENGTH_SHORT).show()
                                        viewModel.dismissAddToLocalCartSheet()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("submit_button"),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("إضافة إلى طلبي الحالي 🛍️", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }
                }
            }
        }
    }
}

// ── Tab 0: Home Intro Screen ──
@Composable
fun HomeScreen(onNavigateToBrowser: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Graphic Illustration with overlapping decorative circles
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            // Top-right background decorative circle
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .offset(x = 16.dp, y = (-16).dp)
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            )

            // Bottom-left background decorative circle
            Box(
                modifier = Modifier
                    .size(128.dp)
                    .offset(x = (-24).dp, y = 24.dp)
                    .align(Alignment.BottomStart)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.ShoppingBasket,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "وسيط التسوق المباشر لليمن",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Instructions Outline Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "كيفية الطلب بسهولة",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Steps List Items
                val steps = listOf(
                    "تصفح منتجات موقع شي إن الرسمي العربي داخل التطبيق." to 1,
                    "افتح صفحة المنتج المحدد الذي ترغب في شرائه." to 2,
                    "اضغط على زر «أضف إلى طلبي» العائم المتاح أسفل الصفحة." to 3,
                    "حدد المقاس واللون ثم أضفه فستسجّل السلة الرابط تلقائيًا." to 4,
                    "أرسل طلبك مجمّعًا ومجيبًا على الشحن دفعة واحدة عبر واتساب." to 5
                )

                steps.forEach { (text, index) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = text,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            textAlign = TextAlign.Right,
                            modifier = Modifier.weight(1f),
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = index.toString(),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Large Capsule CTA button
        Button(
            onClick = onNavigateToBrowser,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("start_shopping_button"),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Language, contentDescription = "ShopNow", tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("ابدأ التسوق الآن", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        Text(
            text = "هذا التطبيق وسيط مستقل وليس تابعًا لشركة شي إن الرسمية.",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )
    }
}

// ── Tab 1: Embedded WebView Browser Screen ──
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    viewModel: MainViewModel,
    currentUrl: String,
    isWebViewLoading: Boolean,
    isWebViewNetworkError: Boolean,
    onWebViewInstanceAssigned: (WebView) -> Unit,
    onLoadingStateChanged: (Boolean) -> Unit,
    onConnectionError: (Boolean) -> Unit,
    webViewReference: WebView?,
    cartCount: Int,
    onNavigateToCart: () -> Unit
) {
    val context = LocalContext.current
    var showUrlWarningDialog by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Address parameters bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (webViewReference?.canGoBack() == true) {
                        webViewReference.goBack()
                    } else {
                        Toast.makeText(context, "لا توجد صفحات سابقة لخلف", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
            }

            IconButton(
                onClick = {
                    webViewReference?.reload()
                    onConnectionError(false)
                }
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reload")
            }

            // Url display field
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.LightGray.copy(alpha = 0.2f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = currentUrl,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.DarkGray
                )
            }

            IconButton(
                onClick = {
                    try {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
                        context.startActivity(browserIntent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "فشل فتح الرابط خارجيًا", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = "External Browser")
            }

            // Cart quick checker icon
            IconButton(onClick = onNavigateToCart) {
                BadgedBox(
                    badge = {
                        if (cartCount > 0) {
                            Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                Text(cartCount.toString(), color = Color.White)
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = "Cart")
                }
            }
        }

        // Linear Progress loading Bar
        if (isWebViewLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            if (isWebViewNetworkError) {
                // Offline fallback page
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.SignalCellularConnectedNoInternet4Bar, contentDescription = "No Network", modifier = Modifier.size(64.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("انقطع الاتصال بالإنترنت!", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("يرجى فحص باقة البيانات أو واي فاي وإعادة المحاولة لتصفح موقع شي إن.", fontSize = 13.sp, color = Color.Gray, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            onConnectionError(false)
                            webViewReference?.reload()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("إعادة المحاولة الدائرية")
                    }
                }
            } else {
                // Android System WebView container
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            onWebViewInstanceAssigned(this)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    url?.let { viewModel.setBrowserUrl(it) }
                                    onLoadingStateChanged(true)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    url?.let { viewModel.setBrowserUrl(it) }
                                    onLoadingStateChanged(false)
                                }

                                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                    // Trigger connection error on main failures
                                    if (request?.isForMainFrame == true) {
                                        onConnectionError(true)
                                    }
                                }
                            }
                            loadUrl(currentUrl)
                        }
                    },
                    update = {
                        // Keep synced URL loaded safely if redirected
                    }
                )
            }

            // High-Contrast Rose Floating adding Button
            ExtendedFloatingActionButton(
                onClick = {
                    val activeUrl = webViewReference?.url ?: currentUrl
                    // Check if they are on a product page or if it's the home page.
                    // Home has ar.shein.com with very basic parameters.
                    val isHome = activeUrl.trim().endsWith("shein.com/") || activeUrl.trim().endsWith(".shein.com/ar") || activeUrl.trim().endsWith("shein.com/index.html") || activeUrl.trim().length < 25
                    if (isHome) {
                        showUrlWarningDialog = activeUrl
                    } else {
                        viewModel.triggerAddToLocalCart(activeUrl)
                    }
                },
                icon = { Icon(Icons.Default.AddShoppingCart, contentDescription = "Add Product", tint = Color.White) },
                text = { Text("أضف هذا المنتج لطلبي 🏷️", color = Color.White, fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .testTag("floating_add_button")
            )
        }
    }

    // Modal warnings if adding from homepage
    showUrlWarningDialog?.let { url ->
        AlertDialog(
            onDismissRequest = { showUrlWarningDialog = null },
            title = { Text("تنبيه تصفح المنتجات", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
            text = { Text("يبدو أنك في الصفحة الرئيسية لشي إن. افتح صفحة الفستان أو القطعة التي تعجبك أولاً حتى ترسل رابطها بالتفصيل للمندوب.\n\nهل تريد المتابعة وإضافة الرابط الحالي على أي حال؟") },
            confirmButton = {
                Button(
                    onClick = {
                        showUrlWarningDialog = null
                        viewModel.triggerAddToLocalCart(url)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("متابعة الإضافة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlWarningDialog = null }) {
                    Text("تصفح المنتجات أولاً", color = Color.Gray)
                }
            }
        )
    }
}

// ── Tab 2: Orders Cart List Screen ──
@Composable
fun CartScreen(
    viewModel: MainViewModel,
    cartItems: List<CartItem>,
    onStartShopping: () -> Unit,
    onCheckoutClick: () -> Unit
) {
    if (cartItems.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.RemoveShoppingCart, contentDescription = "Empty", modifier = Modifier.size(64.dp), tint = Color.LightGray)
            Spacer(modifier = Modifier.height(16.dp))
            Text("سلة تجميع الطلبات فارغة!", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))
            Text("تصفح منتجات شي إن، واضغط «أضف المنتج إلى طلبي» لتبدأ تجميع قائمتك والطلب عبر واتساب.", fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onStartShopping,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("ابدأ بتجميع فساتين ومنتجات شي إن")
            }
        }
    } else {
        val totalQuantity = cartItems.sumOf { it.quantity }
        val estimatedTotal = cartItems.sumOf { (it.displayedPrice ?: 0.0) * it.quantity }

        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "🛍️ سلة تجميع الطلبيات (${cartItems.size} منتجات)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp),
                textAlign = TextAlign.Right
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(cartItems, key = { it.id }) { item ->
                    CartItemCard(item = item, viewModel = viewModel)
                }
            }

            // Calculation Sheet at Bottom
            Card(
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadowUnderTopBar(reverse = true)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("إجمالي عدد القطع المطلوبة:", fontSize = 13.sp, color = Color.Gray)
                        Text("$totalQuantity قطع شحن", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("الإجمالي الكلي التقريبي:", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("$estimatedTotal $", fontSize = 16.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "🛒 الإجمالي تقريبي وقد يتغير حسب سعر موقع شي إن الرسمي وقت مراجعة المندوب وتأكيد المحادثة.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 15.sp,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = onCheckoutClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("checkout_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send")
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("متابعة وإرسال الطلب عبر واتساب 💬", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun CartItemCard(item: CartItem, viewModel: MainViewModel) {
    var isEditingDetails by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_item_card")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Loaded Attachment Preview
                Box(
                    modifier = Modifier
                        .size(65.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.LightGray.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.localImagePath != null) {
                        Image(
                            painter = rememberAsyncImagePainter(Uri.parse(item.localImagePath)),
                            contentDescription = "Attached Pic",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Default.Image, contentDescription = "Attached Pic", tint = Color.LightGray)
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.productName ?: "منتج شي إن",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "المقاس: ${item.size} | اللون: ${item.color}",
                        fontSize = 12.sp,
                        color = Color.DarkGray
                    )
                    if (item.displayedPrice != null) {
                        Text(
                            text = "السعر الفردي: ${item.displayedPrice} $",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (!item.notes.isNullOrEmpty()) {
                        Text(
                            text = "ملاحظة: ${item.notes}",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Action Controls
                Column(horizontalAlignment = Alignment.End) {
                    IconButton(onClick = { viewModel.deleteCartItem(item.id) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color.LightGray)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    IconButton(onClick = { isEditingDetails = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit details", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(4.dp))

            // Counter Quantity row and brief URL link
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Hyperlink openable
                val context = LocalContext.current
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.productUrl))
                            context.startActivity(intent)
                        }
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Link, contentDescription = "Link", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("فتح رابط المنتج", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                }

                // +/- controller
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .border(1.dp, Color.LightGray.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 4.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.updateCartItemQuantity(item.id, -1) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Dec", modifier = Modifier.size(16.dp))
                    }
                    Text(
                        item.quantity.toString(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    IconButton(
                        onClick = { viewModel.updateCartItemQuantity(item.id, 1) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Inc", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }

    // Modal popup to edit sizes/notes manually if they wanted to update color
    if (isEditingDetails) {
        Dialog(onDismissRequest = { isEditingDetails = false }) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("✏️ تعديل تفاصيل القطعة فوريًا", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))

                    var edName by remember { mutableStateOf(item.productName ?: "") }
                    var edSize by remember { mutableStateOf(item.size) }
                    var edColor by remember { mutableStateOf(item.color) }
                    var edPrice by remember { mutableStateOf(item.displayedPrice?.toString() ?: "") }
                    var edNotes by remember { mutableStateOf(item.notes ?: "") }

                    OutlinedTextField(value = edName, onValueChange = { edName = it }, label = { Text("اسم المنتج") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(value = edSize, onValueChange = { edSize = it }, label = { Text("المقاس") }, modifier = Modifier.weight(1f).padding(end = 4.dp))
                        OutlinedTextField(value = edColor, onValueChange = { edColor = it }, label = { Text("اللون") }, modifier = Modifier.weight(1f).padding(start = 4.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(value = edPrice, onValueChange = { edPrice = it }, label = { Text("السعر في شي إن ($)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(value = edNotes, onValueChange = { edNotes = it }, label = { Text("ملاحظات") }, modifier = Modifier.fillMaxWidth(), maxLines = 2)

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TextButton(onClick = { isEditingDetails = false }) { Text("إلغاء", color = Color.Gray) }
                        Button(
                            onClick = {
                                if (edSize.trim().isEmpty() || edColor.trim().isEmpty()) {
                                    Toast.makeText(viewModel.getApplication(), "المقاس واللون مطلوبان للتأكيد الشحن!", Toast.LENGTH_SHORT).show()
                                } else {
                                    val updated = item.copy(
                                        productName = edName.ifBlank { null },
                                        size = edSize,
                                        color = edColor,
                                        displayedPrice = edPrice.toDoubleOrNull(),
                                        notes = edNotes.ifBlank { null }
                                    )
                                    viewModel.updateCartItem(updated)
                                    isEditingDetails = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("تأكيد الحفظ")
                        }
                    }
                }
            }
        }
    }
}

// ── Tab 3: History list screen ──
@Composable
fun HistoryScreen(viewModel: MainViewModel, savedOrders: List<SavedOrder>) {
    val context = LocalContext.current

    if (savedOrders.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.AutoMirrored.Default.List, contentDescription = "History Details", modifier = Modifier.size(64.dp), tint = Color.LightGray)
            Spacer(modifier = Modifier.height(16.dp))
            Text("سجل الطلبات فارغ تمامًا", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))
            Text("عند إكمال أي طلب وإرساله عبر واتساب بنجاح، سيظهر رمزه وملخصه هنا للرجوع إليه أو إعادة استيراده.", fontSize = 13.sp, color = Color.Gray, textAlign = TextAlign.Center)
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "📁 سجل طلباتي السابقة (${savedOrders.size} طلبات)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp),
                textAlign = TextAlign.Right
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(savedOrders, key = { it.id }) { order ->
                    var isShowingDetailsDialog by remember { mutableStateOf(false) }

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    order.id,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 15.sp
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFE8F5E9))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        order.status,
                                        fontSize = 11.sp,
                                        color = Color(0xFF2E7D32),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text("تاريخ الطلب: ${SimpleDateFormat("yyyy/MM/dd | h:mm a", Locale.getDefault()).format(Date(order.createdAt))}", fontSize = 12.sp, color = Color.Gray)
                            Text("العميل: ${order.customerName} (${order.phone})", fontSize = 12.sp, color = Color.DarkGray)
                            Text("العنوان: ${order.city} - ${order.address}", fontSize = 12.sp, color = Color.DarkGray)

                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("إجمالي القطع: ${order.totalQuantity} | المنتجات المختلفة: ${order.itemsCount}", fontSize = 12.sp, color = Color.Gray)
                                Text("المبلغ: ${order.estimatedTotal} $", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                TextButton(onClick = { isShowingDetailsDialog = true }) {
                                    Icon(Icons.Default.ZoomIn, contentDescription = "View", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("تفاصيل المنتجات", fontSize = 13.sp)
                                }

                                TextButton(onClick = {
                                    viewModel.restoreOrderToCart(order)
                                    Toast.makeText(context, "تم استيراد ${order.itemsCount} منتج إلى السلة بنجاح!", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.ContentPasteGo, contentDescription = "Import", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("إضافة للسلة مجددًا", fontSize = 13.sp)
                                }

                                TextButton(onClick = {
                                    val text = viewModel.generateWhatsAppTextForOrder(order)
                                    viewModel.sendMsgToWhatsApp(context, text)
                                    Toast.makeText(context, "جاري فتح واتساب لإعادة الإرسال", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.SendToMobile, contentDescription = "Resend", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("إعادة إرسال", fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    // Historical Order Details Dialog
                    if (isShowingDetailsDialog) {
                        Dialog(onDismissRequest = { isShowingDetailsDialog = false }) {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(16.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Text("📋 تفاصيل المنتجات للطلب ${order.id}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                    Spacer(modifier = Modifier.height(12.dp))

                                    val list = viewModel.deserializeCartItemsList(order.itemsJson)
                                    list.forEachIndexed { idx, it ->
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text("المنتج رقم ${idx + 1}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp)
                                                if (!it.productName.isNullOrEmpty()) {
                                                    Text("الاسم: ${it.productName}", fontSize = 12.sp)
                                                }
                                                Text("المقاس: ${it.size} | اللون: ${it.color}", fontSize = 12.sp)
                                                Text("الكمية: ${it.quantity}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                if (it.displayedPrice != null) {
                                                    Text("السعر: ${it.displayedPrice} $", fontSize = 11.sp)
                                                }
                                                if (!it.notes.isNullOrEmpty()) {
                                                    Text("ملاحظات: ${it.notes}", fontSize = 11.sp, color = Color.Gray)
                                                }
                                                Text(
                                                    "رابط المنتج: ${it.productUrl}",
                                                    fontSize = 10.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = Color.Blue,
                                                    modifier = Modifier.clickable {
                                                        val browse = Intent(Intent.ACTION_VIEW, Uri.parse(it.productUrl))
                                                        context.startActivity(browse)
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = { isShowingDetailsDialog = false },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text("إغلاق التفاصيل")
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }
    }
}

// ── Tab 4: Settings screen ──
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    var isEditingProfile by remember { mutableStateOf(false) }
    var isEditingCustomConfig by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Identity card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.SupportAgent,
                        contentDescription = "Broker info",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(35.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("إعدادات وسيط تسوق شي إن", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("إشراف وإدارة الطلبات المباشرة", fontSize = 12.sp, color = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Preference item list
        ListItem(
            headlineContent = { Text("👤 تعديل ملف بياناتي للشحن", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
            supportingContent = {
                Text(
                    text = if (viewModel.preferencesManager.isProfileComplete()) {
                        "الاسم: ${viewModel.preferencesManager.customerName} (${viewModel.preferencesManager.phone})"
                    } else "لم يتم تهيئة الاسم أو رقم الهاتف بالكامل",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            },
            trailingContent = { Icon(Icons.Default.ChevronLeft, contentDescription = "Edit") },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { isEditingProfile = true }
        )

        Spacer(modifier = Modifier.height(8.dp))

        ListItem(
            headlineContent = { Text("⚙️ تهيئة إعدادات الوسيط الخاصة", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
            supportingContent = {
                Text(
                    text = "رقم واتساب: ${viewModel.preferencesManager.whatsappNumber}\nموقع التصفح: ${viewModel.preferencesManager.sheinHomeUrl}",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            },
            trailingContent = { Icon(Icons.Default.ChevronLeft, contentDescription = "Edit Config") },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { isEditingCustomConfig = true }
        )

        Spacer(modifier = Modifier.height(8.dp))

        ListItem(
            headlineContent = { Text("📞 تواصل مباشر بالدعم الفني", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
            supportingContent = { Text("دردش مباشرة للاستفسار عن الحساب والعمولات والطرود", fontSize = 12.sp, color = Color.Gray) },
            trailingContent = { Icon(Icons.Default.ArrowOutward, contentDescription = "Contact support") },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    val url = "https://wa.me/${viewModel.preferencesManager.whatsappNumber}?text=مرحبًا، لدي استفسار بخصوص خدمة وسيط شي إن"
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (e: Exception) {
                        Toast.makeText(context, "فشل الاتصال بواتساب", Toast.LENGTH_SHORT).show()
                    }
                }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Professional Disclosure Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.LightGray.copy(alpha = 0.15.toFloat())),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    "⚠️ إخلاء المسؤولية القانونية:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "هذا التطبيق وسيط مستقل لتجميع روابط المنتجات وإرسال طلبات العملاء عبر واتساب، وليس تطبيقًا رسميًا تابعًا لشركة شي إن (Shein) العالمية. الأسعار وتوفر المقاسات والألوان قد تتغير في موقع شي إن الأصلي، ويتم تأكيد الأسعار والعمولات النهائية بواسطة المندوب عبر المحادثة في واتساب قبل الدفع.",
                    fontSize = 11.sp,
                    color = Color.DarkGray,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.LightGray.copy(alpha = 0.15.toFloat())),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    "🔒 سياسة الخصوصية وحماية البيانات:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "نحن نحترم خصوصيتك تمامًا: فكل البيانات بما فيها المنتجات المضافة في السلة، الاسم، رقم الهاتف، العنوان، والطلبيات السابقة يتم حفظها بمجملها محليًا مشفرة داخل جهازك الاندرويد فقط. التطبيق لا يملك ولا يستعين بأي خوادم خارجية لتخزين بياناتك، ولا نطلب منك ولا نجمع بيانات تسجيل الدخول ببطاقتك البنكية أو أرقام حسابات شي إن إطلاقًا.",
                    fontSize = 11.sp,
                    color = Color.DarkGray,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Red Reset Local data button
        Button(
            onClick = { showDeleteConfirmDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
            modifier = Modifier
                .fillMaxWidth()
                .height(45.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.DeleteForever, contentDescription = "Clear Cache", tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("حذف جميع داتا التطبيق المحلية التراكمية", color = Color.White, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Version tracker
        Text("بيانات تتبعية - نسخة التشغيل v1.0.0", fontSize = 10.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(40.dp))
    }

    // Modal Profile Editor dialog
    if (isEditingProfile) {
        Dialog(onDismissRequest = { isEditingProfile = false }) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("👤 بيانات العميل الأساسية للشحن", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))

                    var clientName by remember { mutableStateOf(viewModel.preferencesManager.customerName) }
                    var clientPhone by remember { mutableStateOf(viewModel.preferencesManager.phone) }
                    var clientCity by remember { mutableStateOf(viewModel.preferencesManager.city) }
                    var clientAddress by remember { mutableStateOf(viewModel.preferencesManager.address) }
                    var clientNotes by remember { mutableStateOf(viewModel.preferencesManager.generalNotes) }

                    OutlinedTextField(value = clientName, onValueChange = { clientName = it }, label = { Text("الاسم الكامل (مطلوب)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(value = clientPhone, onValueChange = { clientPhone = it }, label = { Text("رقم الهاتف (مطلوب)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(value = clientCity, onValueChange = { clientCity = it }, label = { Text("المدينة (مطلوب)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(value = clientAddress, onValueChange = { clientAddress = it }, label = { Text("المنطقة أو العنوان بالتفصيل") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(value = clientNotes, onValueChange = { clientNotes = it }, label = { Text("ملاحظات عامة للشحن") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)

                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TextButton(onClick = { isEditingProfile = false }) { Text("إلغاء", color = Color.Gray) }
                        Button(
                            onClick = {
                                if (clientName.trim().isEmpty() || clientPhone.trim().isEmpty() ||
                                    clientCity.trim().isEmpty() || clientAddress.trim().isEmpty()) {
                                    Toast.makeText(context, "الرجاء كتم تعبئة الحقول الأساسية!", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.preferencesManager.customerName = clientName
                                    viewModel.preferencesManager.phone = clientPhone
                                    viewModel.preferencesManager.city = clientCity
                                    viewModel.preferencesManager.address = clientAddress
                                    viewModel.preferencesManager.generalNotes = clientNotes
                                    isEditingProfile = false
                                    Toast.makeText(context, "تم حفظ الملف بنجاح!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("تحديث الملف")
                        }
                    }
                }
            }
        }
    }

    // Modal Config Editor dialog
    if (isEditingCustomConfig) {
        Dialog(onDismissRequest = { isEditingCustomConfig = false }) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("⚙️ تهيئة معايير الوسيط المباشرة", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))

                    var cfgWhatsApp by remember { mutableStateOf(viewModel.preferencesManager.whatsappNumber) }
                    var cfgUrl by remember { mutableStateOf(viewModel.preferencesManager.sheinHomeUrl) }

                    OutlinedTextField(value = cfgWhatsApp, onValueChange = { cfgWhatsApp = it }, label = { Text("رقم واتساب المندوب (+967...)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(value = cfgUrl, onValueChange = { cfgUrl = it }, label = { Text("رابط موقع شي إن الافتراضي للتصفح") }, modifier = Modifier.fillMaxWidth(), maxLines = 1)

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("تستطيع تغيير رابط التصفح أو إدخال رقم واتساب المعتمد لتلقي الردرد في أي دولة.", fontSize = 11.sp, color = Color.Gray, textAlign = TextAlign.Center)

                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TextButton(onClick = { isEditingCustomConfig = false }) { Text("إلغاء", color = Color.Gray) }
                        Button(
                            onClick = {
                                if (cfgWhatsApp.trim().isEmpty() || cfgUrl.trim().isEmpty()) {
                                    Toast.makeText(context, "الحقول لا يمكن أن تكون فارغة!", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.preferencesManager.whatsappNumber = cfgWhatsApp
                                    viewModel.preferencesManager.sheinHomeUrl = cfgUrl
                                    isEditingCustomConfig = false
                                    Toast.makeText(context, "تم حفظ الضوابط بنجاح!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("حفظ المعايير")
                        }
                    }
                }
            }
        }
    }

    // Delete confirm dialogue
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("الحذف النهائي والنهائي للتطبيق", color = Color.Red, fontWeight = FontWeight.Bold) },
            text = { Text("هل أنت متأكد من رغبتك في حذف كل تفاصيل السلة المخزنة، وكل سجل الفواتير والمشتريات السابقة، وبيانات العميل؟\n\nهذه العملية نهائية ولا يمكن التراجع عنها.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAllLocalData()
                        showDeleteConfirmDialog = false
                        Toast.makeText(context, "تم مسح جميع بيانات التطبيق!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("نعم، تصفير السجل تمامًا")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("إلغاء وتراجع", color = Color.Gray)
                }
            }
        )
    }
}

// ── Custom Modifier helper to add a high-contrast shadow under or above panels ──
fun Modifier.shadowUnderTopBar(reverse: Boolean = false): Modifier = this.border(
    width = 1.dp,
    color = Color.LightGray.copy(alpha = 0.3f),
    shape = if (reverse) RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp) else RoundedCornerShape(0.dp)
)
