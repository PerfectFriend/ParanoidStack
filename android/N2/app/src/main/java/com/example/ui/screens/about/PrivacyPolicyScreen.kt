package com.example.ui.screens.about

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    onBack: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Политика конфиденциальности") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Назад") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Политика конфиденциальности N2 Messenger",
                fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(16.dp))

            PolicySection("1. Сбор данных",
                "N2 Messenger не собирает персональные данные пользователей. " +
                "Все сообщения хранятся только на устройстве пользователя " +
                "и передаются в зашифрованном виде через децентрализованную сеть SMP.")
            PolicySection("2. Шифрование",
                "Все сообщения защищены сквозным шифрованием (E2EE) " +
                "с использованием протокола Double Ratchet и X3DH. " +
                "Ключи шифрования генерируются на устройстве и никогда не покидают его.")
            PolicySection("3. Сетевая безопасность",
                "Приложение поддерживает маршрутизацию через Tor и V2Ray " +
                "для обеспечения анонимности. Метаданные о соединениях " +
                "не логируются и не передаются третьим лицам.")
            PolicySection("4. Хранение данных",
                "База данных приложения шифруется с использованием AES-256-GCM. " +
                "Парольная фраза для шифрования хранится только на устройстве.")
            PolicySection("5. Сторонние сервисы",
                "Приложение не использует сторонние аналитические сервисы, " +
                "трекеры или рекламные SDK. Единственные сетевые запросы — " +
                "к SMP-серверам, Tor и V2Ray узлам.")
            PolicySection("6. Уведомления",
                "Push-уведомления обрабатываются локально. " +
                "Никакие данные не передаются сторонним сервисам уведомлений.")
            PolicySection("7. Контакты",
                "По вопросам конфиденциальности: " +
                "n2messenger@proton.me")
        }
    }
}

@Composable
private fun PolicySection(title: String, body: String) {
    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    Spacer(Modifier.height(4.dp))
    Text(body, fontSize = 14.sp, lineHeight = 20.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(16.dp))
}
