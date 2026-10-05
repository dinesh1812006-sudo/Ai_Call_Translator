package com.example.calltranslator.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.calltranslator.LanguageOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelector(label: String, selected: LanguageOption, list: List<LanguageOption>, onSelected: (LanguageOption) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selected.displayName, onValueChange = {}, readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                list.forEach { lang ->
                    DropdownMenuItem(text = { Text(lang.displayName) }, onClick = { onSelected(lang); expanded = false })
                }
            }
        }
    }
}
