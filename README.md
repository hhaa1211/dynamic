# Nusa Dynamic Notch cho Redmi Note 13 Pro 5G 📱✨

Dự án tái hiện và xây dựng ứng dụng **Nusa** (Dynamic Notch) từ [rama2555.github.io/nusa](https://rama2555.github.io/nusa/) dành riêng cho điện thoại **Xiaomi Redmi Note 13 Pro 5G** (màn hình AMOLED 6.67" 120Hz, camera nốt ruồi ở chính giữa mép trên màn hình), ứng dụng phương pháp reverse engineering từ skill [morluto/rea](https://github.com/morluto/rea).

---

## 🛠 1. Đã cài đặt Skill `morluto/rea` (Reverse Engineer Anything)

Skill **REA** đã được thiết lập thành công vào môi trường Antigravity:
* **Global Skill**: `~/.gemini/config/skills/reverse-engineer-anything/SKILL.md`
* **Workspace Skill**: `.agents/skills/reverse-engineer-anything/`
* **MCP Integration**: Đã cấu hình MCP Server cho Antigravity qua `npx rea-agents@latest setup --client antigravity -y`.

---

## 📱 2. Hai phiên bản ứng dụng được tạo

### Cách 1: Ứng dụng Web / PWA Trực tiếp (`nusa-pwa`)
Chạy ngay lập tức trên điện thoại Redmi Note 13 Pro 5G thông qua trình duyệt (Chrome / Mi Browser) mà không cần cài đặt Android Studio:

1. Trên máy tính, khởi động server:
   ```bash
   node serve.js
   ```
2. Trên điện thoại **Redmi Note 13 Pro 5G** (cùng mạng Wi-Fi), mở trình duyệt và truy cập:
   ```
   http://192.168.102.8:3000
   ```
3. Nhấn vào menu trình duyệt (dấu 3 chấm `⋮`) -> chọn **"Thêm vào màn hình chính" (Install app / Add to Home screen)**.
4. Ứng dụng sẽ hoạt động toàn màn hình như một ứng dụng gốc với đầy đủ các tính năng:
   * **Mascot Pet**: Mắt nhấp nháy, vầng hào quang neon RGB (Cyan - Indigo - Violet).
   * **Trình phát nhạc & Lời đồng bộ (Synced Lyrics)**: Xem album art, sóng nhạc, thanh tiến trình, điều khiển bài hát và xem lời chạy thời gian thực.
   * **Pomodoro Focus Timer**: Bộ đếm 25 phút tập trung, vòng hiệp trực quan.
   * **Nhắc uống nước (Water Reminder)**: Theo dõi mục tiêu 2.0L, nút bấm `+250ml`, biểu đồ tiêu thụ theo giờ.
   * **The Shelf & Bộ nhớ tạm**: Lưu trữ ghi chú nhanh và sao chép 1 chạm vào clipboard.
   * **Danh sách việc cần làm (To-Do)**: Quản lý công việc hôm nay.
   * **Mô phỏng sạc nhanh 67W Turbo Charge** và thông báo Dynamic.

---

### Cách 2: Ứng dụng Android Native Gốc (`nusa-redmi-android`)
Dự án Android Studio hoàn chỉnh sử dụng Kotlin và `WindowManager Overlay`:

* **`NusaOverlayService.kt`**: Dịch vụ chạy ngầm hiển thị "viên thuốc" Dynamic Notch đè lên tất cả các ứng dụng khác (`TYPE_APPLICATION_OVERLAY`), ôm sát camera nốt ruồi của Redmi Note 13 Pro 5G.
* **`NusaNotificationListener.kt`**: Bắt sự kiện phát nhạc từ Spotify, YouTube Music, Zing MP3 để tự động hiển thị thông tin bài hát trên Notch.
* **Căn chỉnh phần cứng Redmi Note 13 Pro 5G**:
  * Tọa độ camera: Căn giữa (`Gravity.TOP or Gravity.CENTER_HORIZONTAL`).
  * Y-Offset: `18dp` (khoảng cách Status bar của HyperOS/MIUI).
  * Kích thước đóng: `92dp x 30dp`.
  * Kích thước mở rộng: `350dp x 160dp` với hiệu ứng lò xo mượt mà (`OvershootInterpolator`).
* **Tự động build APK qua GitHub Actions**:
  * Đã tích hợp sẵn file `.github/workflows/build-apk.yml`.
  * Bạn chỉ cần đẩy thư mục này lên GitHub cá nhân, mục **Actions** sẽ tự động biên dịch và tạo file `app-debug.apk` để tải về cài đặt trực tiếp.

---

## 📂 Cấu trúc thư mục

```
ok/
├── .agents/skills/              # Skill REA (Reverse Engineer Anything)
├── nusa-pwa/                    # Ứng dụng Web / PWA cho điện thoại
│   ├── index.html
│   ├── style.css
│   ├── app.js
│   ├── manifest.json
│   ├── sw.js
│   └── icon-192.png / icon-512.png
├── nusa-redmi-android/          # Source code Android Native hoàn chỉnh
│   ├── app/
│   │   ├── src/main/java/com/nusa/dynamicnotch/
│   │   │   ├── MainActivity.kt
│   │   │   ├── NusaOverlayService.kt
│   │   │   └── NusaNotificationListener.kt
│   │   └── src/main/res/
│   └── .github/workflows/build-apk.yml
├── serve.js                     # Server Node.js để chạy PWA trên mạng nội bộ
└── README.md
```
