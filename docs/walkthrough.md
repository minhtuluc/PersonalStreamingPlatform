# Walkthrough – Sprint 1: Authentication & Token Management 🔐

> **Sprint:** Sprint 1: Authentication & Token Management  
> **Status:** ✅ **COMPLETED & VERIFIED – QC Passed & Pushed to GitHub**  
> **Branch / Remote:** `main` → `https://github.com/minhtuluc/PersonalStreamingPlatform.git`  
> **Environment:** Android Jetpack Compose, Hilt DI, EncryptedSharedPreferences, OpenJDK 21, Android SDK 34/35

---

## 1. Deliverables Summary

| ID | Task | Implementation Details | Status | Verification |
|---|---|---|---|---|
| **S1-01** | `GoogleAuthManager` – Sign-In flow | Google Sign-In SDK cấu hình scope tối thiểu `https://www.googleapis.com/auth/drive.readonly`. Trả về `AuthUser` + `AuthTokens`. | ✅ COMPLETED | [GoogleAuthManager.kt](file:///c:/Users/tumin/OneDrive/Documents/STREAMING/app/src/main/java/com/drivestream/app/auth/GoogleAuthManager.kt) |
| **S1-02** | `TokenManager` – Encrypted Storage | `EncryptedTokenStorage` sử dụng `EncryptedSharedPreferences` với phần cứng AES-256 (`AES256_GCM`). | ✅ COMPLETED | [TokenStorage.kt](file:///c:/Users/tumin/OneDrive/Documents/STREAMING/app/src/main/java/com/drivestream/app/auth/TokenStorage.kt) |
| **S1-03** | `TokenManager` – Proactive Refresh | Tự động kích hoạt refresh khi thời gian sống token còn `< 300s` (5 phút), đảm bảo streaming không bao giờ bị ngắt quãng giữa chừng. | ✅ COMPLETED | [TokenManager.kt](file:///c:/Users/tumin/OneDrive/Documents/STREAMING/app/src/main/java/com/drivestream/app/auth/TokenManager.kt) |
| **S1-04** | `TokenManager` – Concurrent Dedup | Mutex double-checked locking: Khi có 5-10 coroutine đồng thời yêu cầu token, chỉ có đúng **1** network call thực tế diễn ra. | ✅ COMPLETED | [TokenManagerTest.kt](file:///c:/Users/tumin/OneDrive/Documents/STREAMING/app/src/test/java/com/drivestream/app/auth/TokenManagerTest.kt) |
| **S1-05** | `TokenInterceptor` – Bearer Injection | OkHttp Interceptor tự động gắn header `Authorization: Bearer <validAccessToken>` vào mọi outbound call đến Google domains. | ✅ COMPLETED | [TokenInterceptor.kt](file:///c:/Users/tumin/OneDrive/Documents/STREAMING/app/src/main/java/com/drivestream/app/network/TokenInterceptor.kt) |
| **S1-06** | `TokenInterceptor` – 401 Retry | Bắt mã HTTP 401 Unauthorized từ Google Drive API, tự động refresh token và retry request đúng 1 lần. | ✅ COMPLETED | [TokenInterceptorTest.kt](file:///c:/Users/tumin/OneDrive/Documents/STREAMING/app/src/test/java/com/drivestream/app/network/TokenInterceptorTest.kt) |
| **S1-07** | `LoginScreen` & `AuthViewModel` | Giao diện Material 3 Dark Luxury (nút Sign-In Google, loading spinner, error banner) kết nối `AuthViewModel` theo kiến trúc MVVM + UDF. | ✅ COMPLETED | [LoginScreen.kt](file:///c:/Users/tumin/OneDrive/Documents/STREAMING/app/src/main/java/com/drivestream/app/ui/screens/LoginScreen.kt), [AuthViewModel.kt](file:///c:/Users/tumin/OneDrive/Documents/STREAMING/app/src/main/java/com/drivestream/app/auth/AuthViewModel.kt) |
| **S1-08** | Auth State Persistence | `MainActivity` và `AuthViewModel` tự động khôi phục session đã lưu từ EncryptedSharedPreferences lúc mở app. | ✅ COMPLETED | [MainActivity.kt](file:///c:/Users/tumin/OneDrive/Documents/STREAMING/app/src/main/java/com/drivestream/app/MainActivity.kt) |
| **S1-09** | Sign-Out Flow | Nút Sign-Out trên HomeScreen dọn sạch tokens trong secure storage, sign-out Google client, và điều hướng quay lại LoginScreen. | ✅ COMPLETED | [HomeScreen.kt](file:///c:/Users/tumin/OneDrive/Documents/STREAMING/app/src/main/java/com/drivestream/app/ui/screens/HomeScreen.kt), [NavGraph.kt](file:///c:/Users/tumin/OneDrive/Documents/STREAMING/app/src/main/java/com/drivestream/app/ui/navigation/NavGraph.kt) |

---

## 2. Testing & Quality Verification

### 2.1 Automated Unit Test Suites
1. **`TokenManagerTest.kt`**:
   - `returns existing valid token when remaining lifetime is well above 300s` -> PASS
   - `proactively triggers refresh when remaining lifetime is less than 300s` -> PASS
   - `fails when no token exists in storage` -> PASS
   - `deduplicates concurrent refresh calls into a single actual refresh invocation` (5 concurrent coroutines -> exactly 1 network call) -> PASS
   - `clears storage and fails when refresh returns TokenExpired error` -> PASS
   - `signOut clears all stored credentials and user info` -> PASS

2. **`TokenInterceptorTest.kt`** (sử dụng `MockWebServer`):
   - `attaches Bearer token header on request` -> PASS
   - `when 401 received, refreshes token and retries request` (HTTP 401 -> token refreshed -> retry HTTP 200) -> PASS
   - `when 401 retry fails, returns 401 response` -> PASS

3. **`AuthViewModelTest.kt`** (sử dụng `Turbine` & `MockK`):
   - `restores authenticated state on init when valid session exists` -> PASS
   - `emits Unauthenticated when no session exists` -> PASS
   - `handleSignInResult on success transitions to Authenticated` -> PASS
   - `handleSignInResult on failure transitions to Error` -> PASS
   - `signOut clears sessions and transitions to Unauthenticated` -> PASS

4. **`SprintZeroSetupTest.kt`**:
   - BufferConfig, Room Converters, Routes, Error Taxonomy -> PASS

### 2.2 Quality Gates Status
```
.\gradlew.bat testDebugUnitTest  → BUILD SUCCESSFUL (All unit tests pass)
.\gradlew.bat detekt             → BUILD SUCCESSFUL (0 issues)
.\gradlew.bat lintDebug          → BUILD SUCCESSFUL (0 errors)
.\gradlew.bat assembleDebug      → BUILD SUCCESSFUL (app-debug.apk 24.2 MB)
```

---

## 3. Tuân Thủ Quy Chuẩn An Toàn & Policy

- **Bảo mật token (SEC-01 & SEC-04):** Không có bất kỳ token nào bị in ra console hay Logcat. Toàn bộ token lưu trữ qua `EncryptedSharedPreferences` với khóa phần cứng `MasterKey.DEFAULT_AES256_GCM_SPEC`.
- **Phạm vi quyền tối thiểu (Section 4.1):** Chỉ yêu cầu duy nhất scope `https://www.googleapis.com/auth/drive.readonly`.
- **Khả năng phục hồi mạng (Section 2.4):** Toàn bộ lỗi được gom về `AppError` chuẩn hóa, hỗ trợ retry và thông báo tiếng Việt thân thiện với người dùng.

---

## 4. Hardening & Senior QC Approval

- **Lọc Domain an toàn:** `TokenInterceptor` chỉ gửi Bearer token đến `*.googleapis.com`, `*.googleusercontent.com` hoặc local mock server.
- **Khôi phục lỗi Keystore hỏng:** `EncryptedTokenStorage` tự động dọn dẹp file prefs hỏng và tái tạo bộ nhớ mã hóa khi Keystore OEM bị corrupt.
- **Báo cáo QC:** Chi tiết tại [sprint1_qc_review.md](file:///C:/Users/tumin/.gemini/antigravity-ide/brain/930b13ae-c5d2-48b1-bb3e-d2952afc2a21/sprint1_qc_review.md) với kết luận: **🟢 PASS (100% Tiêu Chuẩn)**.

---

## 5. Git Repository & Initial Push 🚀

- **Remote URL:** `https://github.com/minhtuluc/PersonalStreamingPlatform.git`
- **Default Branch:** `main`
- **Initial Commit:** `648d67c` (`feat(auth): complete Sprint 0 foundation and Sprint 1 authentication & token management`)
- **Pushed Status:** Up-to-date with `origin/main`, clean working tree.
