package com.nexar.assistant.ui.screens

import BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexar.assistant.memory.MemoryFact
import com.nexar.assistant.ui.theme.NexarCyan
import com.nexar.assistant.ui.theme.NexarError
import com.nexar.assistant.ui.theme.NexarSurface
import com.nexar.assistant.ui.viewmodel.NexarViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MemoryScreen(
    viewModel: NexarViewModel,
    onBack: () -> Unit
) {
    val memories by viewModel.memories.collectAsState()
    var showClearAllDialog by remember { mutableStateOf(false) }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Clear All Memories?", color = Color(0xFFE0F0FF)) },
            text = { Text("This will permanently delete all stored memories. NEXAR will not remember anything about you.", color = Color(0xFFB0CCDD)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllMemory()
                    showClearAllDialog = false
                }) {
                    Text("Clear All", color = NexarError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel", color = Color(0xFF667788))
                }
            },
            containerColor = NexarSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050A14))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = NexarCyan)
            }
            Text(
                text = "Memory",
                color = NexarCyan,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.weight(1f)
            )
            if (memories.isNotEmpty()) {
                IconButton(onClick = { showClearAllDialog = true }) {
                    Icon(Icons.Default.DeleteForever, contentDescription = "Clear all", tint = NexarError.copy(alpha = 0.7f))
                }
            }
        }

        if (memories.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Memory,
                        contentDescription = null,
                        tint = Color(0xFF1A3040),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No memories yet",
                        color = Color(0xFF334455),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tell NEXAR to remember something,\nlike your favorite game or city.",
                        color = Color(0xFF223344),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            Text(
                text = "${memories.size} stored ${if (memories.size == 1) "memory" else "memories"}",
                color = Color(0xFF445566),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(memories, key = { it.key }) { fact ->
                    MemoryItem(
                        fact = fact,
                        onDelete = { viewModel.deleteMemory(fact) }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun MemoryItem(
    fact: MemoryFact,
    onDelete: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    Card(
        colors = CardDefaults.cardColors(containerColor = NexarSurface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, Color(0xFF1A3040))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fact.description,
                    color = Color(0xFFD0E8FF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = fact.key,
                        color = NexarCyan.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                    Text(
                        text = " → ",
                        color = Color(0xFF334455),
                        fontSize = 11.sp
                    )
                    Text(
                        text = fact.value,
                        color = Color(0xFF8AABCC),
                        fontSize = 11.sp
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = dateFormat.format(Date(fact.timestamp)),
                    color = Color(0xFF334455),
                    fontSize = 10.sp
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color(0xFF334455),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
