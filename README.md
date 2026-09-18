# DriveStream

> **Xem và tải video từ Google Drive cá nhân trên Android — nguyên chất lượng gốc, không transcode.**

📥 **Tải bản mới nhất:** [Releases](https://github.com/minhtuluc/PersonalStreamingPlatform/releases/latest)

---

## Giới thiệu

Google Drive không có sẵn một trình phát video tử tế: tua chậm, không nhớ đang xem dở, không tải về xem offline, và bản web thường hạ chất lượng khi phát.

**DriveStream** là ứng dụng Android dành riêng cho **một người dùng** — chủ sở hữu tài khoản Google Drive — để:

* **Phát trực tiếp** video trong Drive ở **đúng chất lượng đã upload** (MP4, MKV, MOV… tới 4K), không qua server trung gian, không re-encode.
* **Tải về máy** để xem offline, hỗ trợ tạm dừng và tiếp tục.
* **Nhớ vị trí đang xem** để hôm sau mở lên là xem tiếp đúng chỗ.
* **Duyệt Drive** như một thư viện phim: tìm kiếm, sắp xếp, đánh dấu yêu thích.

Ứng dụng kết nối trực tiếp từ điện thoại tới Google Drive bằng API chính thức, chỉ xin quyền **đọc**. Không có máy chủ nào ở giữa, không upload gì lên đâu cả.

---

## Tính năng

### 🎬 Trình phát video
* Phát video **nguyên chất lượng gốc**, tua tới/lùi mượt nhờ đọc từng đoạn byte (HTTP Range) thay vì tải cả file.
* **Tua nhanh ±10 giây** bằng chạm hai lần vào hai bên màn hình, hoặc dùng nút trên màn hình.
* **Tốc độ phát 0.5x – 2x**, có thể đặt tốc độ mặc định trong Cài đặt.
* **Nhớ vị trí xem** — tự lưu khi đang xem, khi tạm dừng và khi thoát; mở lại là xem tiếp.
* **Tự phát video kế tiếp** khi hết video (có thể tắt).
* **Khoá điều khiển** để tránh chạm nhầm khi đang xem.
* **Xoay màn hình** ngang/dọc bằng một nút.
* **Picture-in-Picture** — thu nhỏ thành cửa sổ nổi để vừa xem vừa làm việc khác.
* Ưu tiên phát **file đã tải về máy** nếu có, không tốn dữ liệu mạng.

### 📁 Duyệt Drive
* Chỉ hiển thị **thư mục và video**, tự động ẩn ảnh, nhạc, tài liệu cho gọn.
* **Breadcrumb** ở thanh tiêu đề — bấm vào tên thư mục cha để nhảy thẳng lên cấp trên.
* **Danh sách hoặc lưới** tuỳ thích, được ghi nhớ cho lần sau.
* **Tìm kiếm toàn bộ Drive** theo tên video.
* **Sắp xếp** theo tên, ngày sửa hoặc dung lượng; nhớ thứ tự đã chọn.
* **Kéo xuống để làm mới** danh sách.

### 📥 Tải về xem offline
* Tải chạy nền, có thanh tiến trình trên thanh thông báo.
* **Tạm dừng / tiếp tục / huỷ** ngay từ thông báo, kể cả khi đã thoát app.
* **Tải tiếp từ đúng chỗ đã dừng** nếu bị ngắt mạng.
* Kiểm tra dung lượng trước khi tải; nếu thất bại thì hiện rõ lý do (hết dung lượng, lỗi mạng) ngay trên thẻ tải về.
* Xoá video đã tải sẽ hỏi xác nhận trước.

### ⭐ Yêu thích & Trang chủ
* **Gắn sao** cho cả thư mục lẫn video để mở nhanh.
* **Tiếp tục xem** — các video đang xem dở, kèm thời gian còn lại.
* **Video đã tải** — kho phim offline của bạn.
* **Yêu thích** và **Xem gần đây** — lịch sử xem, bấm là xem lại.

---

## Yêu cầu

| | |
|---|---|
| **Hệ điều hành** | Android 8.0 (API 26) trở lên |
| **Tài khoản** | Một tài khoản Google có chứa video trong Drive |
| **Mạng** | Cần mạng khi xem trực tuyến; video đã tải thì xem offline được |
| **Dung lượng** | Chỉ cần khi bạn muốn tải video về máy |

---

## Cài đặt

1. Tải file `DriveStream-x.y.z.apk` ở mục [Releases](https://github.com/minhtuluc/PersonalStreamingPlatform/releases/latest).
2. Mở file APK trên điện thoại, bật **"Cài đặt ứng dụng không rõ nguồn gốc"** nếu máy hỏi.
3. Hoặc cài qua ADB:

```bash
adb install -r DriveStream-1.0.0.apk
```

4. Khi mở app lần đầu, **cho phép gửi thông báo** (Android 13+) để thấy tiến trình tải.

> APK được ký bằng debug keystore của dự án nên cài trực tiếp được, không cần qua Google Play.

---

## Hướng dẫn sử dụng

### 1. Đăng nhập
Mở app → bấm **Đăng nhập bằng Google** → chọn tài khoản → đồng ý cho app **quyền đọc Drive**. Sau lần đầu, app ghi nhớ phiên đăng nhập và mở thẳng vào Trang chủ.

### 2. Trang chủ
* Góc trên bên phải có nút **⚙ Cài đặt** và nút **Đăng xuất**.
* Hai thẻ lớn: **Duyệt Drive** (mở lại thư mục xem lần trước nếu bật trong Cài đặt) và **Đã tải**.
* Bên dưới là các mục **Tiếp tục xem**, **Video đã tải**, **Yêu thích**, **Xem gần đây** — chạm vào bất kỳ thẻ nào để xem.

### 3. Duyệt Drive
| Thao tác | Kết quả |
|---|---|
| Chạm vào thư mục | Mở thư mục đó |
| Chạm vào video | Phát video |
| Chạm tên thư mục trên breadcrumb | Nhảy lên cấp trên |
| 🔍 | Tìm kiếm video trên toàn Drive |
| ▦ | Đổi giữa danh sách và lưới |
| ⇅ | Chọn cách sắp xếp |
| ⟳ | Làm mới danh sách |
| ⭐ trên mỗi mục | Thêm/bỏ khỏi Yêu thích |
| ⬇ trên mỗi video | Tải video về máy |
| Kéo danh sách xuống | Làm mới |

### 4. Xem video
Điều khiển tự ẩn sau 4 giây, chạm một lần vào màn hình để hiện lại.

| Thao tác | Kết quả |
|---|---|
| Chạm 1 lần | Hiện/ẩn điều khiển |
| Chạm 2 lần bên **trái** | Tua lại 10 giây |
| Chạm 2 lần bên **phải** | Tua tới 10 giây |
| Kéo thanh tiến trình | Tua tới vị trí bất kỳ |
| Nút tốc độ | Chọn 0.5x – 2x |
| ⏮ / ⏭ | Video trước / video kế tiếp trong thư mục |
| Nút PiP | Thu nhỏ thành cửa sổ nổi |
| Nút xoay | Đổi ngang/dọc |
| Nút khoá | Khoá điều khiển (bấm nút khoá lần nữa hoặc nút Back để mở) |

Vị trí đang xem được **lưu tự động** (trong lúc phát, khi tạm dừng và khi thoát). Lần sau mở lại, app **xem tiếp từ đúng chỗ dừng** — trừ khi bạn mới xem dưới 5 giây hoặc còn dưới 10 giây nữa là hết video.

### 5. Tải video về máy
1. Trong Duyệt Drive, bấm biểu tượng **⬇** trên video cần tải.
2. Theo dõi tiến trình ở thanh thông báo hoặc vào mục **Đã tải** trên Trang chủ.
3. Bấm **Tạm dừng / Tiếp tục** ngay trên thông báo khi cần.
4. Video tải xong sẽ có nhãn **Offline** và tự động phát từ bộ nhớ máy.
5. Muốn xoá: vào **Đã tải** → bấm thùng rác → xác nhận.

### 6. Yêu thích
Gắn sao cho thư mục hoặc video ngay trong Duyệt Drive. Các mục đã gắn sao xuất hiện ở mục **Yêu thích** trên Trang chủ; bấm **Xem tất cả** để mở danh sách đầy đủ và bỏ yêu thích ở đó.

### 7. Cài đặt trong app

| Mục | Ý nghĩa |
|---|---|
| **Sắp xếp mặc định** | Thứ tự sắp xếp khi mở một thư mục |
| **Chế độ xem** | Danh sách hoặc Lưới |
| **Tự động phát video kế tiếp** | Hết video thì tự chạy video tiếp theo trong thư mục |
| **Tốc độ phát mặc định** | Tốc độ áp dụng khi bắt đầu phát |
| **Mở lại thư mục lần cuối** | Bấm "Duyệt Drive" sẽ vào đúng thư mục vừa xem dở |
| **Xoá cache Drive** | Xoá danh sách thư mục và ảnh thu nhỏ đã lưu; sẽ tải lại từ Drive |
| **Xoá lịch sử xem** | Xoá toàn bộ lịch sử, mục "Tiếp tục xem" và "Xem gần đây" |
| **Phiên bản ứng dụng** | Phiên bản đang cài |
| **Đăng xuất** | Xoá phiên đăng nhập khỏi máy |

---

## Xử lý sự cố

**Đăng nhập thất bại, hoặc màn hình đăng nhập tự tắt trên Xiaomi (MIUI / HyperOS)**
* Tài khoản Google chưa được thêm vào **Test users** trong Google Cloud Console (bắt buộc khi ứng dụng OAuth còn ở trạng thái *Testing*).
* Vào *Cài đặt > Tài khoản & đồng bộ > Dịch vụ Google cơ bản* và bật lên.
* Vào *Cài đặt > Ứng dụng > DriveStream > Quyền khác*, bật **"Lấy thông tin tài khoản"** và **"Hiển thị cửa sổ bật lên khi chạy nền"**.

**Xem video báo lỗi 401 / 403**
* Kiểm tra **Google Drive API** đã được bật trong Google Cloud Console.
* Vào Trang chủ → **Đăng xuất** → đăng nhập lại để lấy token mới.

**Không cài được bản debug**
Bản debug có tên gói khác (`com.drivestream.app.debug`), nên muốn đăng nhập được thì phải đăng ký thêm một **OAuth client ID** riêng trong Google Cloud Console với đúng tên gói đó. Cách đơn giản nhất là dùng bản **release**.

**Tải video thất bại**
* Thẻ tải về hiện rõ nguyên nhân: **"Bộ nhớ máy không đủ dung lượng"** → xoá bớt file đã tải; **"Lỗi kết nối"** → kiểm tra mạng rồi bấm thử lại.
* Google Drive có giới hạn tốc độ truy cập; nếu tải chậm hoặc bị ngắt giữa chừng, chờ vài phút rồi tải tiếp — app sẽ tải tiếp từ đúng chỗ đã dừng.

**Video không phát được**
Một số định dạng/độ phân giải mà chip của máy không giải mã được (ví dụ HEVC 10-bit, 4K với máy yếu) sẽ báo lỗi khi phát. Cách xử lý: tải video về máy rồi phát, hoặc chọn bản encode nhẹ hơn.

**Drive hiện thiếu file**
Danh sách được lưu tạm 5 phút. Kéo xuống để làm mới, hoặc vào **Cài đặt → Xoá cache Drive**.

---

## Quyền riêng tư

* Ứng dụng chỉ xin **một quyền duy nhất**: `drive.readonly` — **chỉ đọc**. Không có quyền ghi, sửa, xoá hay chia sẻ file trên Drive của bạn.
* Video được phát bằng **token truy cập tạm thời**; file không bao giờ bị đặt ở chế độ công khai.
* Token đăng nhập được lưu **mã hoá bằng Android Keystore** trên chính thiết bị.
* Không có máy chủ trung gian: dữ liệu đi thẳng giữa điện thoại và Google.
* Gỡ ứng dụng là toàn bộ dữ liệu cục bộ (lịch sử xem, video đã tải, token) bị xoá theo.

---

## Thiết lập Google Cloud (chỉ cần làm một lần)

Ứng dụng cần một **OAuth client ID** thuộc sở hữu của bạn mới đăng nhập được:

1. Mở [Google Cloud Console](https://console.cloud.google.com/) → tạo project mới.
2. **APIs & Services > Library** → tìm **Google Drive API** → **Enable**.
3. **APIs & Services > OAuth consent screen**:
   * User Type: **External**.
   * Scopes: thêm `https://www.googleapis.com/auth/drive.readonly`.
   * **Test users**: thêm địa chỉ Gmail sẽ đăng nhập vào app. *(Bắt buộc — khi ứng dụng còn ở trạng thái Testing, chỉ những tài khoản trong danh sách này đăng nhập được.)*
4. **APIs & Services > Credentials > Create Credentials > OAuth client ID**:
   * Application type: **Android**
   * Package name: `com.drivestream.app`
   * SHA-1: lấy từ keystore dùng để ký APK
     ```bash
     keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
     ```

---

## Tự build từ mã nguồn

Cần **JDK 17/21** và **Android SDK Platform 35**:

```bash
./gradlew assembleRelease   # APK ở app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest # chạy unit test
```

Nếu build trên Windows và Gradle báo sai phiên bản Java, mở `gradle.properties` và bỏ dấu `#` ở dòng `org.gradle.java.home` rồi trỏ tới JDK 17/21 trên máy.

---

## Giấy phép & miễn trừ

Phần mềm này được viết để **dùng cá nhân** — chỉ dành cho chủ sở hữu tài khoản Google Drive đang đăng nhập.
* Không liên kết, không được Google bảo trợ hay chứng thực.
* Các thương hiệu thuộc về chủ sở hữu tương ứng.
