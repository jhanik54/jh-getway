package com.jh.gateway

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight

/**
 * The frame of every full page: a title bar with a back button and the page content.
 *
 * The screens that used to be popups now live on their own page, so there is room for
 * long lists (the request log, the routing chain) and the back button always returns to
 * the tab the user came from.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPageScaffold(title: String, onBack: () -> Unit, showBack: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    if (!showBack) {
        Column(Modifier.fillMaxSize()) { content() }
        return
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.str_back_2c7e91))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) { content() }
    }
}
