package com.martdev.flickq

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.google.firebase.Firebase
import com.google.firebase.appdistribution.FirebaseAppDistributionException
import com.google.firebase.appdistribution.appDistribution
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
        setupFirebaseAppDistribution()
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

    private fun setupFirebaseAppDistribution() {
        val firebaseAppDistribution = Firebase.appDistribution
        firebaseAppDistribution.updateIfNewReleaseAvailable()
            .addOnProgressListener { updateProgress ->
                // (Optional) Implement custom progress updates in addition to
                // automatic NotificationManager updates.
            }
            .addOnFailureListener { e ->
                // (Optional) Handle errors.
                if (e is FirebaseAppDistributionException) {
                    when (e.errorCode) {
                        FirebaseAppDistributionException.Status.NOT_IMPLEMENTED -> {
                            // SDK did nothing. This is expected when building for Play.
                        }
                        else -> {
                            // Handle other errors.
                        }
                    }
                }
            }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    FlickQApp()
}
