package com.mohamed.fileflow

import android.content.Intent
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.widget.VideoView
import android.widget.MediaController
import android.content.ClipData
import android.content.ContentUris
import android.provider.MediaStore
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFramePercent
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private data class FileCategory(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tint: Color
)

private val categories = listOf(
    FileCategory("study", "الدراسة", "المواد والمراجع والمحاضرات", "تنظيم هرمي: مادة دراسية ثم ملفات المادة، مع تجميع المراجع والمحاضرات والملخصات.", Icons.Rounded.School, Color(0xFF8B7CF6)),
    FileCategory("images", "الصور", "الصور واللقطات", "تجميع الصور حسب النوع والتاريخ والمجلد، مع عرض المعاينات بعد منح الوصول.", Icons.Rounded.Image, Color(0xFF58A6E7)),
    FileCategory("videos", "الفيديوهات", "المقاطع والتسجيلات المرئية", "عرض ملفات الفيديو وبياناتها الأساسية ومددها وأحجامها عند توفر البيانات.", Icons.Rounded.PlayCircle, Color(0xFFE879A8)),
    FileCategory("audio", "الصوتيات", "الموسيقى والتسجيلات", "تنظيم الصوتيات باستخدام بيانات الوسائط المتاحة مثل الفنان والألبوم والمدة.", Icons.Rounded.MusicNote, Color(0xFFE7A24E)),
    FileCategory("documents", "المستندات", "PDF وWord وExcel والنصوص", "المستندات المصنفة حسب MIME type أو امتداد الملف.", Icons.Rounded.Description, Color(0xFF5C9DE8)),
    FileCategory("archives", "الملفات المضغوطة", "ZIP وRAR و7Z وغيرها", "حزم وأرشيفات الملفات المعروفة من الامتداد أو نوع MIME.", Icons.Rounded.FolderZip, Color(0xFFB18AE8)),
    FileCategory("code", "ملفات البرمجة", "المصدر والإعدادات والبيانات", "ملفات الشيفرة والتهيئة المعروفة من امتداداتها.", Icons.Rounded.Code, Color(0xFF51B68A)),
    FileCategory("installers", "ملفات تثبيت التطبيقات", "APK وAAB والحزم", "ملفات تثبيت أو حزم تطبيقات Android.", Icons.Rounded.Android, Color(0xFF4EB8C5)),
    FileCategory("projects", "مشاريع البرمجة", "المشاريع وملفات المصدر", "تنظيم هرمي: المشروع ثم مجلداته وملفاته، مع تصنيف ملفات المصدر حسب الامتداد.", Icons.Rounded.Code, Color(0xFF51B68A)),
    FileCategory("apps", "التطبيقات", "التطبيقات المثبتة", "عرض التطبيقات المثبتة ومعلومات الحزم التي يسمح Android بالوصول إليها.", Icons.Rounded.Apps, Color(0xFF4EB8C5)),
    FileCategory("empty", "الملفات الفارغة", "ملفات بحجم صفر", "إظهار الملفات التي يبلغ حجمها صفرًا، دون حذفها أو تعديلها تلقائيًا.", Icons.Rounded.InsertDriveFile, Color(0xFF9299A8)),
    FileCategory("other", "أخرى", "ملفات لم يتم تصنيفها بعد", "الملفات التي لا تتوفر أدلة كافية لتصنيفها ستبقى هنا بدل التخمين.", Icons.Rounded.Folder, Color(0xFF9299A8))
)

private val ink = Color(0xFF172033)
private val muted = Color(0xFF788397)
private val canvas = Color(0xFFF4F6FA)
private val brand = Color(0xFF8B5CF6)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FileFlowApp() }
    }
}

@Composable
private fun FileFlowApp() {
    var selected by rememberSaveable { mutableStateOf("home") }
    val history = remember { mutableStateListOf<String>() }
    var query by rememberSaveable { mutableStateOf("") }
    var dark by rememberSaveable { mutableStateOf(true) }
    var gridView by rememberSaveable { mutableStateOf(true) }
    var sortByName by rememberSaveable { mutableStateOf(true) }

    fun navigate(target: String) {
        if (target != selected) { history.add(selected); selected = target }
    }
    fun goBack() {
        selected = if (history.isNotEmpty()) history.removeAt(history.lastIndex) else "home"
    }
    BackHandler(enabled = selected != "home") { goBack() }

    val scheme = if (dark) darkColorScheme(
        primary = Color(0xFF9D70FF), secondary = Color(0xFF31D6F2), tertiary = Color(0xFFE879F9),
        background = Color(0xFF090B1B), surface = Color(0xFF151832),
        onBackground = Color(0xFFF4F1FF), onSurface = Color(0xFFF4F1FF),
        surfaceVariant = Color(0xFF24264A), onSurfaceVariant = Color(0xFFC4C4E3)
    ) else lightColorScheme(
        primary = Color(0xFF7045D8), secondary = Color(0xFF168EA8),
        background = Color(0xFFF5F2FF), surface = Color.White,
        onBackground = ink, onSurface = ink,
        surfaceVariant = Color(0xFFECE7FA), onSurfaceVariant = Color(0xFF596579)
    )

    MaterialTheme(colorScheme = scheme) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    val destinations = listOf(
                        Triple("home", "الرئيسية", Icons.Rounded.Home),
                        Triple("all", "الأقسام", Icons.Rounded.FolderOpen),
                        Triple("recent", "الأخيرة", Icons.Rounded.History),
                        Triple("settings", "الإعدادات", Icons.Rounded.Settings)
                    )
                    destinations.forEach { (id, label, icon) ->
                        NavigationBarItem(
                            selected = selected == id || (id == "all" && categories.any { it.id == selected }),
                            onClick = { history.clear(); selected = id },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                TopHeader(
                    query = query, onQuery = { query = it }, onSearch = { navigate("all") },
                    onSettings = { navigate("settings") }, dark = dark, onTheme = { dark = !dark }
                )
                when (selected) {
                    "home" -> HomeScreen(onNavigate = { navigate(it) }, onCategory = { navigate(it) })
                    "settings" -> SettingsScreen(dark = dark, onTheme = { dark = it })
                    else -> ArchiveScreen(
                        selected = selected, query = query, gridView = gridView, sortByName = sortByName,
                        onCategory = { navigate(it) }, onBack = { goBack() },
                        onGridToggle = { gridView = !gridView }, onSortToggle = { sortByName = !sortByName }
                    )
                }
            }
        }
    }
}

@Composable
private fun TopHeader(
    query: String,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    dark: Boolean,
    onTheme: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
            .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(13.dp), color = brand) {
                Icon(Icons.Rounded.FolderCopy, null, tint = Color.White, modifier = Modifier.padding(9.dp).size(24.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("FileFlow", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("مساحة ملفاتك، بوضوح", fontSize = 11.sp, color = muted)
            }
            IconButton(onClick = onTheme) {
                Icon(if (dark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode, contentDescription = "تغيير المظهر")
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = "الإعدادات")
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("ابحث في الأقسام والملفات المفهرسة...", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { onQuery("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "مسح البحث")
                    }
                }
            },
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() })
        )
    }
}

@Composable
private fun HomeScreen(onNavigate: (String) -> Unit, onCategory: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var counts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    LaunchedEffect(Unit) {
        counts = withContext(Dispatchers.IO) {
            val database = FileIndexDatabase(context)
            try {
                val all = database.allFiles()
                categories.associate { category ->
                    category.id to if (category.id == "empty") all.count { it.sizeBytes == 0L }
                    else all.count { it.category == category.id }
                }
            } finally {
                database.close()
            }
        }
    }

    val tileCategories = categories.filter {
        it.id in setOf("images", "videos", "audio", "documents", "archives", "empty")
    }
    val wideCategories = categories.filter { it.id in setOf("study", "projects", "apps", "other", "installers", "code") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(25.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    Modifier.fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF29145D), Color(0xFF171A42), Color(0xFF073B59))
                            )
                        )
                        .padding(horizontal = 18.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("FILEFLOW", color = Color(0xFFC5A9FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(5.dp))
                        Text("كل ملفاتك.. بنظام أذكى", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("تصنيف بالاسم • فئات متداخلة • معاينات", color = Color(0xFFD1D0F0), fontSize = 10.sp)
                    }
                    Surface(shape = RoundedCornerShape(19.dp), color = Color(0xFF8050E8)) {
                        Icon(Icons.Rounded.FolderCopy, null, tint = Color.White, modifier = Modifier.padding(13.dp).size(38.dp))
                    }
                }
            }
        }
        item { StorageAccessPanel() }
        item { DeviceStorageCard() }
        item {
            Text("التصنيف الذكي", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("تنظيم حسب معنى اسم الملف ومادته، وليس امتداده فقط", color = muted, fontSize = 11.sp)
        }
        items(wideCategories) { category ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onCategory(category.id) },
                shape = RoundedCornerShape(17.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(shape = RoundedCornerShape(12.dp), color = category.tint.copy(alpha = 0.16f)) {
                        Icon(category.icon, null, tint = category.tint, modifier = Modifier.padding(10.dp).size(23.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(category.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(category.subtitle, color = muted, fontSize = 10.sp, maxLines = 1)
                    }
                    counts[category.id]?.let { Text("$it", color = category.tint, fontWeight = FontWeight.Bold) }
                    Icon(Icons.Rounded.ChevronLeft, null, tint = muted)
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("الأقسام والملفات", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { onNavigate("all") }) { Text("عرض الكل") }
            }
        }
        items(tileCategories.chunked(3)) { rowCategories ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowCategories.forEach { category ->
                    CategoryCard(category = category, modifier = Modifier.weight(1f), count = counts[category.id]) {
                        onCategory(category.id)
                    }
                }
                repeat(3 - rowCategories.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(17.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Shield, null, tint = Color(0xFF39A77B), modifier = Modifier.size(27.dp))
                    Spacer(Modifier.height(7.dp))
                    Text("خصوصيتك أولاً", fontWeight = FontWeight.Bold)
                    Text("الفهرسة محليًا، والملفات الأصلية لا تُنقل ولا تُعدّل أثناء الفهرسة.", color = muted, fontSize = 11.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(category: FileCategory, modifier: Modifier = Modifier, count: Int? = null, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            Modifier.fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(category.tint.copy(alpha = 0.22f), MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface)
                    ),
                    RoundedCornerShape(20.dp)
                )
                .padding(15.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(13.dp), color = category.tint.copy(alpha = 0.18f)) {
                    Icon(category.icon, contentDescription = null, tint = category.tint, modifier = Modifier.padding(10.dp).size(25.dp))
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Rounded.ChevronLeft, contentDescription = null, tint = category.tint.copy(alpha = 0.9f))
            }
            Spacer(Modifier.height(13.dp))
            Text(category.title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            count?.let { Text("$it ملف", fontSize = 10.sp, color = category.tint, fontWeight = FontWeight.Bold) }
            Text(category.subtitle, fontSize = 9.sp, color = muted, maxLines = 2, minLines = 2)
        }
    }
}

@Composable
private fun ArchiveScreen(
    selected: String,
    query: String,
    gridView: Boolean,
    sortByName: Boolean,
    onCategory: (String) -> Unit,
    onBack: () -> Unit,
    onGridToggle: () -> Unit,
    onSortToggle: () -> Unit
) {
    val category = categories.firstOrNull { it.id == selected }
    var selectedSubject by remember(selected) { mutableStateOf<String?>(null) }
    var selectedExtension by remember(selected, selectedSubject) { mutableStateOf<String?>(null) }
    BackHandler(enabled = selectedExtension != null || selectedSubject != null) {
        if (selectedExtension != null) selectedExtension = null else selectedSubject = null
    }
    val title = when (selected) {
        "all" -> "كل الأقسام"
        "recent" -> "الأخيرة"
        "favorites" -> "المفضلة"
        "duplicates" -> "المكررات"
        else -> category?.title ?: "الأرشيف"
    }
    val filtered = categories.filter {
        query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) ||
            it.subtitle.contains(query.trim(), ignoreCase = true)
    }.let { if (sortByName) it.sortedBy(FileCategory::title) else it }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                when {
                    selectedExtension != null -> selectedExtension = null
                    selectedSubject != null -> selectedSubject = null
                    else -> onBack()
                }
            }) { Icon(Icons.Rounded.ArrowBack, contentDescription = "رجوع") }
            Text("الأرشيف", color = brand, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            if (category != null || selected == "all") {
                Text("  /  ", color = muted)
                Text(category?.title ?: title, color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp)
            }
            selectedSubject?.let {
                Text("  /  ", color = muted)
                Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            }
            selectedExtension?.let {
                Text("  /  ", color = muted)
                Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            }
        }

        when {
            selected == "all" && query.isNotBlank() -> IndexedSearchResults(query, onCategory)
            selected == "all" -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("الأقسام المتاحة", color = muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onSortToggle) {
                        Icon(Icons.Rounded.SortByAlpha, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(if (sortByName) "أبجدي" else "الترتيب الأصلي")
                    }
                    IconButton(onClick = onGridToggle) {
                        Icon(if (gridView) Icons.Rounded.ViewList else Icons.Rounded.GridView, contentDescription = "تغيير العرض")
                    }
                }
                if (filtered.isEmpty()) {
                    EmptyState(Icons.Rounded.SearchOff, "لا توجد أقسام مطابقة", "جرّب كلمة بحث مختلفة.")
                } else if (gridView) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 3.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered.chunked(2)) { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                pair.forEach { CategoryCard(it, Modifier.weight(1f)) { onCategory(it.id) } }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp)) {
                        items(filtered) { item ->
                            ListItem(
                                headlineContent = { Text(item.title, fontWeight = FontWeight.SemiBold) },
                                supportingContent = { Text(item.subtitle, color = muted, fontSize = 11.sp) },
                                leadingContent = {
                                    Surface(shape = RoundedCornerShape(10.dp), color = item.tint.copy(alpha = 0.13f)) {
                                        Icon(item.icon, null, tint = item.tint, modifier = Modifier.padding(8.dp))
                                    }
                                },
                                trailingContent = { Icon(Icons.Rounded.ChevronLeft, null, tint = muted) },
                                modifier = Modifier.clickable { onCategory(item.id) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
            selected == "recent" || selected == "favorites" || selected == "duplicates" -> {
                val icon = when (selected) {
                    "recent" -> Icons.Rounded.History
                    "favorites" -> Icons.Rounded.StarBorder
                    else -> Icons.Rounded.ContentCopy
                }
                EmptyState(icon, "القسم غير مربوط بعد", "يلزم تنفيذ سجل الاستخدام والمفضلة وفحص بصمات التكرار قبل عرض نتائج حقيقية.")
            }
            selected in listOf("study", "projects", "images", "videos", "audio", "documents", "archives", "code", "installers", "other", "empty") -> {
                IndexedFilesContent(
                    selected = selected,
                    query = query,
                    gridView = gridView,
                    onGridToggle = onGridToggle,
                    selectedSubject = selectedSubject,
                    onSubjectChange = { selectedSubject = it; selectedExtension = null },
                    selectedExtension = selectedExtension,
                    onExtensionChange = { selectedExtension = it }
                )
            }
            else -> EmptyState(category?.icon ?: Icons.Rounded.FolderOpen, "هذا القسم لم يُربط بعد", "اختر مجلدًا من الرئيسية لفهرسة محتوياته.")
        }
    }
}

@Composable
private fun IndexedSearchResults(query: String, onCategory: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var files by remember(query) { mutableStateOf<List<IndexedFile>>(emptyList()) }
    val matchingCategories = remember(query) {
        categories.filter { category ->
            category.title.contains(query.trim(), ignoreCase = true) ||
                category.subtitle.contains(query.trim(), ignoreCase = true) ||
                category.description.contains(query.trim(), ignoreCase = true)
        }
    }

    LaunchedEffect(query) {
        files = withContext(Dispatchers.IO) {
            val database = FileIndexDatabase(context)
            try {
                database.allFiles().filter { file ->
                    file.name.contains(query.trim(), ignoreCase = true) ||
                        (file.mimeType?.contains(query.trim(), ignoreCase = true) == true)
                }.sortedBy { it.name.lowercase() }
            } finally {
                database.close()
            }
        }
    }

    fun openFile(file: IndexedFile) {
        try {
            val uri = Uri.parse(file.uri)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, file.mimeType ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (error: Exception) {
            android.widget.Toast.makeText(
                context,
                "تعذر فتح الملف: ${error.localizedMessage ?: "لا يوجد تطبيق مناسب"}",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    if (files.isEmpty() && matchingCategories.isEmpty()) {
        EmptyState(Icons.Rounded.SearchOff, "لا توجد نتائج مطابقة", "ابحث باسم قسم أو اسم ملف أو نوعه.")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
        ) {
            if (matchingCategories.isNotEmpty()) {
                item {
                    Text(
                        "الأقسام",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(matchingCategories, key = { "category:${it.id}" }) { category ->
                    ListItem(
                        headlineContent = { Text(category.title, fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(category.subtitle, color = muted, fontSize = 12.sp) },
                        leadingContent = { Icon(category.icon, null, tint = category.tint) },
                        trailingContent = { Icon(Icons.Rounded.ChevronLeft, null, tint = muted) },
                        modifier = Modifier.clickable { onCategory(category.id) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            if (files.isNotEmpty()) {
                item {
                    Text(
                        "الملفات",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(files, key = { it.uri }) { file ->
                    ListItem(
                        headlineContent = { Text(file.name, maxLines = 1) },
                        supportingContent = {
                            Text(
                                "${file.category} • ${file.mimeType ?: "نوع غير معروف"} • ${formatBytes(file.sizeBytes)}",
                                color = muted,
                                fontSize = 11.sp
                            )
                        },
                        leadingContent = { Icon(fileCategoryIcon(file.category), null, tint = brand) },
                        modifier = Modifier.clickable { openFile(file) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

private fun fileCategoryIcon(category: String): androidx.compose.ui.graphics.vector.ImageVector =
    when (category) {
        "images" -> Icons.Rounded.Image
        "videos" -> Icons.Rounded.PlayCircle
        "audio" -> Icons.Rounded.MusicNote
        "documents" -> Icons.Rounded.Description
        "archives" -> Icons.Rounded.FolderZip
        "code" -> Icons.Rounded.Code
        "installers" -> Icons.Rounded.Android
        else -> Icons.Rounded.InsertDriveFile
    }

private fun mediaPreviewUri(file: IndexedFile): Uri {
    val original = Uri.parse(file.uri)
    if (original.authority != "media" || !original.pathSegments.contains("file")) return original
    val mediaId = original.lastPathSegment?.toLongOrNull() ?: return original
    return when (file.category) {
        "images" -> ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaId)
        "videos" -> ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, mediaId)
        else -> original
    }
}

@Composable
private fun MediaThumbnail(file: IndexedFile, selected: String, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(mediaPreviewUri(file))
            .crossfade(true)
            .apply {
                if (selected == "videos") {
                    videoFramePercent(0.1)
                    // Content URIs may not expose a filename extension or trustworthy MIME.
                    decoderFactory { result, options, _ ->
                        VideoFrameDecoder(result.source, options)
                    }
                }
            }
            .build(),
        contentDescription = file.name,
        placeholder = painterResource(R.drawable.ic_media_placeholder),
        error = painterResource(R.drawable.ic_media_placeholder),
        fallback = painterResource(R.drawable.ic_media_placeholder),
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    )
}

private fun extensionLabel(file: IndexedFile): String {
    val extension = file.name.substringAfterLast('.', "").trim().lowercase()
    return if (extension.isBlank() || extension == file.name.lowercase()) {
        "بدون امتداد"
    } else {
        extension.removePrefix(".").uppercase(Locale.ROOT)
    }
}

private fun studySubjectLabel(file: IndexedFile): String =
    FileClassifier.studySubject(file.name) ?: "مواد دراسية أخرى"

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IndexedFilesContent(
    selected: String,
    query: String,
    gridView: Boolean,
    onGridToggle: () -> Unit,
    selectedSubject: String?,
    onSubjectChange: (String?) -> Unit,
    selectedExtension: String?,
    onExtensionChange: (String?) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var files by remember(selected, query) { mutableStateOf<List<IndexedFile>>(emptyList()) }
    var detailFile by remember(selected) { mutableStateOf<IndexedFile?>(null) }
    var previewFile by remember(selected) { mutableStateOf<IndexedFile?>(null) }
    var sortMode by remember(selected) { mutableStateOf("name_asc") }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var selecting by remember(selected) { mutableStateOf(false) }
    val selectedUris = remember(selected) { mutableStateListOf<String>() }
    var refreshKey by remember(selected) { mutableIntStateOf(0) }
    var actionStatus by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<IndexedFile?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var transferAsMove by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    var pendingFolderTree by remember { mutableStateOf<Uri?>(null) }
    var showRenameFolderDialog by remember { mutableStateOf(false) }
    var pendingRenameFolderTree by remember { mutableStateOf<Uri?>(null) }
    var folderRenameValue by remember { mutableStateOf("") }

    BackHandler(enabled = previewFile != null || detailFile != null || selecting) {
        when {
            previewFile != null -> previewFile = null
            detailFile != null -> detailFile = null
            else -> { selecting = false; selectedUris.clear() }
        }
    }

    LaunchedEffect(selected, query, refreshKey) {
        files = withContext(Dispatchers.IO) {
            val database = FileIndexDatabase(context)
            try {
                val all = database.allFiles()
                val categoryFiles = if (selected == "empty") all.filter { it.sizeBytes == 0L }
                else all.filter { it.category == selected }
                categoryFiles.filter { file ->
                    query.isBlank() || file.name.contains(query.trim(), ignoreCase = true) ||
                        (file.mimeType?.contains(query.trim(), ignoreCase = true) == true)
                }
            } finally {
                database.close()
            }
        }
    }

    val subjects = remember(files, selected) {
        if (selected == "study") files.map(::studySubjectLabel).distinct().sorted() else emptyList()
    }
    val subjectFiles = remember(files, selectedSubject) {
        if (selected == "study" && selectedSubject != null) files.filter { studySubjectLabel(it) == selectedSubject }
        else files
    }
    val extensionSource = if (selected == "study") subjectFiles else files
    val extensions = remember(extensionSource) { extensionSource.map(::extensionLabel).distinct().sorted() }
    val extensionFiles = remember(extensionSource, selectedExtension) {
        if (selectedExtension == null) extensionSource else extensionSource.filter { extensionLabel(it) == selectedExtension }
    }
    val visibleFiles = remember(extensionFiles, sortMode) {
        when (sortMode) {
            "name_desc" -> extensionFiles.sortedByDescending { it.name.lowercase(Locale.ROOT) }
            "size_desc" -> extensionFiles.sortedByDescending { it.sizeBytes }
            "size_asc" -> extensionFiles.sortedBy { it.sizeBytes }
            "date_asc" -> extensionFiles.sortedBy { it.modifiedSeconds }
            "date_desc" -> extensionFiles.sortedByDescending { it.modifiedSeconds }
            else -> extensionFiles.sortedBy { it.name.lowercase(Locale.ROOT) }
        }
    }
    val showingSubjectTiles = selected == "study" && selectedSubject == null
    val showingExtensionTiles = selectedExtension == null && (selected != "study" || selectedSubject != null)
    val groupCount = if (showingSubjectTiles) subjects.size else if (showingExtensionTiles) extensions.size else visibleFiles.size
    val selectedFiles = files.filter { it.uri in selectedUris }
    val sortLabels = listOf(
        "name_asc" to "الاسم: أ إلى ي",
        "name_desc" to "الاسم: ي إلى أ",
        "date_desc" to "الأحدث أولاً",
        "date_asc" to "الأقدم أولاً",
        "size_desc" to "الأكبر حجماً",
        "size_asc" to "الأصغر حجماً"
    )

    fun toggleSelection(file: IndexedFile) {
        if (file.uri in selectedUris) selectedUris.remove(file.uri) else selectedUris.add(file.uri)
        if (selectedUris.isEmpty()) selecting = false
    }

    fun openFile(file: IndexedFile) {
        if (file.category == "images" || file.category == "videos") previewFile = file
        else openFileExternally(context, file)
    }

    val transferPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
        if (treeUri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(treeUri, flags) }
            val sources = files.filter { it.uri in selectedUris }
            val moving = transferAsMove
            scope.launch {
                val results = withContext(Dispatchers.IO) {
                    sources.map { FileOperations.transferToTree(context, it, treeUri, moving) }
                }
                withContext(Dispatchers.IO) {
                    val db = FileIndexDatabase(context)
                    try {
                        if (moving) {
                            val successfullyMovedSourceUris = results.mapIndexedNotNull { index, result ->
                                if (result.success) sources.getOrNull(index)?.uri else null
                            }
                            db.deleteFiles(successfullyMovedSourceUris)
                        }
                        db.upsertFiles(results.filter { it.success }.mapNotNull { it.file })
                    } finally {
                        db.close()
                    }
                }
                val succeeded = results.count { it.success }
                actionStatus = if (moving) "تم نقل $succeeded من ${sources.size} ملف." else "تم نسخ $succeeded من ${sources.size} ملف."
                results.firstOrNull { !it.success }?.let { actionStatus = actionStatus + " " + it.message }
                selectedUris.clear()
                selecting = false
                refreshKey++
            }
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
        if (treeUri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(treeUri, flags) }
            pendingFolderTree = treeUri
            showCreateFolderDialog = true
        }
    }

    val folderRenamePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
        if (treeUri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(treeUri, flags) }
            pendingRenameFolderTree = treeUri
            folderRenameValue = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)?.name.orEmpty()
            showRenameFolderDialog = true
        }
    }

    if (previewFile != null) {
        MediaPreviewScreen(file = previewFile!!, onBack = { previewFile = null })
        return
    }
    if (detailFile != null) {
        FileDetailPanel(
            file = detailFile!!,
            onBack = { detailFile = null },
            onOpen = { openFileExternally(context, detailFile!!) },
            onShare = { shareIndexedFile(context, detailFile!!) },
            onPreview = { previewFile = detailFile }
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        if (selecting) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 7.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("تحديد: ${selectedUris.size}", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = {
                    if (selectedUris.size == visibleFiles.size) selectedUris.clear()
                    else {
                        selectedUris.clear()
                        selectedUris.addAll(visibleFiles.map { it.uri })
                    }
                }) {
                    Text(if (visibleFiles.isNotEmpty() && selectedUris.size == visibleFiles.size) "إلغاء الكل" else "تحديد الكل", fontSize = 10.sp)
                }
                if (selectedUris.size == 1) {
                    TextButton(onClick = {
                        renameTarget = selectedFiles.firstOrNull()
                        renameValue = renameTarget?.name.orEmpty()
                        showRenameDialog = renameTarget != null
                    }) {
                        Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(16.dp))
                        Text("تسمية", fontSize = 10.sp)
                    }
                }
                TextButton(onClick = {
                    val uris = selectedFiles.map { Uri.parse(it.uri) }
                    if (uris.isNotEmpty()) {
                        runCatching {
                            val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                type = "*/*"
                                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                clipData = ClipData.newUri(context.contentResolver, "FileFlow files", uris.first()).apply {
                                    uris.drop(1).forEach { addItem(ClipData.Item(it)) }
                                }
                            }
                            context.startActivity(Intent.createChooser(send, "مشاركة الملفات"))
                        }.onFailure { actionStatus = "تعذرت مشاركة الملفات المحددة." }
                    }
                }) { Icon(Icons.Rounded.Share, null, modifier = Modifier.size(16.dp)); Text("مشاركة", fontSize = 10.sp) }
                TextButton(onClick = { transferAsMove = false; transferPicker.launch(null) }) {
                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp)); Text("نسخ", fontSize = 10.sp)
                }
                TextButton(onClick = { transferAsMove = true; transferPicker.launch(null) }) {
                    Icon(Icons.Rounded.DriveFileMove, null, modifier = Modifier.size(16.dp)); Text("نقل", fontSize = 10.sp)
                }
                TextButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Text("حذف", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = { selectedUris.clear(); selecting = false }) { Text("إلغاء", fontSize = 10.sp) }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp, top = 0.dp, bottom = 0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("$groupCount ${if (showingSubjectTiles) "مادة" else if (showingExtensionTiles) "امتداد" else "ملف"}", color = muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { folderPicker.launch(null) }) {
                    Icon(Icons.Rounded.CreateNewFolder, null, modifier = Modifier.size(16.dp))
                    Text("مجلد جديد", fontSize = 10.sp)
                }
                TextButton(onClick = { folderRenamePicker.launch(null) }) {
                    Icon(Icons.Rounded.Folder, null, modifier = Modifier.size(16.dp))
                    Text("تسمية مجلد", fontSize = 10.sp)
                }
                TextButton(onClick = { selecting = true }) {
                    Icon(Icons.Rounded.Checklist, null, modifier = Modifier.size(16.dp))
                    Text("تحديد", fontSize = 10.sp)
                }
                IconButton(onClick = onGridToggle, modifier = Modifier.size(34.dp)) {
                    Icon(if (gridView) Icons.Rounded.ViewList else Icons.Rounded.GridView, contentDescription = "تغيير طريقة العرض", modifier = Modifier.size(19.dp))
                }
                Box {
                    IconButton(onClick = { sortMenuExpanded = true }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Rounded.Sort, contentDescription = "الفرز", modifier = Modifier.size(19.dp))
                    }
                    DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                        sortLabels.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { sortMode = key; sortMenuExpanded = false },
                                leadingIcon = { if (sortMode == key) Icon(Icons.Rounded.Check, null) }
                            )
                        }
                    }
                }
            }
        }

        actionStatus?.let {
            Text(it, color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 1.dp))
        }

        when {
            files.isEmpty() -> EmptyState(
                Icons.Rounded.FolderOpen,
                "لا توجد ملفات مفهرسة في هذا القسم",
                "أعد فهرسة الملفات بعد منح صلاحية الوصول، ثم افتح القسم مرة أخرى."
            )
            showingSubjectTiles || showingExtensionTiles -> {
                val groups = if (showingSubjectTiles) subjects else extensions
                if (groups.isEmpty()) {
                    EmptyState(Icons.Rounded.FolderOpen, "لا توجد تصنيفات فرعية", "لم يعثر الفهرس على ملفات يمكن عرضها هنا.")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        gridItems(groups, key = { it }) { group ->
                            val count = if (showingSubjectTiles) files.count { studySubjectLabel(it) == group }
                            else extensionSource.count { extensionLabel(it) == group }
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (showingSubjectTiles) onSubjectChange(group) else onExtensionChange(group)
                                },
                                shape = RoundedCornerShape(15.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                    Icon(
                                        if (showingSubjectTiles) Icons.Rounded.School else Icons.Rounded.InsertDriveFile,
                                        contentDescription = null,
                                        tint = if (showingSubjectTiles) Color(0xFFB28AFF) else Color(0xFF53C6F0),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.height(7.dp))
                                    Text(group, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2)
                                    Text("$count ملف", color = muted, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
            gridView && (selected == "images" || selected == "videos") -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 7.dp, end = 7.dp, top = 2.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    gridItems(visibleFiles, key = { it.uri }) { file ->
                        val isChosen = file.uri in selectedUris
                        Column(
                            modifier = Modifier.fillMaxWidth().combinedClickable(
                                onClick = { if (selecting) toggleSelection(file) else openFile(file) },
                                onLongClick = { selecting = true; if (!isChosen) selectedUris.add(file.uri) }
                            ),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box {
                                MediaThumbnail(file, selected, Modifier.fillMaxWidth().aspectRatio(1f))
                                if (isChosen) Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF8B5CF6), modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
                            }
                            Text(file.name, maxLines = 1, fontSize = 10.sp, color = MaterialTheme.colorScheme.onBackground)
                            Text(formatBytes(file.sizeBytes), maxLines = 1, fontSize = 9.sp, color = muted)
                        }
                    }
                }
            }
            gridView -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 148.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    gridItems(visibleFiles, key = { it.uri }) { file ->
                        val isChosen = file.uri in selectedUris
                        Card(
                            modifier = Modifier.fillMaxWidth().combinedClickable(
                                onClick = { if (selecting) toggleSelection(file) else openFile(file) },
                                onLongClick = { selecting = true; if (!isChosen) selectedUris.add(file.uri) }
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isChosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(13.dp)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(9.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(fileCategoryIcon(file.category), null, tint = brand, modifier = Modifier.size(25.dp))
                                    Spacer(Modifier.weight(1f))
                                    IconButton(onClick = { detailFile = file }, modifier = Modifier.size(29.dp)) {
                                        Icon(Icons.Rounded.Info, contentDescription = "تفاصيل الملف", tint = muted, modifier = Modifier.size(17.dp))
                                    }
                                    if (isChosen) Icon(Icons.Rounded.CheckCircle, null, tint = brand, modifier = Modifier.size(17.dp))
                                }
                                Spacer(Modifier.height(5.dp))
                                Text(file.name, maxLines = 2, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Text("${extensionLabel(file)} • ${formatBytes(file.sizeBytes)}", maxLines = 1, fontSize = 9.sp, color = muted)
                            }
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    items(visibleFiles, key = { it.uri }) { file ->
                        val isChosen = file.uri in selectedUris
                        ListItem(
                            headlineContent = { Text(file.name, maxLines = 2) },
                            supportingContent = {
                                Text("${extensionLabel(file)} • ${file.mimeType ?: "نوع غير معروف"} • ${formatBytes(file.sizeBytes)}", color = muted, fontSize = 10.sp)
                            },
                            leadingContent = { Icon(fileCategoryIcon(file.category), null, tint = if (isChosen) MaterialTheme.colorScheme.primary else brand) },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { detailFile = file }) { Icon(Icons.Rounded.Info, contentDescription = "تفاصيل الملف") }
                                    if (isChosen) Icon(Icons.Rounded.CheckCircle, null, tint = brand)
                                }
                            },
                            modifier = Modifier.combinedClickable(
                                onClick = { if (selecting) toggleSelection(file) else openFile(file) },
                                onLongClick = { selecting = true; if (!isChosen) selectedUris.add(file.uri) }
                            )
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("إعادة تسمية الملف") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    singleLine = true,
                    label = { Text("الاسم الجديد") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = renameTarget
                    showRenameDialog = false
                    if (target != null) scope.launch {
                        val result = withContext(Dispatchers.IO) { FileOperations.rename(context, target, renameValue) }
                        if (result.success && result.file != null) {
                            withContext(Dispatchers.IO) {
                                val db = FileIndexDatabase(context)
                                try {
                                    db.deleteFiles(listOf(target.uri))
                                    db.upsertFiles(listOf(result.file))
                                } finally { db.close() }
                            }
                            actionStatus = result.message
                            refreshKey++
                        } else actionStatus = result.message
                        selectedUris.clear()
                        selecting = false
                    }
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("إلغاء") } }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("حذف الملفات المحددة؟") },
            text = { Text("سيتم حذف ${selectedFiles.size} ملف من التخزين. لا يمكن التراجع عن الحذف.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    val toDelete = selectedFiles
                    scope.launch {
                        val results = withContext(Dispatchers.IO) { toDelete.map { it to FileOperations.delete(context, it) } }
                        withContext(Dispatchers.IO) {
                            val db = FileIndexDatabase(context)
                            try { db.deleteFiles(results.filter { it.second.success }.map { it.first.uri }) }
                            finally { db.close() }
                        }
                        val done = results.count { it.second.success }
                        actionStatus = "تم حذف $done من ${toDelete.size} ملف."
                        results.firstOrNull { !it.second.success }?.let { actionStatus += " ${it.second.message}" }
                        selectedUris.clear()
                        selecting = false
                        refreshKey++
                    }
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("إلغاء") } }
        )
    }

    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("إنشاء مجلد جديد") },
            text = {
                OutlinedTextField(value = folderName, onValueChange = { folderName = it }, singleLine = true, label = { Text("اسم المجلد") })
            },
            confirmButton = {
                TextButton(onClick = {
                    val parent = pendingFolderTree
                    val name = folderName
                    showCreateFolderDialog = false
                    if (parent != null) scope.launch {
                        val result = withContext(Dispatchers.IO) { FileOperations.createFolder(context, parent, name) }
                        actionStatus = result.message
                        folderName = ""
                    }
                }) { Text("إنشاء") }
            },
            dismissButton = { TextButton(onClick = { showCreateFolderDialog = false }) { Text("إلغاء") } }
        )
    }

    if (showRenameFolderDialog) {
        AlertDialog(
            onDismissRequest = { showRenameFolderDialog = false },
            title = { Text("إعادة تسمية المجلد المحدد") },
            text = {
                OutlinedTextField(
                    value = folderRenameValue,
                    onValueChange = { folderRenameValue = it },
                    singleLine = true,
                    label = { Text("اسم المجلد الجديد") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val tree = pendingRenameFolderTree
                    val name = folderRenameValue
                    showRenameFolderDialog = false
                    if (tree != null) scope.launch {
                        val result = withContext(Dispatchers.IO) { FileOperations.renameFolder(context, tree, name) }
                        actionStatus = result.message
                    }
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { showRenameFolderDialog = false }) { Text("إلغاء") } }
        )
    }
}

private fun openFileExternally(context: android.content.Context, file: IndexedFile) {
    runCatching {
        val uri = Uri.parse(file.uri)
        val mime = FileOperations.mimeType(context, file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, file.name, uri)
        }
        context.startActivity(Intent.createChooser(intent, "فتح ${file.name}"))
    }.onFailure { error ->
        android.widget.Toast.makeText(
            context,
            "تعذر فتح الملف. تحقق من الصلاحية أو ثبّت تطبيقًا يدعم هذا النوع. ${error.localizedMessage ?: ""}",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }
}

private fun shareIndexedFile(context: android.content.Context, file: IndexedFile) {
    runCatching {
        val uri = Uri.parse(file.uri)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = FileOperations.mimeType(context, file)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, file.name, uri)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة ${file.name}"))
    }.onFailure {
        android.widget.Toast.makeText(context, "تعذرت مشاركة الملف المحدد.", android.widget.Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun MediaPreviewScreen(file: IndexedFile, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    BackHandler { onBack() }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF111326)).padding(horizontal = 5.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "رجوع", tint = Color.White) }
            Text(file.name, color = Color.White, maxLines = 1, fontWeight = FontWeight.SemiBold)
        }
        if (file.category == "images") {
            AsyncImage(
                model = ImageRequest.Builder(context).data(mediaPreviewUri(file)).crossfade(true).build(),
                contentDescription = file.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        } else {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        val player = this
                        val controls = MediaController(ctx)
                        controls.setAnchorView(player)
                        setMediaController(controls)
                        setVideoURI(mediaPreviewUri(file))
                        setOnPreparedListener { mediaPlayer -> mediaPlayer.start() }
                    }
                },
                modifier = Modifier.fillMaxWidth().weight(1f),
                onRelease = { it.stopPlayback() }
            )
        }
    }
}

@Composable
private fun FileDetailPanel(
    file: IndexedFile,
    onBack: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onPreview: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "رجوع") }
            Column(Modifier.weight(1f)) {
                Text("تفاصيل الملف", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                Text(file.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2)
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(17.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            if (file.category == "images" || file.category == "videos") {
                MediaThumbnail(file, file.category, Modifier.fillMaxWidth().height(175.dp))
            } else {
                Box(
                    Modifier.fillMaxWidth().height(90.dp).background(Brush.linearGradient(listOf(Color(0xFF26164F), Color(0xFF102A46)))),
                    contentAlignment = Alignment.Center
                ) { Icon(fileCategoryIcon(file.category), null, tint = Color(0xFFB99AFF), modifier = Modifier.size(50.dp)) }
            }
            Column(Modifier.fillMaxWidth().padding(11.dp)) {
                Text(file.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                Text("${extensionLabel(file)} • ${formatBytes(file.sizeBytes)}", color = muted, fontSize = 10.sp)
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(15.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Button(onClick = onOpen, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.OpenInNew, null); Spacer(Modifier.width(3.dp)); Text("فتح خارجي")
                    }
                    OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Share, null); Spacer(Modifier.width(3.dp)); Text("مشاركة")
                    }
                }
                if (file.category == "images" || file.category == "videos") {
                    OutlinedButton(onClick = onPreview, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Fullscreen, null); Spacer(Modifier.width(4.dp)); Text("معاينة داخل FileFlow")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DetailInfoRow("النوع", file.mimeType ?: "غير معروف")
                DetailInfoRow("الامتداد", extensionLabel(file))
                DetailInfoRow("الحجم", formatBytes(file.sizeBytes))
                DetailInfoRow(
                    "آخر تعديل",
                    if (file.modifiedSeconds > 0L) java.text.DateFormat.getDateTimeInstance().format(java.util.Date(file.modifiedSeconds * 1000L))
                    else "غير متاح"
                )
            }
        }
    }
}

@Composable
private fun DetailInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(label, color = muted, fontSize = 12.sp, modifier = Modifier.weight(0.35f))
        Text(value, fontSize = 12.sp, modifier = Modifier.weight(0.65f), textAlign = TextAlign.End)
    }
}

@Composable
private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String) {
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = CircleShape, color = brand.copy(alpha = 0.10f)) {
                Icon(icon, null, tint = brand, modifier = Modifier.padding(20.dp).size(34.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(7.dp))
            Text(description, color = muted, fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun SettingsScreen(dark: Boolean, onTheme: (Boolean) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("التفضيلات", color = brand, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("الإعدادات", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (dark) Icons.Rounded.DarkMode else Icons.Rounded.LightMode, null, tint = brand)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("المظهر الداكن", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("تغيير ألوان واجهة التطبيق", color = muted, fontSize = 11.sp)
                    }
                    Switch(checked = dark, onCheckedChange = onTheme)
                }
            }
        }
        item {
            SettingsInfoCard(Icons.Rounded.Shield, "حماية الملفات الأصلية", "سياسة التطبيق: لا نقل ولا تعديل ولا حذف تلقائي للملفات أثناء الفهرسة.")
        }
        item {
            SettingsInfoCard(Icons.Rounded.PhoneAndroid, "صلاحيات الوصول", "يمكنك منح صلاحيات الصور والفيديو والصوت، أو اختيار مجلد محدد يدويًا. يقتصر الوصول على ما تسمح به إصدارات Android ومزود الملفات.")
        }
        item {
            SettingsInfoCard(Icons.Rounded.Info, "حول FileFlow", "الإصدار 1.0.0 • تطبيق Android أصلي • التصنيف والفهرسة على الجهاز.")
        }
    }
}

@Composable
private fun SettingsInfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = brand)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text(description, color = muted, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
    }
}
