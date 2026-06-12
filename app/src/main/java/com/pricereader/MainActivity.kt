package com.pricereader

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.*
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.pricereader.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ✅ إخفاء الشريط العلوي — الموقع يأخذ الشاشة كاملة
        supportActionBar?.hide()

        val url = intent.getStringExtra(EXTRA_URL) ?: prefs().getString(SetupActivity.KEY_URL, "") ?: ""

        with(binding.webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(v: WebView, u: String, f: Bitmap?) {
                binding.progress.visibility = View.VISIBLE
            }
            override fun onPageFinished(v: WebView, u: String) {
                binding.progress.visibility = View.GONE
                binding.swipe.isRefreshing = false
            }
            override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                if (r.isForMainFrame) {
                    binding.progress.visibility = View.GONE
                    binding.swipe.isRefreshing = false
                }
            }
        }

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, p: Int) {
                binding.progress.progress = p
            }
        }

        binding.swipe.setOnRefreshListener { binding.webView.reload() }

        // زر عائم للإعدادات (يظهر في الزاوية)
        binding.fabSettings.setOnClickListener { showPasswordDialog() }

        binding.webView.loadUrl(url)
    }

    // ✅ كلمة السر — الخطوة الأولى
    private fun showPasswordDialog() {
        val savedPin = prefs().getString(SetupActivity.KEY_PIN, "")

        // إذا لم تُضبط كلمة سر بعد، اطلب إنشاءها
        if (savedPin.isNullOrEmpty()) {
            showCreatePinDialog()
            return
        }

        val input = EditText(this).apply {
            hint = "أدخل كلمة السر"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(48, 24, 48, 24)
        }

        AlertDialog.Builder(this)
            .setTitle("🔒 كلمة السر")
            .setView(input)
            .setPositiveButton("دخول") { _, _ ->
                if (input.text.toString() == savedPin) {
                    showChangeUrlDialog()
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("❌ خطأ")
                        .setMessage("كلمة السر غير صحيحة")
                        .setPositiveButton("حسناً", null)
                        .show()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    // إنشاء كلمة سر جديدة (أول مرة)
    private fun showCreatePinDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
        }
        val etPin1 = EditText(this).apply {
            hint = "كلمة السر الجديدة"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val etPin2 = EditText(this).apply {
            hint = "تأكيد كلمة السر"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        layout.addView(etPin1)
        layout.addView(etPin2)

        AlertDialog.Builder(this)
            .setTitle("🔑 إنشاء كلمة سر")
            .setMessage("هذه أول مرة — أنشئ كلمة سر لحماية تغيير الرابط")
            .setView(layout)
            .setPositiveButton("حفظ") { _, _ ->
                val p1 = etPin1.text.toString()
                val p2 = etPin2.text.toString()
                when {
                    p1.isEmpty() -> showMsg("أدخل كلمة السر")
                    p1 != p2     -> showMsg("كلمتا السر غير متطابقتين")
                    else -> {
                        prefs().edit().putString(SetupActivity.KEY_PIN, p1).apply()
                        showMsg("✅ تم حفظ كلمة السر")
                    }
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    // تغيير الرابط بعد التحقق من كلمة السر
    private fun showChangeUrlDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
        }
        val etUrl = EditText(this).apply {
            setText(prefs().getString(SetupActivity.KEY_URL, ""))
            hint = "https://example.com"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        val etPin = EditText(this).apply {
            hint = "كلمة السر الجديدة (اتركها فارغة للإبقاء)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        layout.addView(etUrl)
        layout.addView(etPin)

        AlertDialog.Builder(this)
            .setTitle("⚙️ الإعدادات")
            .setView(layout)
            .setPositiveButton("حفظ") { _, _ ->
                val newUrl = etUrl.text.toString().trim()
                val newPin = etPin.text.toString().trim()
                if (newUrl.isNotEmpty()) {
                    val final = if (newUrl.startsWith("http")) newUrl else "https://$newUrl"
                    prefs().edit().putString(SetupActivity.KEY_URL, final).apply()
                    binding.webView.loadUrl(final)
                }
                if (newPin.isNotEmpty()) {
                    prefs().edit().putString(SetupActivity.KEY_PIN, newPin).apply()
                    showMsg("✅ تم تحديث كلمة السر")
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showMsg(msg: String) {
        AlertDialog.Builder(this).setMessage(msg).setPositiveButton("حسناً", null).show()
    }

    private fun prefs() = getSharedPreferences(SetupActivity.PREFS, Context.MODE_PRIVATE)

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.webView.canGoBack()) binding.webView.goBack() else super.onBackPressed()
    }

    companion object { const val EXTRA_URL = "url" }
}
