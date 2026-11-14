package com.mohamed.fileflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class FileCategory(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tint: Color
)

private val categories = listOf(
    FileCategory("study", "الدراسة", "المواد والمراجع والمحاضرات", Icons.Rounded.School, Color(0xFF8B7CF6)),
    FileCategory("images", "الصور", "الصور واللقطات", Icons.Rounded.Image, Color(0xFF58A6E7)),
    FileCategory("videos", "الفيديوهات", "المقاطع والتسجيلات المرئية", Icons.Rounded.PlayCircle, Color(0xFFE879A8)),
    FileCategory("audio", "الصوتيات", "الموسيقى والتسجيلات", Icons.Rounded.MusicNote, Color(0xFFE7A24E)),
    FileCategory("projects", "مشاريع البرمجة", "المشاريع وملفات المصدر", Icons.Rounded.Code, Color(0xFF51B68A)),
    FileCategory("apps", "التطبيقات", "حزم التطبيقات المثبتة", Icons.Rounded.Apps, Color(0xFF4EB8C5)),
    FileCategory("empty", "الملفات الفارغة", "ملفات بحجم صفر", Icons.Rounded.InsertDriveFile, Color(0xFF9299A8)),
    FileCategory("other", "أخرى", "ملفات لم يتم تصنيفها بعد", Icons.Rounded.Folder, Color(0xFF9299A8))
)

private val ink = Color(0xFF172033)
private val muted = Color(0xFF788397)
private val canvas = Color(0xFFF4F6FA)
private val brand = Color(0xFF5367D8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FileFlowApp() }
    }
}

@Composable
private fun FileFlowApp() {
    var selected by remember { mutableStateOf("home") }
    var query by remember { mutableStateOf("") }
    var dark by remember { mutableStateOf(false) }
    val scheme = if (dark) darkColorScheme(
        primary = Color(0xFF9AA8FF),
        background = Color(0xFF101522),
        surface = Color(0xFF1A2232),
        onBackground = Color(0xFFF1F3F8),
        onSurface = Color(0xFFF1F3F8)
    ) else lightColorScheme(
        primary = brand,
        background = canvas,
        surface = Color.White,
        onBackground = ink,
        onSurface = ink
    )

    MaterialTheme(colorScheme = scheme) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    val destinations = listOf(
                        Triple("home", "الرئيسية", Icons.Rounded.Home),
                        Triple("all", "الملفات", Icons.Rounded.FolderOpen),
                        Triple("recent", "الأخيرة", Icons.Rounded.History),
                        Triple("settings", "الإعدادات", Icons.Rounded.Settings)
                    )
                    destinations.forEach { (id, label, icon) ->
                        NavigationBarItem(
                            selected = selected == id,
                            onClick = { selected = id },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                TopHeader(
                    query = query,
                    onQuery = { query = it },
                    onSettings = { selected = "settings" },
                    dark = dark,
                    onTheme = { dark = !dark }
                )
                when (selected) {
                    "home" -> HomeScreen(onCategory = { selected = it }, dark = dark)
                    "settings" -> SettingsScreen(dark = dark, onTheme = { dark = it })
                    else -> CategoryScreen(
                        selected = selected,
                        query = query,
                        onCategory = { selected = it }
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
    onSettings: () -> Unit,
    dark: Boolean,
    onTheme: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = brand) {
                Icon(Icons.Rounded.FolderCopy, null, tint = Color.White, modifier = Modifier.padding(10.dp).size(25.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("FileFlow", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("مساحة ملفاتك، بوضوح", fontSize = 11.sp, color = muted)
            }
            IconButton(onClick = onTheme) {
                Icon(if (dark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode, contentDescription = "تغيير المظهر")
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = "الإعدادات")
            }
        }
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("ابحث عن ملف أو مجلد...", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            shape = RoundedCornerShape(14.dp)
        )
    }
}

@Composable
private fun HomeScreen(onCategory: (String) -> Unit, dark: Boolean) {
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
            Card(
                colors = CardDefaults.cardColors(containerColor = if (dark) Color(0xFF26324B) else Color(0xFFE8EDFF)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Storage, null, tint = brand)
                        Spacer(Modifier.width(8.dp))
                        Text("مساحة الجهاز", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("لم يتم ربط مساحة الهاتف بعد", color = muted, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(progress = { 0f }, modifier = Modifier.fillMaxWidth(), color = brand, trackColor = Color(0xFFD5DBEA))
                    Spacer(Modifier.height(8.dp))
                    Text("ستظهر بيانات التخزين بعد تفعيل صلاحيات Android.", color = muted, fontSize = 11.sp)
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("تصفح حسب النوع", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("8 أقسام", color = muted, fontSize = 11.sp)
            }
        }
        items(categories.chunked(2)) { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { category ->
                    CategoryCard(category, Modifier.weight(1f)) { onCategory(category.id) }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Shield, null, tint = Color(0xFF39A77B), modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("خصوصيتك أولاً", fontWeight = FontWeight.Bold)
                    Text("الفهرسة والتصنيف سيعملان محلياً على جهازك.", color = muted, fontSize = 12.sp, textAlign = TextAlign.Center)
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
private fun CategoryScreen(selected: String, query: String, onCategory: (String) -> Unit) {
    val category = categories.firstOrNull { it.id == selected }
    val title = when (selected) {
        "all" -> "كل الملفات"
        "recent" -> "الملفات الأخيرة"
        "favorites" -> "المفضلة"
        "duplicates" -> "الملفات المتكررة"
        else -> category?.title ?: "الأرشيف"
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 22.dp)) {
        Text("الأرشيف", color = brand, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(title, fontSize = 25.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(14.dp))
        if (selected == "all") {
            categories.forEach { item ->
                ListItem(
                    headlineContent = { Text(item.title, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(item.subtitle, color = muted, fontSize = 12.sp) },
                    leadingContent = { Icon(item.icon, null, tint = item.tint) },
                    trailingContent = { Icon(Icons.Rounded.ChevronLeft, null, tint = muted) },
                    modifier = Modifier.clickable { onCategory(item.id) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        } else {
            Spacer(Modifier.height(40.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(shape = CircleShape, color = brand.copy(alpha = 0.10f)) {
                        Icon(category?.icon ?: Icons.Rounded.FolderOpen, null, tint = brand, modifier = Modifier.padding(20.dp).size(34.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(if (query.isBlank()) "لا توجد ملفات معروضة" else "لا توجد نتائج مطابقة", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "محتوى الهاتف سيظهر هنا بعد تنفيذ ربط Android وفهرسة الملفات في المرحلة الثانية.",
                        color = muted, fontSize = 12.sp, textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(dark: Boolean, onTheme: (Boolean) -> Unit) {
    var notifications by remember { mutableStateOf(false) }
    var protectOriginals by remember { mutableStateOf(true) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("التفضيلات", color = brand, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("الإعدادات", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        }
        item {
            SettingsSwitch("المظهر الداكن", "تغيير ألوان واجهة التطبيق", dark, onTheme)
            SettingsSwitch("حماية الملفات الأصلية", "عدم نقل أو تعديل الملفات أثناء الفهرسة", protectOriginals) { protectOriginals = it }
            SettingsSwitch("الإشعارات", "تنبيهات اكتمال الفهرسة لاحقاً", notifications) { notifications = it }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Text("حول FileFlow", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("الإصدار 1.0.0", color = muted, fontSize = 12.sp)
                    Text("تطبيق محلي لتنظيم الملفات وإدارتها على Android.", color = muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitch(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(subtitle, color = muted, fontSize = 11.sp)
            }
            Switch(checked = checked, onCheckedChange = onChecked)
        }
    }
}
