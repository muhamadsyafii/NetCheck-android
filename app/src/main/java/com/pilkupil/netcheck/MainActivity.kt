package com.pilkupil.netcheck

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.pilkupil.netcheck.ui.NetCheckDashboard
import com.pilkupil.netcheck.ui.theme.NetCheckTheme

@SuppressLint("ContextCastToActivity")
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      NetCheckTheme {
        var keepScreenOn by rememberSaveable { mutableStateOf(true) }
        val activity = LocalContext.current as? Activity

        DisposableEffect(activity, keepScreenOn) {
          if (keepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
          } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
          }

          onDispose {
            if (keepScreenOn) {
              activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
          }
        }

        NetCheckDashboard(
          keepScreenOn = keepScreenOn,
          onKeepScreenOnChange = { keepScreenOn = it }
        )
      }
    }
  }
}
