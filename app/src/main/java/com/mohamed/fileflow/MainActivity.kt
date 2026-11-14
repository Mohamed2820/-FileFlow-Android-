package com.mohamed.fileflow

import android.content.Intent
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("مساحة منظمة، بدون فوضى", color = brand, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("كل ملفاتك في مكان واحد", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Text("أرشيف واضح يساعدك توصل لملفاتك بسرعة.", color = muted, fontSize = 13.sp)
        }
        item {
            StorageAccessPanel()
        }
        item { DeviceStorageCard() }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("تصفح حسب النوع", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { onNavigate("all") }) { Text("كل الأقسام") }
            }
        }
        items(categories.chunked(2)) { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { category -> CategoryCard(category, Modifier.weight(1f)) { onCategory(category.id) } }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Shield, null, tint = Color(0xFF39A77B), modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("خصوصيتك أولاً", fontWeight = FontWeight.Bold)
                    Text("الفهرسة محليًا، والملفات الأصلية لا تُنقل ولا تُعدّل أثناء الفهرسة.", color = muted, fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(category: FileCategory, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = category.tint.copy(alpha = 0.13f)) {
                Icon(category.icon, contentDescription = null, tint = category.tint, modifier = Modifier.padding(9.dp).size(22.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(category.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(category.subtitle, fontSize = 10.sp, color = muted, maxLines = 2, minLines = 2)
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
    val title = when (selected) {
        "all" -> "كل الأقسام"
        "recent" -> "الملفات الأخيرة"
        "favorites" -> "المفضلة"
        "duplicates" -> "الملفات المتكررة"
        else -> category?.title ?: "الأرشيف"
    }
    val filtered = categories.filter {
        query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) ||
            it.subtitle.contains(query.trim(), ignoreCase = true)
    }.let { if (sortByName) it.sortedBy(FileCategory::title) else it }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "رجوع") }
                Column(Modifier.weight(1f)) {
                    Text(if (category == null) "الأرشيف الذكي" else "الأرشيف / " + category.title, color = brand, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                }
            }
            if (category != null) {
                Spacer(Modifier.height(5.dp))
                Text(category.description, color = muted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (selected == "all") "الأقسام المتاحة" else "المحتوى", color = muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                if (selected == "all") {
                    TextButton(onClick = onSortToggle) {
                        Icon(Icons.Rounded.SortByAlpha, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (sortByName) "أبجدي" else "الترتيب الأصلي")
                    }
                }
                IconButton(onClick = onGridToggle) {
                    Icon(if (gridView) Icons.Rounded.ViewList else Icons.Rounded.GridView, contentDescription = "تغيير طريقة العرض")
                }
            }
        }

        when {
            selected == "all" && query.isNotBlank() -> {
                IndexedSearchResults(query, onCategory)
            }
            selected == "all" -> {
                if (filtered.isEmpty()) {
                    EmptyState(Icons.Rounded.SearchOff, "لا توجد أقسام مطابقة", "جرّب كلمة بحث مختلفة.")
                } else if (gridView) {
                    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(filtered.chunked(2)) { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                pair.forEach { CategoryCard(it, Modifier.weight(1f)) { onCategory(it.id) } }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                        items(filtered) { item ->
                            ListItem(
                                headlineContent = { Text(item.title, fontWeight = FontWeight.SemiBold) },
                                supportingContent = { Text(item.subtitle, color = muted, fontSize = 12.sp) },
                                leadingContent = {
                                    Surface(shape = RoundedCornerShape(11.dp), color = item.tint.copy(alpha = 0.13f)) {
                                        Icon(item.icon, null, tint = item.tint, modifier = Modifier.padding(9.dp))
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
                val message = when (selected) {
                    "recent" -> "لا توجد ملفات حديثة"
                    "favorites" -> "قائمة المفضلة فارغة"
                    else -> "لم يتم فحص الملفات المتكررة بعد"
                }
                EmptyState(icon, message, "لن تظهر نتائج قبل ربط الهاتف وفهرسة الملفات في المرحلة الثانية.")
            }
            selected in listOf("images", "videos", "audio", "documents", "archives", "code", "installers", "other", "empty") -> {
                IndexedFilesContent(selected, query, gridView)
            }
            else -> {
                EmptyState(category?.icon ?: Icons.Rounded.FolderOpen, "هذا القسم لم يُربط بعد", "لن نصنّف الملفات تلقائيًا دون أدلة كافية. اختر مجلدًا من الرئيسية لفهرسة محتوياته.")
            }
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
                category.subtitle.contains(query.trim(), ignoreCase = true)
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

@Composable
private fun MediaThumbnail(file: IndexedFile, selected: String, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(Uri.parse(file.uri))
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

@Composable
private fun IndexedFilesContent(selected: String, query: String, gridView: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var files by remember(selected, query) { mutableStateOf<List<IndexedFile>>(emptyList()) }

    LaunchedEffect(selected, query) {
        files = withContext(Dispatchers.IO) {
            val database = FileIndexDatabase(context)
            try {
                val all = database.allFiles()
                val categoryFiles = when (selected) {
                    "empty" -> all.filter { it.sizeBytes == 0L }
                    else -> all.filter { it.category == selected }
                }
                categoryFiles.filter { file ->
                    query.isBlank() || file.name.contains(query.trim(), ignoreCase = true) ||
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

    if (files.isEmpty()) {
        EmptyState(
            Icons.Rounded.FolderOpen,
            "لا توجد ملفات مفهرسة في هذا القسم",
            "ارجع للرئيسية وحدّث فهرسة الملفات، ثم افتح القسم مرة أخرى."
        )
    } else if ((selected == "images" || selected == "videos") && gridView) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            gridItems(files, key = { it.uri }) { file ->
                Column(
                    modifier = Modifier.fillMaxWidth().clickable { openFile(file) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    MediaThumbnail(
                        file = file,
                        selected = selected,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(file.name, maxLines = 1, fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground)
                    Text(formatBytes(file.sizeBytes), maxLines = 1, fontSize = 10.sp, color = muted)
                }
            }
        }
    } else if (gridView) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 148.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            gridItems(files, key = { it.uri }) { file ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { openFile(file) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Icon(fileCategoryIcon(file.category), null, tint = brand, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.height(10.dp))
                        Text(file.name, maxLines = 2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${file.mimeType ?: "نوع غير معروف"} • ${formatBytes(file.sizeBytes)}",
                            maxLines = 2,
                            fontSize = 10.sp,
                            color = muted
                        )
                    }
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
        ) {
            items(files, key = { it.uri }) { file ->
                ListItem(
                    headlineContent = { Text(file.name, maxLines = 1) },
                    supportingContent = {
                        Text(
                            "${file.mimeType ?: "نوع غير معروف"} • ${formatBytes(file.sizeBytes)}",
                            color = muted,
                            fontSize = 11.sp
                        )
                    },
                    leadingContent = {
                        if (selected == "images" || selected == "videos") {
                            MediaThumbnail(
                                file = file,
                                selected = selected,
                                modifier = Modifier.size(58.dp)
                            )
                        } else {
                            Icon(fileCategoryIcon(file.category), null, tint = brand)
                        }
                    },
                    modifier = Modifier.clickable { openFile(file) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
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
