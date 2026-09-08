package com.drivestream.app

enum class ErrorSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * Centralized error taxonomy for DriveStream operations.
 */
sealed class AppError(
    val userMessage: String,
    val technicalMessage: String,
    val isRetryable: Boolean,
    val severity: ErrorSeverity,
    cause: Throwable? = null
) : Exception(userMessage, cause) {

    data class TokenExpired(override val cause: Throwable) : AppError(
        userMessage = "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
        technicalMessage = "Access token expired and refresh failed: ${cause.message}",
        isRetryable = false,
        severity = ErrorSeverity.HIGH,
        cause = cause
    )

    data class NetworkUnavailable(override val cause: Throwable) : AppError(
        userMessage = "Không có kết nối mạng. Vui lòng kiểm tra WiFi/4G.",
        technicalMessage = "Network unreachable: ${cause.message}",
        isRetryable = true,
        severity = ErrorSeverity.MEDIUM,
        cause = cause
    )

    data class RateLimited(val retryAfterMs: Long) : AppError(
        userMessage = "Đang tải quá nhanh. Vui lòng đợi vài giây...",
        technicalMessage = "Google API rate limit reached, backoff ${retryAfterMs}ms",
        isRetryable = true,
        severity = ErrorSeverity.LOW
    )

    data class ServerError(val statusCode: Int, override val cause: Throwable) : AppError(
        userMessage = "Máy chủ Google đang bận. Thử lại sau.",
        technicalMessage = "Server error HTTP $statusCode: ${cause.message}",
        isRetryable = true,
        severity = ErrorSeverity.MEDIUM,
        cause = cause
    )

    data class FileNotFound(val fileId: String) : AppError(
        userMessage = "File không tồn tại hoặc đã bị xóa.",
        technicalMessage = "File not found: $fileId",
        isRetryable = false,
        severity = ErrorSeverity.LOW
    )

    data class InsufficientStorage(val requiredBytes: Long) : AppError(
        userMessage = "Không đủ bộ nhớ để tải video. Cần thêm dung lượng.",
        technicalMessage = "Insufficient storage for download: requires $requiredBytes bytes",
        isRetryable = false,
        severity = ErrorSeverity.MEDIUM
    )

    data class AuthFailed(override val cause: Throwable) : AppError(
        userMessage = "Đăng nhập Google không thành công. Vui lòng thử lại.",
        technicalMessage = "Google sign-in failed: ${cause.message}",
        isRetryable = true,
        severity = ErrorSeverity.MEDIUM,
        cause = cause
    )

    companion object {
        fun fromThrowable(throwable: Throwable): AppError {
            return when (throwable) {
                is AppError -> throwable
                is java.io.IOException -> NetworkUnavailable(throwable)
                else -> ServerError(statusCode = -1, cause = throwable)
            }
        }
    }
}
