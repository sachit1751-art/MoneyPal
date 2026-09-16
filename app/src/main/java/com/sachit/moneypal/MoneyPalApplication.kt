package com.sachit.moneypal

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.sachit.moneypal.domain.usecase.BackfillOrphanedPeriodsUseCase
import com.sachit.moneypal.wearsync.PhoneWearMessageListener
import dagger.Module
import dagger.Provides
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import logcat.AndroidLogcatLogger
import logcat.LogPriority
import logcat.asLog
import logcat.logcat
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/** App-lifetime coroutine scope tied to the process, with a SupervisorJob and error logging. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppScopeModule {

    @Provides
    @ApplicationScope
    @Singleton
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            logcat(LogPriority.ERROR) { "Uncaught error in app-scope coroutine\n${e.asLog()}" }
        }
    )
}

@HiltAndroidApp
class MoneyPalApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var phoneWearMessageListener: PhoneWearMessageListener

    @Inject
    lateinit var backfillOrphanedPeriodsUseCase: BackfillOrphanedPeriodsUseCase

    @Inject
    lateinit var thresholdAlertObserver: com.sachit.moneypal.presentation.notification.ThresholdAlertObserver

    @Inject
    lateinit var envelopeAlertObserver: com.sachit.moneypal.presentation.notification.EnvelopeAlertObserver

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        AndroidLogcatLogger.installOnDebuggableApp(this, minPriority = LogPriority.VERBOSE)

        phoneWearMessageListener.start()
        thresholdAlertObserver.start()
        envelopeAlertObserver.start()

        applicationScope.launch {
            runCatching { backfillOrphanedPeriodsUseCase() }
                .onFailure { e ->
                    logcat(LogPriority.ERROR) { "Startup backfill failed\n${e.asLog()}" }
                }
        }

//        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
//            override fun onActivityPaused(activity: Activity) {
// // 				ExtendWidgetReceiver.requestUpdateData(activity.applicationContext)
// // 				MinimalWidgetReceiver.requestUpdateData(activity.applicationContext)
//            }
//        })
    }
}
