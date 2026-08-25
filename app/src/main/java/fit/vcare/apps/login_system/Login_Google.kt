package fit.vcare.apps.login_system
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import fit.vcare.apps.R
import kotlinx.coroutines.launch
//Login_Google.kt
@Composable
fun GoogleLoginScreen(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    val isLoading by authViewModel.isLoading.collectAsState()
    var termsAccepted by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "Welcome to VCare",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.DarkGray
        )

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "Login with your Google account",
            fontSize = 16.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 50.dp)
        )

        Spacer(modifier = Modifier.height(30.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 40.dp)
        ) {
            CircleCheckbox(
                checked = termsAccepted,
                onCheckedChange = { termsAccepted = it },
                size = 22.dp,
                checkedColor = Color(0xFF6200EE),
                uncheckedBorderColor = Color(0xFF6200EE)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "I accept the Terms and Privacy Policy",
                fontSize = 14.sp,
                color = Color.Gray
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        OutlinedButton(
            onClick = {
                if (!termsAccepted) {
                    Toast.makeText(context, "Please accept the Privacy Policy", Toast.LENGTH_SHORT).show()
                } else {
                    coroutineScope.launch {
                        try {
                            authViewModel.setLoading(true)

                            val credentialManager = CredentialManager.create(context)
                            val googleIdOption = GetGoogleIdOption.Builder()
                                .setFilterByAuthorizedAccounts(false)
                                .setServerClientId(context.getString(R.string.default_web_client_id))
                                .setAutoSelectEnabled(true)
                                .build()

                            val request = GetCredentialRequest.Builder()
                                .addCredentialOption(googleIdOption)
                                .build()

                            val result = credentialManager.getCredential(context, request)
                            val credential = result.credential
                            Log.d("VCareLogin", "credential دریافت شد -> type=${credential.type}")

                            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                                val idToken = googleIdTokenCredential.idToken
                                Log.d("VCareLogin", "idToken گوگل استخراج شد -> طول=${idToken.length}")

                                authViewModel.loginWithGoogleIdToken(
                                    idToken = idToken,
                                    context = context,
                                    onSuccess = {
                                        Log.d("VCareLogin", "onSuccess در Login_Google اجرا شد")
                                        Toast.makeText(context, "Login Successful", Toast.LENGTH_SHORT).show()

                                        // طبق تصمیم پروژه: بعد از هر لاگین موفق برو صفحه رفرال
                                        navController.navigate("referral") {
                                            popUpTo("login") { inclusive = true }
                                        }
                                    },
                                    onError = { errorMsg ->
                                        Log.e("VCareLogin", "onError در Login_Google اجرا شد -> $errorMsg")
                                        Toast.makeText(context, "Login failed: $errorMsg", Toast.LENGTH_SHORT).show()
                                        authViewModel.setLoading(false)
                                    }
                                )
                            } else {
                                Log.e("VCareLogin", "credential نوع مورد انتظار (GoogleIdToken) نبود")
                            }
                        } catch (e: GetCredentialCancellationException) {
                            Log.d("VCareLogin", "کاربر لاگین گوگل رو کنسل کرد")
                            authViewModel.setLoading(false)
                        } catch (e: Exception) {
                            Log.e("VCareLogin", "Exception در فرآیند Google Sign In", e)
                            Toast.makeText(context, "Google Sign In failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            authViewModel.setLoading(false)
                        }
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 35.dp),
            border = BorderStroke(1.5.dp, Color(0xFF6200EE)),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.White
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF6200EE)
                )
            } else {
                Text(
                    text = "Continue with Google",
                    fontSize = 18.sp,
                    color = Color.DarkGray
                )
            }
        }
    }
}

@Composable
fun CircleCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    borderWidth: Dp = 1.5.dp,
    checkedColor: Color = Color(0xFF6200EE),
    uncheckedBorderColor: Color = Color(0xFF6200EE),
    checkmarkColor: Color = Color.White,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val bg by animateColorAsState(if (checked) checkedColor else Color.Transparent, label = "bg")
    val iconAlpha by animateFloatAsState(if (checked) 1f else 0f, label = "icon")

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bg, CircleShape)
            .border(
                borderWidth,
                if (enabled) uncheckedBorderColor else uncheckedBorderColor.copy(0.4f),
                CircleShape
            )
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                interactionSource = interaction,
                indication = rememberRipple(bounded = true, radius = size / 2)
            ) { onCheckedChange(it) },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "✓",
            color = checkmarkColor.copy(alpha = iconAlpha),
            fontSize = 12.sp
        )
    }
}