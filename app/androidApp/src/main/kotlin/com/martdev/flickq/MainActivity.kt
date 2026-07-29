package com.martdev.flickq

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.martdev.flickq.feature.payment.presentation.AndroidPaystackUrl
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val payStackUrl: AndroidPaystackUrl by inject()
    override fun onCreate(savedInstanceState: Bundle?) {
        payStackUrl.register(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handlePaymentRedirectIfPresent(intent)
        setContent {
            FlickQApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePaymentRedirectIfPresent(intent)
    }

    override fun onResume() {
        super.onResume()
        payStackUrl.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        payStackUrl.unregister()
    }

    private fun handlePaymentRedirectIfPresent(intent: Intent?) {
        val data = intent?.data

        if (data != null && data.scheme == "flickq" && data.host == "payment-callback") {
            payStackUrl.handleRedirect()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    FlickQApp()
}
