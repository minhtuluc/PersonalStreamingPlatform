package com.drivestream.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.drivestream.app.ui.theme.DriveStreamTheme
import com.drivestream.app.ui.theme.PureBlack
import com.drivestream.app.ui.theme.TextPrimary

@Composable
fun PlayerScreen(
    fileId: String,
    title: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
    ) {
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = TextPrimary
            )
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PlayerScreenPreview() {
    DriveStreamTheme {
        PlayerScreen(
            fileId = "demo-id",
            title = "Sample Video.mp4",
            onNavigateBack = {}
        )
    }
}
