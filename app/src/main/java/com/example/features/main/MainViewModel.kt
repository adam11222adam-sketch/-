package com.example.features.main

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.constants.AppConfig
import com.example.core.services.PreferencesManager
import com.example.core.services.SharedIntentManager
import com.example.data.local.AppDatabase
import com.example.data.models.CartItem
import com.example.data.models.SavedOrder
import com.example.data.repositories.CartRepository
import com.example.data.repositories.OrderRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val cartRepository = CartRepository(db.cartItemDao())
    private val orderRepository = OrderRepository(db.savedOrderDao())
    val preferencesManager = PreferencesManager(application)

    // UI States observed reactively
    val cartItems: StateFlow<List<CartItem>> = cartRepository.allCartItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedOrders: StateFlow<List<SavedOrder>> = orderRepository.allSavedOrders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // WebView browser navigation & share link pre-fill flows
    private val _currentBrowserUrl = MutableStateFlow(preferencesManager.sheinHomeUrl)
    val currentBrowserUrl: StateFlow<String> = _currentBrowserUrl.asStateFlow()

    // Screen trigger for Bottom Sheet opening
    private val _showAddToLocalCartSheet = MutableStateFlow<String?>(null) // Contains URL to add
    val showAddToLocalCartSheet: StateFlow<String?> = _showAddToLocalCartSheet.asStateFlow()

    // Temporary storage for order pending WhatsApp confirmation
    private val _pendingOrder = MutableStateFlow<SavedOrder?>(null)
    val pendingOrder: StateFlow<SavedOrder?> = _pendingOrder.asStateFlow()

    // Form states for the Bottom Sheet
    var addSheetUrl = ""
    var addSheetName = ""
    var addSheetSize = ""
    var addSheetColor = ""
    var addSheetQuantity = 1
    var addSheetPriceString = ""
    var addSheetNotes = ""
    var addSheetLocalImagePath = ""

    init {
        // Collect received external share intents reactively
        viewModelScope.launch {
            SharedIntentManager.sharedText.collect { text ->
                extractAndProcessUrl(text)
            }
        }
    }

    fun setBrowserUrl(url: String) {
        _currentBrowserUrl.value = url
        // Save last visited url optionally (up to user requirement)
        if (url.startsWith("http")) {
            // we could save it, but we respect privacy by not locking home url to dirty links.
        }
    }

    fun triggerAddToLocalCart(url: String) {
        _showAddToLocalCartSheet.value = url
    }

    fun dismissAddToLocalCartSheet() {
        _showAddToLocalCartSheet.value = null
    }

    private fun extractAndProcessUrl(text: String) {
        // Find if text contains a url
        val urlRegex = "(https?://[^\\s]+)".toRegex()
        val match = urlRegex.find(text)
        val extractedUrl = match?.value ?: text
        triggerAddToLocalCart(extractedUrl)
    }

    fun addToCart(
        productUrl: String,
        name: String?,
        size: String,
        color: String,
        quantity: Int,
        price: Double?,
        notes: String?,
        localImagePath: String?
    ) {
        viewModelScope.launch {
            val item = CartItem(
                productUrl = productUrl,
                productName = if (name?.trim().isNullOrEmpty()) null else name,
                size = size,
                color = color,
                quantity = quantity,
                displayedPrice = price,
                notes = if (notes?.trim().isNullOrEmpty()) null else notes,
                localImagePath = if (localImagePath?.trim().isNullOrEmpty()) null else localImagePath
            )
            cartRepository.insert(item)
        }
    }

    fun updateCartItem(item: CartItem) {
        viewModelScope.launch {
            cartRepository.update(item)
        }
    }

    fun deleteCartItem(id: String) {
        viewModelScope.launch {
            cartRepository.deleteById(id)
        }
    }

    fun updateCartItemQuantity(id: String, countChange: Int) {
        viewModelScope.launch {
            val currentList = cartItems.value
            val item = currentList.find { it.id == id } ?: return@launch
            val newQuantity = item.quantity + countChange
            if (newQuantity > 0) {
                cartRepository.update(item.copy(quantity = newQuantity))
            } else {
                cartRepository.deleteById(id)
            }
        }
    }

    // Helper functions to serialize items
    fun serializeCartItemsList(items: List<CartItem>): String {
        val array = JSONArray()
        for (item in items) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("productUrl", item.productUrl)
            obj.put("productName", item.productName ?: JSONObject.NULL)
            obj.put("size", item.size)
            obj.put("color", item.color)
            obj.put("quantity", item.quantity)
            obj.put("displayedPrice", item.displayedPrice ?: JSONObject.NULL)
            obj.put("notes", item.notes ?: JSONObject.NULL)
            obj.put("localImagePath", item.localImagePath ?: JSONObject.NULL)
            obj.put("createdAt", item.createdAt)
            array.put(obj)
        }
        return array.toString()
    }

    fun deserializeCartItemsList(jsonStr: String): List<CartItem> {
        val list = mutableListOf<CartItem>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val price = if (obj.isNull("displayedPrice")) null else obj.getDouble("displayedPrice")
                val item = CartItem(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    productUrl = obj.optString("productUrl", ""),
                    productName = if (obj.isNull("productName")) null else obj.optString("productName"),
                    size = obj.optString("size", ""),
                    color = obj.optString("color", ""),
                    quantity = obj.optInt("quantity", 1),
                    displayedPrice = price,
                    notes = if (obj.isNull("notes")) null else obj.optString("notes"),
                    localImagePath = if (obj.isNull("localImagePath")) null else obj.optString("localImagePath"),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                )
                list.add(item)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    // Prepare message and save temporary pending SavedOrder
    fun prepareOrderAndGenerateUrl(): String {
        val items = cartItems.value
        if (items.isEmpty()) return ""

        val totalQuantity = items.sumOf { it.quantity }
        val estimatedTotal = items.sumOf { (it.displayedPrice ?: 0.0) * it.quantity }

        // Generate WhatsApp string
        val sb = StringBuilder()
        sb.append("مرحبًا، أريد تنفيذ طلب جديد من شي إن 🛍️\n\n")
        sb.append("👤 بيانات العميل:\n")
        sb.append("الاسم: ${preferencesManager.customerName}\n")
        sb.append("رقم الهاتف: ${preferencesManager.phone}\n")
        sb.append("المدينة: ${preferencesManager.city}\n")
        sb.append("المنطقة أو العنوان: ${preferencesManager.address}\n")
        if (preferencesManager.generalNotes.trim().isNotEmpty()) {
            sb.append("ملاحظات العميل: ${preferencesManager.generalNotes.trim()}\n")
        }
        sb.append("\n🛒 المنتجات المطلوبة:\n")
        sb.append("━━━━━━━━━━━━━━\n")
        items.forEachIndexed { index, item ->
            sb.append("المنتج رقم ${index + 1}\n")
            if (!item.productName.isNullOrEmpty()) {
                sb.append("الاسم: ${item.productName}\n")
            }
            sb.append("الرابط: ${item.productUrl}\n")
            sb.append("المقاس: ${item.size}\n")
            sb.append("اللون: ${item.color}\n")
            sb.append("الكمية: ${item.quantity}\n")
            if (item.displayedPrice != null) {
                sb.append("السعر الظاهر: ${item.displayedPrice} $ \n")
            }
            if (!item.notes.isNullOrEmpty()) {
                sb.append("ملاحظات: ${item.notes}\n")
            }
            sb.append("━━━━━━━━━━━━━━\n")
        }

        sb.append("\n📊 ملخص الطلب:\n")
        sb.append("عدد المنتجات المختلفة: ${items.size}\n")
        sb.append("إجمالي عدد القطع: $totalQuantity\n")
        sb.append("الإجمالي التقريبي: $estimatedTotal $\n")
        sb.append("\n*الإجمالي تقريبي وقد يتغير حسب سعر شي إن وقت تنفيذ الطلب.*\n")

        // Construct pending SavedOrder
        viewModelScope.launch {
            // Generate id SH-YYYYMMDD-XXXX
            val datePrefix = SimpleDateFormat("'SH'-yyyyMMdd-", Locale.US).format(Date())
            val latest = orderRepository.getLatestOrderWithPrefix(datePrefix)
            val suffix = if (latest != null) {
                val parts = latest.id.split("-")
                val num = parts.lastOrNull()?.toIntOrNull() ?: 0
                String.format(Locale.US, "%04d", num + 1)
            } else {
                "0001"
            }
            val newOrderId = "$datePrefix$suffix"

            _pendingOrder.value = SavedOrder(
                id = newOrderId,
                customerName = preferencesManager.customerName,
                phone = preferencesManager.phone,
                city = preferencesManager.city,
                address = preferencesManager.address,
                generalNotes = preferencesManager.generalNotes.ifBlank { null },
                itemsCount = items.size,
                totalQuantity = totalQuantity,
                estimatedTotal = estimatedTotal,
                itemsJson = serializeCartItemsList(items)
            )
        }

        return sb.toString()
    }

    // Triggered when client confirms WhatsApp was successfully sent
    fun confirmWhatsAppSent() {
        val pending = _pendingOrder.value ?: return
        viewModelScope.launch {
            orderRepository.insert(pending)
            cartRepository.clearCart() // empty local shopping cart!
            _pendingOrder.value = null
        }
    }

    fun dismissPendingOrder() {
        _pendingOrder.value = null
    }

    fun restoreOrderToCart(order: SavedOrder) {
        viewModelScope.launch {
            val orderItems = deserializeCartItemsList(order.itemsJson)
            for (item in orderItems) {
                // Re-insert into current shopping cart with new timestamp
                val duplicated = item.copy(id = UUID.randomUUID().toString(), createdAt = System.currentTimeMillis())
                cartRepository.insert(duplicated)
            }
        }
    }

    fun generateWhatsAppTextForOrder(order: SavedOrder): String {
        val items = deserializeCartItemsList(order.itemsJson)
        val sb = StringBuilder()
        sb.append("مرحبًا، أريد إعادة إرسال طلبي رقم *${order.id}* 🛍️\n\n")
        sb.append("👤 بيانات العميل:\n")
        sb.append("الاسم: ${order.customerName}\n")
        sb.append("رقم الهاتف: ${order.phone}\n")
        sb.append("المدينة: ${order.city}\n")
        sb.append("المنطقة أو العنوان: ${order.address}\n")
        if (!order.generalNotes.isNullOrEmpty()) {
            sb.append("ملاحظات العميل: ${order.generalNotes}\n")
        }
        sb.append("\n🛒 المنتجات المطلوبة:\n")
        sb.append("━━━━━━━━━━━━━━\n")
        items.forEachIndexed { index, item ->
            sb.append("المنتج رقم ${index + 1}\n")
            if (!item.productName.isNullOrEmpty()) {
                sb.append("الاسم: ${item.productName}\n")
            }
            sb.append("الرابط: ${item.productUrl}\n")
            sb.append("المقاس: ${item.size}\n")
            sb.append("اللون: ${item.color}\n")
            sb.append("الكمية: ${item.quantity}\n")
            if (item.displayedPrice != null) {
                sb.append("السعر الظاهر: ${item.displayedPrice} $ \n")
            }
            if (!item.notes.isNullOrEmpty()) {
                sb.append("ملاحظات: ${item.notes}\n")
            }
            sb.append("━━━━━━━━━━━━━━\n")
        }

        sb.append("\n📊 ملخص الطلب:\n")
        sb.append("عدد المنتجات المختلفة: ${order.itemsCount}\n")
        sb.append("إجمالي عدد القطع: ${order.totalQuantity}\n")
        sb.append("الإجمالي التقريبي: ${order.estimatedTotal} $\n")
        sb.append("\n*الإجمالي تقريبي وقد يتغير حسب سعر شي إن وقت تنفيذ الطلب.*\n")

        return sb.toString()
    }

    // Helper to launch WhatsApp on Android safely
    fun sendMsgToWhatsApp(context: Context, text: String): Boolean {
        return try {
            val encodedText = Uri.encode(text)
            val phoneNum = preferencesManager.whatsappNumber.filter { it.isDigit() }
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://wa.me/$phoneNum?text=$encodedText")
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            // Open fallback web browser link
            try {
                val encodedText = Uri.encode(text)
                val phoneNum = preferencesManager.whatsappNumber.filter { it.isDigit() }
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://web.whatsapp.com/send?phone=$phoneNum&text=$encodedText")
                )
                context.startActivity(intent)
                true
            } catch (fallbackEx: Exception) {
                fallbackEx.printStackTrace()
                Toast.makeText(context, "لم يتم العثور على تطبيق واتساب وجاري النسخ للطلب", Toast.LENGTH_LONG).show()
                false
            }
        }
    }

    // Clear all local database items and sharedpref
    fun deleteAllLocalData() {
        viewModelScope.launch {
            cartRepository.clearCart()
            // Delete all historical saved orders:
            val orders = savedOrders.value
            for (order in orders) {
                orderRepository.deleteById(order.id)
            }
            preferencesManager.clearAll()
        }
    }
}
