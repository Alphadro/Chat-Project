package fit.vcare.apps.tools

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** وقتی سرور ۴۰۱ برگردونه (توکن منقضی/نامعتبر)، این سیگنال داده می‌شه
 *  تا کل اپ (مثلاً MainActivity) کاربر رو به صفحه‌ی لاگین بفرسته. */
object SessionExpiryNotifier {
    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    fun notifyExpired() {
        _sessionExpired.tryEmit(Unit)
    }
}