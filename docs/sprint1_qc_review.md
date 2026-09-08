# Sprint 1 – QC Review Report 🔐

> **Reviewer:** Antigravity Senior QC Engine  
> **Review Date:** 2026-09-08  
> **Target Sprint:** Sprint 1: Authentication & Token Management  
> **Verdict:** 🟢 **PASS – 100% Quality Gates Met (Đủ điều kiện mở khóa Sprint 2)**

---

## 1. Deliverable-by-Deliverable Audit

| ID | Deliverable | Acceptance Criteria | QC Verification | Result |
|---|---|---|---|---|
| **S1-01** | `GoogleAuthManager` – Sign-In flow | Bấm Sign-In → Intent Google → Nhận User & Tokens | Đã bọc `GoogleSignInClient` với scope tối thiểu `drive.readonly`. Trả về `Pair<AuthUser, AuthTokens>`. | ✅ PASS |
| **S1-02** | `TokenManager` – Encrypted Storage | Lưu trữ token bằng `EncryptedSharedPreferences` | `EncryptedTokenStorage` sử dụng `MasterKey.DEFAULT_AES256_GCM_SPEC` + `AES256_SIV` (hardware-backed). | ✅ PASS |
| **S1-03** | `TokenManager` – Proactive Refresh | Tự động làm mới khi thời gian còn lại `< 300s` | Đã test deterministic với `MutableClock`: 250s → triggers refresh; 1000s → reuses token. | ✅ PASS |
| **S1-04** | `TokenManager` – Concurrent Dedup | 5 concurrent requests → đúng 1 network call | Đã test với 5 coroutines đồng thời qua `Mutex` double-checked locking → exactly 1 call. | ✅ PASS |
| **S1-05** | `TokenInterceptor` – Auto-attach | Gắn `Authorization: Bearer <token>` vào request | Verified qua `MockWebServer`: Header xuất hiện chính xác trên request đến host hợp lệ. | ✅ PASS |
| **S1-06** | `TokenInterceptor` – 401 Retry | Bắt 401 → refresh token → retry → 200 OK | Verified qua `MockWebServer`: 401 đóng body, làm mới token và retry thành công. | ✅ PASS |
| **S1-07** | `LoginScreen` – UI States | Hiển thị đủ Unauthenticated, Loading, Error, Success | `LoginScreen` + `LoginContent` tách biệt stateless, preview đầy đủ, xử lý nút bấm, spinner, error banner. | ✅ PASS |
| **S1-08** | Auth State Persistence | Tắt app mở lại vẫn giữ trạng thái đăng nhập | `MainActivity` và `AuthViewModel` kiểm tra `tokenManager.hasValidSession()` lúc startup để quyết định `startDestination`. | ✅ PASS |
| **S1-09** | Sign-Out Flow | Sign out → dọn sạch tokens → navigate về Login | `HomeScreen` có nút Logout, dọn sạch EncryptedSharedPreferences và Google Client session. | ✅ PASS |

---

## 2. Quality Gates Matrix

| Quality Gate | Tiêu Chí Nghiệm Thu | Kết Quả Thực Tế | Trạng Thái |
|---|---|---|---|
| **Unit Tests** | All unit tests pass | `./gradlew testDebugUnitTest` → 100% tests pass | ✅ PASS |
| **Static Analysis** | Detekt 0 issues | `./gradlew detekt` → 0 issues | ✅ PASS |
| **Android Lint** | 0 errors | `./gradlew lintDebug` → 0 errors | ✅ PASS |
| **Build Artifact** | Debug APK compile & package | `./gradlew assembleDebug` → `app-debug.apk` (24.2 MB) | ✅ PASS |

---

## 3. Security & Policy Compliance (§4 & §5)

- **SEC-01 (No sensitive data in logs):** ✅ Tuyệt đối không log token, serverAuthCode hay thông tin nhạy cảm.
- **SEC-02 (No hardcoded credentials):** ✅ Không hardcode secret hay API keys.
- **SEC-04 (Hardware encryption):** ✅ Mã hóa AES-256 qua Android Keystore & EncryptedSharedPreferences.
- **Drive API Rule 4.1 (Scope tối thiểu):** ✅ Chỉ xin duy nhất `https://www.googleapis.com/auth/drive.readonly`.

---

## 4. Proactive Hardening Đã Áp Dụng

1. **Lọc Domain trong `TokenInterceptor`:** Đã bổ sung `isEligibleGoogleHost()` nhằm đảm bảo Bearer token Google chỉ gửi đến `*.googleapis.com`, `*.googleusercontent.com` hoặc local test server, ngăn chặn triệt để nguy cơ rò rỉ token sang bên thứ ba.
2. **Tự phục hồi Keystore (`EncryptedTokenStorage`):** Bổ sung cơ chế fallback tự động xóa file prefs cũ và tái tạo khi Android Keystore gặp lỗi corrupt trên các thiết bị OEM / ROM nâng cấp, chống crash-loop khi khởi động.

---

## 5. Quyết Định Nghiệm Thu

### 🏆 **VERDICT: PASS – CHÍNH THỨC NGHIỆM THU SPRINT 1**

Toàn bộ 9/9 tiêu chí hoàn tất vượt mức kỳ vọng. **Sprint 1 đóng lại thành công**, sẵn sàng triển khai **Sprint 2: Drive File Browser 📁**.
