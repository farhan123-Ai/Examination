package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.AdminRepository
import com.example.data.StudentRegistrationRepository
import com.example.ui.admin.AdminDashboardScreen
import com.example.ui.admin.AdminLoginScreen
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutUser
import com.example.ui.student.StudentPortalScreen
import com.example.ui.theme.AwanAcademyTheme
import com.example.viewmodel.AdminViewModel
import com.example.viewmodel.StudentPortalViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private enum class AppDestination {
    STUDENT_PORTAL,
    ADMIN_LOGIN,
    ADMIN_DASHBOARD
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AwanAcademyTheme {
                AwanAcademyApp()
            }
        }
    }
}

@Composable
private fun AwanAcademyApp() {
    val authFlow = remember { Firebase.auth.authStateFlow() }
    val currentUser by authFlow.collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    key(user.uid) {
        val studentFactory = remember(user.uid) {
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY]!!
                    StudentPortalViewModel(
                        repository = StudentRegistrationRepository(app.applicationContext),
                        currentUserId = user.uid
                    )
                }
            }
        }
        val adminFactory = remember(user.uid) {
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY]!!
                    AdminViewModel(
                        adminRepository = AdminRepository(app.applicationContext)
                    )
                }
            }
        }

        val studentViewModel: StudentPortalViewModel = viewModel(
            key = "student_${user.uid}",
            factory = studentFactory
        )
        val adminViewModel: AdminViewModel = viewModel(
            key = "admin_${user.uid}",
            factory = adminFactory
        )

        var destination by rememberSaveable { mutableStateOf(AppDestination.STUDENT_PORTAL) }
        val adminSession by adminViewModel.session.collectAsStateWithLifecycle()
        val settings by studentViewModel.academySettings.collectAsStateWithLifecycle()
        val adminUiState by adminViewModel.uiState.collectAsStateWithLifecycle()

        val activeDestination = when {
            destination == AppDestination.ADMIN_DASHBOARD && adminSession?.isValid() == true -> {
                AppDestination.ADMIN_DASHBOARD
            }
            destination == AppDestination.ADMIN_DASHBOARD -> {
                AppDestination.ADMIN_LOGIN
            }
            else -> destination
        }

        when (activeDestination) {
            AppDestination.STUDENT_PORTAL -> {
                StudentPortalScreen(
                    viewModel = studentViewModel,
                    onOpenAdminLogin = {
                        destination = if (adminSession?.isValid() == true) {
                            AppDestination.ADMIN_DASHBOARD
                        } else {
                            AppDestination.ADMIN_LOGIN
                        }
                    },
                    onSignOut = {
                        adminViewModel.logoutAdmin()
                        destination = AppDestination.STUDENT_PORTAL
                        signOutUser(
                            credentialManager = credentialManager,
                            scope = scope
                        )
                    }
                )
            }

            AppDestination.ADMIN_LOGIN -> {
                AdminLoginScreen(
                    settings = settings,
                    isLoggingIn = adminUiState.isLoggingIn,
                    loginError = adminUiState.loginError,
                    onLogin = { username, password ->
                        adminViewModel.login(username, password) {
                            destination = AppDestination.ADMIN_DASHBOARD
                        }
                    },
                    onBack = {
                        adminViewModel.clearFeedback()
                        destination = AppDestination.STUDENT_PORTAL
                    }
                )
            }

            AppDestination.ADMIN_DASHBOARD -> {
                AdminDashboardScreen(
                    adminViewModel = adminViewModel,
                    studentViewModel = studentViewModel,
                    onExitToStudentPortal = {
                        destination = AppDestination.STUDENT_PORTAL
                    },
                    onAdminLogout = {
                        adminViewModel.logoutAdmin()
                        destination = AppDestination.STUDENT_PORTAL
                    }
                )
            }
        }
    }
}
