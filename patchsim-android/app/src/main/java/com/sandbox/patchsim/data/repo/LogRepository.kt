package com.sandbox.patchsim.data.repo

import com.sandbox.patchsim.core.model.LogLevel
import com.sandbox.patchsim.data.db.LogDao
import com.sandbox.patchsim.data.db.LogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Central execution/diagnostic log sink surfaced in the Logs screen. */
class LogRepository(
    private val dao: LogDao,
    private val scope: CoroutineScope
) {
    fun observeRecent(limit: Int = 500): Flow<List<LogEntity>> = dao.observeRecent(limit)

    fun log(level: LogLevel, tag: String, message: String) {
        scope.launch(Dispatchers.IO) {
            dao.insert(LogEntity(level = level.name, tag = tag, message = message))
        }
    }

    fun d(tag: String, msg: String) = log(LogLevel.DEBUG, tag, msg)
    fun i(tag: String, msg: String) = log(LogLevel.INFO, tag, msg)
    fun w(tag: String, msg: String) = log(LogLevel.WARN, tag, msg)
    fun e(tag: String, msg: String) = log(LogLevel.ERROR, tag, msg)

    suspend fun clear() = dao.clear()
}
