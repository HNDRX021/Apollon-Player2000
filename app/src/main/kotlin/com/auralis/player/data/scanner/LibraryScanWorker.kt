package com.auralis.player.data.scanner

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.auralis.player.data.local.dao.MusicFolderDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * A durable, uniquely named scan, started only by the user: the Refresh button (every enabled
 * folder in one run), a folder's own Rescan, or adding / re-granting a folder. Nothing schedules
 * scans on its own, and a failed scan is not retried in the background: a missing folder or a
 * revoked grant will not fix itself, so the failure is reported and the user decides what to do.
 */
class LibraryScanWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val folderId = inputData.getLong(KEY_FOLDER_ID, INVALID_FOLDER_ID)
        if (folderId == INVALID_FOLDER_ID) return Result.failure()
        val dependencies = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        val folders = if (folderId == ALL_FOLDERS) {
            dependencies.musicFolderDao().getEnabled()
        } else {
            listOfNotNull(dependencies.musicFolderDao().findById(folderId))
        }
        if (folders.isEmpty()) return Result.failure()

        return try {
            val scanner = dependencies.libraryScanner()
            val progressJob = kotlinx.coroutines.currentCoroutineContext()
            // Worker WorkInfo progress remains inspectable after the settings UI is closed.
            val collectJob = kotlinx.coroutines.CoroutineScope(progressJob).launch {
                scanner.progress.collect { progress ->
                    setProgress(workDataOf(KEY_PROCESSED to progress.processed, KEY_CURRENT_NAME to progress.currentName.orEmpty()))
                }
            }
            try {
                if (scanner.scanFolders(folders).any { it.failed }) Result.failure() else Result.success()
            } finally {
                collectJob.cancel()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.failure()
        }
    }

    companion object {
        const val KEY_FOLDER_ID = "folder_id"
        const val KEY_PROCESSED = "processed"
        const val KEY_CURRENT_NAME = "current_name"
        private const val INVALID_FOLDER_ID = -1L
        private const val ALL_FOLDERS = -2L
        private const val ALL_FOLDERS_WORK_NAME = "library_scan_all_folders"

        fun enqueue(context: Context, folderId: Long, replace: Boolean = false) {
            enqueueScan(context, folderId, "library_scan_folder_$folderId", replace)
        }

        /** The Refresh button: one run over every enabled folder. A run already in progress is kept. */
        fun enqueueAll(context: Context) {
            enqueueScan(context, ALL_FOLDERS, ALL_FOLDERS_WORK_NAME, replace = false)
        }

        fun cancel(context: Context, folderId: Long) {
            WorkManager.getInstance(context).cancelUniqueWork("library_scan_folder_$folderId")
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(ALL_FOLDERS_WORK_NAME)
        }

        private fun enqueueScan(context: Context, folderId: Long, uniqueName: String, replace: Boolean) {
            val request = OneTimeWorkRequestBuilder<LibraryScanWorker>()
                .setInputData(workDataOf(KEY_FOLDER_ID to folderId))
                .addTag("library_scan")
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueName,
                if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun libraryScanner(): LibraryScanner
        fun musicFolderDao(): MusicFolderDao
    }
}
