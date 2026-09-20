package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R

/**
 * Information screen: clean, pure white native Android interface with Q&A style answers,
 * company details (CHRIST FLOW ISO), owner information (Cristian Gabriel), and link styling.
 */
@Composable
fun InformationScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  var showWebView by remember { mutableStateOf(false) }
  val context = LocalContext.current
  val scrollState = rememberScrollState()

  BackHandler {
    if (showWebView) {
      showWebView = false
    } else {
      onBack()
    }
  }

  if (showWebView) {
    InAppWebViewScreen(
      url = "https://reinvent-your-project.ai.studio/",
      title = "Repositorio de Mods",
      onClose = { showWebView = false }
    )
    return
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color.White)
      .statusBarsPadding()
      .testTag("information_screen")
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      // Top bar
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = onBack,
          modifier = Modifier.testTag("info_back_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.action_back),
            tint = Color(0xFF1E1F22)
          )
        }

        Text(
          text = stringResource(R.string.info_title),
          fontSize = 15.sp,
          fontWeight = FontWeight.SemiBold,
          letterSpacing = 0.2.sp,
          color = Color(0xFF1E1F22),
          modifier = Modifier
            .padding(start = 6.dp)
            .testTag("info_top_title")
        )
      }

      HorizontalDivider(
        color = Color(0xFFF0F2F5),
        thickness = 1.dp
      )

      // Content list: clean Q&A, clean spacing, no heavy boxes
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(scrollState)
          .padding(horizontal = 24.dp, vertical = 20.dp)
      ) {
        // Section 1: ¿Qué es Modstudio?
        InfoItem(
          question = stringResource(R.string.info_what_is_title),
          answer = stringResource(R.string.info_what_is_answer),
          testTagPrefix = "what_is"
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Section 2: ¿Para qué sirve?
        InfoItem(
          question = stringResource(R.string.info_what_for_title),
          answer = stringResource(R.string.info_what_for_answer),
          testTagPrefix = "what_for"
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Section 3: Empresa y repositorio de mods
        Text(
          text = stringResource(R.string.info_company_title),
          fontSize = 14.sp,
          fontWeight = FontWeight.Bold,
          color = Color(0xFF1E1F22),
          letterSpacing = 0.1.sp,
          modifier = Modifier.testTag("company_title")
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
          text = stringResource(R.string.info_company_name),
          fontSize = 13.sp,
          fontWeight = FontWeight.SemiBold,
          color = Color(0xFF2B2B2B),
          modifier = Modifier.testTag("company_name")
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
          text = stringResource(R.string.info_company_desc),
          fontSize = 13.sp,
          lineHeight = 20.sp,
          color = Color(0xFF555555),
          modifier = Modifier.testTag("company_desc")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Blue link style text: "Ver página web"
        Text(
          text = stringResource(R.string.info_link_page),
          fontSize = 13.sp,
          fontWeight = FontWeight.Medium,
          color = Color(0xFF1A73E8),
          textDecoration = TextDecoration.Underline,
          modifier = Modifier
            .clickable {
              if (checkInternetConnection(context)) {
                showWebView = true
              } else {
                Toast.makeText(
                  context,
                  "No tienes acceso a internet para ver la página",
                  Toast.LENGTH_LONG
                ).show()
              }
            }
            .padding(vertical = 4.dp)
            .testTag("info_link_page")
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Section 4: Propietario y equipo técnico
        InfoItem(
          question = stringResource(R.string.info_owner_title),
          answer = stringResource(R.string.info_owner_name),
          testTagPrefix = "owner"
        )

        Spacer(modifier = Modifier.height(40.dp))
      }
    }
  }
}

private fun checkInternetConnection(context: Context): Boolean {
  val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
  val activeNetwork = cm.activeNetwork ?: return false
  val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
  return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
    (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
      capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
      capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun InAppWebViewScreen(
  url: String,
  title: String,
  onClose: () -> Unit
) {
  var webViewInstance by remember { mutableStateOf<WebView?>(null) }
  var progress by remember { mutableFloatStateOf(0f) }
  var isLoading by remember { mutableStateOf(true) }

  BackHandler {
    if (webViewInstance?.canGoBack() == true) {
      webViewInstance?.goBack()
    } else {
      onClose()
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.White)
      .statusBarsPadding()
      .navigationBarsPadding()
      .testTag("in_app_webview_screen")
  ) {
    // Top Bar with Title, Refresh and Close button
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(56.dp)
        .padding(horizontal = 8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      IconButton(
        onClick = onClose,
        modifier = Modifier
          .size(48.dp)
          .testTag("btn_close_webview")
      ) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = "Cerrar",
          tint = Color(0xFF1E1F22)
        )
      }

      Column(
        modifier = Modifier
          .weight(1f)
          .padding(horizontal = 8.dp)
      ) {
        Text(
          text = title,
          fontSize = 15.sp,
          fontWeight = FontWeight.SemiBold,
          color = Color(0xFF1E1F22),
          maxLines = 1
        )
        Text(
          text = url,
          fontSize = 11.5.sp,
          color = Color(0xFF8C9099),
          maxLines = 1
        )
      }

      IconButton(
        onClick = { webViewInstance?.reload() },
        modifier = Modifier
          .size(48.dp)
          .testTag("btn_refresh_webview")
      ) {
        Icon(
          imageVector = Icons.Default.Refresh,
          contentDescription = "Recargar",
          tint = Color(0xFF1E1F22)
        )
      }
    }

    if (isLoading) {
      LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
          .fillMaxWidth()
          .height(2.5.dp),
        color = Color(0xFF2563EB),
        trackColor = Color(0xFFE5E7EB)
      )
    } else {
      HorizontalDivider(color = Color(0xFFF2F4F7), thickness = 1.dp)
    }

    // Android WebView component
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
    ) {
      AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
          WebView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT,
              ViewGroup.LayoutParams.MATCH_PARENT
            )

            settings.apply {
              javaScriptEnabled = true
              domStorageEnabled = true
              loadWithOverviewMode = true
              useWideViewPort = true
              setSupportZoom(true)
              builtInZoomControls = true
              displayZoomControls = false
            }

            webViewClient = object : WebViewClient() {
              override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                isLoading = true
              }

              override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                isLoading = false
              }

              override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
              ) {
                super.onReceivedError(view, request, error)
                isLoading = false
              }
            }

            webChromeClient = object : WebChromeClient() {
              override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                progress = newProgress / 100f
                if (newProgress >= 100) {
                  isLoading = false
                }
              }
            }

            loadUrl(url)
            webViewInstance = this
          }
        },
        update = { webView ->
          webViewInstance = webView
        }
      )
    }
  }
}

@Composable
private fun InfoItem(
  question: String,
  answer: String,
  testTagPrefix: String
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Text(
      text = question,
      fontSize = 14.sp,
      fontWeight = FontWeight.Bold,
      color = Color(0xFF1E1F22),
      letterSpacing = 0.1.sp,
      modifier = Modifier.testTag("${testTagPrefix}_question")
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
      text = answer,
      fontSize = 13.sp,
      lineHeight = 20.sp,
      color = Color(0xFF555555),
      modifier = Modifier.testTag("${testTagPrefix}_answer")
    )
  }
}
