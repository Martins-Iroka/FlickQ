package com.martdev.flickq

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.fragment.app.FragmentActivity
import com.google.firebase.Firebase
import com.google.firebase.appdistribution.FirebaseAppDistributionException
import com.google.firebase.appdistribution.appDistribution
import com.martdev.flickq.feature.notification.domain.PushDeepLinkParser
import com.martdev.flickq.feature.notification.presentation.PushDeepLinkDispatcher
import com.martdev.flickq.feature.payment.presentation.AndroidPaystackUrl
import com.martdev.flickq.push.PushIntentExtras
import org.koin.android.ext.android.inject

class MainActivity : FragmentActivity() {

    private val payStackUrl: AndroidPaystackUrl by inject()
    private val pushDeepLinks: PushDeepLinkDispatcher by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        payStackUrl.register(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handlePaymentRedirectIfPresent(intent)
        // A recreated activity re-delivers its original intent; that tap was already handled.
        if (savedInstanceState == null) handlePushIntentIfPresent(intent)
        setContent {
            FlickQApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePaymentRedirectIfPresent(intent)
        handlePushIntentIfPresent(intent)
    }

    override fun onResume() {
        super.onResume()
        payStackUrl.onResume()
        if (!BuildKonfig.IS_DEBUG) {
            setupFirebaseAppDistribution()
        }
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

    /**
     * A notification tap: the system-tray tap (FCM data as launcher-intent extras) or our own
     * foreground notification. The extras are untrusted; the shared allowlist parser decides.
     */
    private fun handlePushIntentIfPresent(intent: Intent?) {
        val data = PushIntentExtras.read(intent) ?: return
        PushDeepLinkParser.parse(data)?.let(pushDeepLinks::submit)
        intent?.let(PushIntentExtras::strip)
    }

    private fun setupFirebaseAppDistribution() {
        val firebaseAppDistribution = Firebase.appDistribution
        firebaseAppDistribution.updateIfNewReleaseAvailable()
            .addOnProgressListener { _ ->
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
