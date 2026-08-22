package com.example.ui.screens.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.screens.Language

@Composable
fun RuleItem(title: String, desc: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(2.dp))
        Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 14.sp)
    }
}

@Composable
fun RulesDialog(lang: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = Language.get("menu_rules", lang),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Box(modifier = Modifier.height(240.dp)) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            RuleItem(title = Language.get("rule_1_title", lang), desc = Language.get("rule_1_desc", lang))
                            RuleItem(title = Language.get("rule_2_title", lang), desc = Language.get("rule_2_desc", lang))
                            RuleItem(title = Language.get("rule_3_title", lang), desc = Language.get("rule_3_desc", lang))
                            RuleItem(title = Language.get("rule_4_title", lang), desc = Language.get("rule_4_desc", lang))
                            RuleItem(title = Language.get("rule_5_title", lang), desc = Language.get("rule_5_desc", lang))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(Language.get("close", lang))
                }
            }
        }
    }
}
