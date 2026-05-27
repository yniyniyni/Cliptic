package art.yniyniyni.cliptic.cleanup

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class PendingOriginalTrashWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    override fun doWork(): Result {
        if (OriginalScreenshotCleanup.pendingOriginalUri(applicationContext) == null) {
            return Result.success()
        }
        if (!OriginalScreenshotCleanup.canTrashSilently(applicationContext)) {
            OriginalScreenshotCleanup.showPendingFallbackNotification(applicationContext)
            return Result.failure()
        }
        if (OriginalScreenshotCleanup.attemptPendingTrash(applicationContext)) {
            return Result.success()
        }
        return if (runAttemptCount < MAX_RETRY_COUNT) {
            Result.retry()
        } else {
            OriginalScreenshotCleanup.showPendingFallbackNotification(applicationContext)
            Result.failure()
        }
    }

    private companion object {
        const val MAX_RETRY_COUNT = 6
    }
}
