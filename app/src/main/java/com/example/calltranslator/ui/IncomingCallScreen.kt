package com.example.calltranslator.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calltranslator.R
import com.example.calltranslator.model.CallInvite

@Composable
fun IncomingCallScreen(
    invite: CallInvite,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val context = LocalContext.current

    DisposableEffect(Unit) {
        var mediaPlayer: MediaPlayer? = null
        var vibrator: Vibrator? = null

        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val vibrationEnabled = prefs.getBoolean("vibration_enabled", true)

        try {
            val ringtoneChoice = prefs.getString("ringtone_choice", "Default Ringtone")
            Log.d("Ringing", "Playing incoming ringtone choice: $ringtoneChoice")

            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, ringtoneUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("Ringing", "MediaPlayer failed: ${e.message}")
        }

        if (vibrationEnabled) {
            try {
                vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vibratorManager?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val vibrationEffect = VibrationEffect.createWaveform(longArrayOf(0, 1000, 1000), 1)
                    vibrator?.vibrate(vibrationEffect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(longArrayOf(0, 1000, 1000), 1)
                }
            } catch (e: Exception) {
                Log.e("Ringing", "Vibration failed: ${e.message}")
            }
        }

        onDispose {
            try {
                mediaPlayer?.let {
                    if (it.isPlaying) it.stop()
                    it.release()
                }
            } catch (e: Exception) {
                Log.e("Ringing", "MediaPlayer release failed: ${e.message}")
            }
            try {
                vibrator?.cancel()
            } catch (e: Exception) {
                Log.e("Ringing", "Vibrator cancel failed: ${e.message}")
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.app_logo_foreground),
            contentDescription = null,
            modifier = Modifier.size(60.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Incoming AI Call", fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Ringing...", fontSize = 14.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Medium)
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Box(
            modifier = Modifier.size(120.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = invite.callerName.firstOrNull()?.uppercase() ?: "?",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(text = invite.callerName, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(text = invite.callerNumber, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Text(text = "AI Translation", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        Text(text = "You will hear in: ${invite.receiverHearingLang}", color = MaterialTheme.colorScheme.secondary)
        
        Spacer(modifier = Modifier.weight(1f))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            FloatingActionButton(
                onClick = onDecline,
                containerColor = Color.Red,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(72.dp),
            ) {
                Icon(Icons.Default.CallEnd, contentDescription = "Decline", modifier = Modifier.size(36.dp))
            }
            
            FloatingActionButton(
                onClick = onAccept,
                containerColor = Color(0xFF4CAF50),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(72.dp),
            ) {
                Icon(Icons.Default.Call, contentDescription = "Accept", modifier = Modifier.size(36.dp))
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
    }
}
