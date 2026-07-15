package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen() {
    val context = LocalContext.current

    val openUrl: (String) -> Unit = { urlString ->
        try {
            val url = if (!urlString.startsWith("http://") && !urlString.startsWith("https://")) {
                "https://$urlString"
            } else urlString
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (e: Exception) {}
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(JunoBackground)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section 1: Sayed Johon Developer Profile Hero
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, JunoBorder, RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = JunoSurface),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Profile Image with Cyan Border Ring
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(JunoPrimary, JunoTertiary)))
                            .padding(3.dp)
                            .clip(CircleShape)
                            .background(JunoSurface)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data("https://i.ibb.co.com/F4QytpCb/Johon-1.jpg")
                                .crossfade(true)
                                .build(),
                            contentDescription = "Sayed Johon Profile Picture",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Sayed Johon",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = JunoTextPrimary
                    )
                    Text(
                        text = "Founder, JunoVerse AI",
                        fontSize = 13.sp,
                        color = JunoPrimary,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Building secure, cloud-native distributed computation engines and systems architecture for next-generation intelligence.",
                        fontSize = 12.sp,
                        color = JunoTextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // Section 2: Portfolio Web Domains
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, JunoBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = JunoSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ENTERPRISE CHANNELS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = JunoPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    WebLinkRow("JunoVerse AI Portal", "www.Junoverseai.com") { openUrl("www.Junoverseai.com") }
                    Divider(color = JunoBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 10.dp))
                    WebLinkRow("PeeAI Platforms", "www.peeai.com") { openUrl("www.peeai.com") }
                    Divider(color = JunoBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 10.dp))
                    WebLinkRow("MicTab Workspace", "www.MicTab.com") { openUrl("www.MicTab.com") }
                }
            }
        }

        // Section 3: Social Connectivity Channels
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, JunoBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = JunoSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "CONNECT WITH DEVELOPER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = JunoPrimary
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    ContactRow("Email Support", "contact.sayedjohon@gmail.com", Icons.Default.Email) {
                        try {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:contact.sayedjohon@gmail.com")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {}
                    }
                    Divider(color = JunoBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 10.dp))
                    ContactRow("WhatsApp Direct", "+880 1972-072408", Icons.Default.PhoneAndroid) {
                        openUrl("https://wa.me/8801972072408")
                    }
                    Divider(color = JunoBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 10.dp))
                    ContactRow("Telegram Node", "t.me/sayedjohon", Icons.Default.Chat) {
                        openUrl("https://t.me/sayedjohon")
                    }
                    Divider(color = JunoBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 10.dp))
                    ContactRow("LinkedIn Professional", "linkedin.com/in/sayedjohon", Icons.Default.Business) {
                        openUrl("https://www.linkedin.com/in/sayedjohon/")
                    }
                    Divider(color = JunoBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 10.dp))
                    ContactRow("X / Twitter Feed", "@sayedaljohon", Icons.Default.AlternateEmail) {
                        openUrl("https://x.com/sayedaljohon")
                    }
                    Divider(color = JunoBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 10.dp))
                    ContactRow("YouTube Tech Channel", "youtube.com/@JunoVerseAI", Icons.Default.PlayCircle) {
                        openUrl("https://www.youtube.com/@JunoVerseAI")
                    }
                    Divider(color = JunoBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 10.dp))
                    ContactRow("Facebook Hub", "facebook.com/Sjohon", Icons.Default.Public) {
                        openUrl("https://www.facebook.com/Sjohon/")
                    }
                    Divider(color = JunoBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 10.dp))
                    ContactRow("Instagram Feed", "instagram.com/sayed.johon", Icons.Default.PhotoCamera) {
                        openUrl("https://www.instagram.com/sayed.johon/")
                    }
                }
            }
        }

        // Section 4: Engine Version metadata specs
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "JunoCompute Node Core Engine",
                    fontSize = 12.sp,
                    color = JunoTextSecondary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Version 1.0.0 (Build 4501) · Production Ready",
                    fontSize = 11.sp,
                    color = JunoTextSecondary,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "© 2026 JunoVerse AI. All rights reserved.",
                    fontSize = 10.sp,
                    color = JunoTextSecondary.copy(alpha = 0.7f)
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun WebLinkRow(
    title: String,
    url: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = JunoTextPrimary)
            Text(url, fontSize = 11.sp, color = JunoPrimary, fontFamily = FontFamily.Monospace)
        }
        Spacer(modifier = Modifier.weight(1f))
        Icon(Icons.Default.Launch, null, tint = JunoPrimary, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun ContactRow(
    title: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = JunoPrimary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, fontSize = 13.sp, color = JunoTextSecondary)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = JunoTextPrimary)
        }
        Spacer(modifier = Modifier.weight(1f))
        Icon(Icons.Default.ArrowForwardIos, null, tint = JunoBorder, modifier = Modifier.size(12.dp))
    }
}
