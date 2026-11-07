# Nusa Dynamic Notch cho Redmi Note 13 Pro 5G 📱✨

[![Build Android APK](https://github.com/hhaa1211/dynamic/actions/workflows/build-apk.yml/badge.svg)](https://github.com/hhaa1211/dynamic/actions/workflows/build-apk.yml)
[![Deploy PWA](https://github.com/hhaa1211/dynamic/actions/workflows/deploy-pwa.yml/badge.svg)](https://github.com/hhaa1211/dynamic/actions/workflows/deploy-pwa.yml)
[![GitHub Release](https://img.shields.io/github/v/release/hhaa1211/dynamic?color=cyan&label=Release)](https://github.com/hhaa1211/dynamic/releases/tag/v1.0.0)

Dự án tái hiện và xây dựng ứng dụng **Nusa** (Dynamic Notch) từ [rama2555.github.io/nusa](https://rama2555.github.io/nusa/) dành riêng cho điện thoại **Xiaomi Redmi Note 13 Pro 5G** (màn hình AMOLED 6.67" 120Hz, camera nốt ruồi ở chính giữa mép trên màn hình), ứng dụng phương pháp reverse engineering từ skill [morluto/rea](https://github.com/morluto/rea).

---

## 🚀 Tải về & Trải nghiệm ngay trên điện thoại

### 1. Cài đặt file APK trực tiếp (Android Native)
* 👉 **[Tải file APK trực tiếp: app-debug.apk](https://github.com/hhaa1211/dynamic/releases/download/v1.0.0/app-debug.apk)** (Dung lượng: ~5.9MB)
* Trang phát hành: [GitHub Releases v1.0.0](https://github.com/hhaa1211/dynamic/releases/tag/v1.0.0)
* **Cách cài đặt**:
  1. Tải file `app-debug.apk` về điện thoại Redmi Note 13 Pro 5G.
  2. Mở file và cấp quyền cài đặt từ nguồn này.
  3. Mở ứng dụng **Nusa Notch**, bật quyền **"Hiển thị trên ứng dụng khác" (Overlay)** và quyền **"Truy cập thông báo"**.
  4. Viên thuốc Dynamic Notch sẽ tự động xuất hiện ôm trọn camera nốt ruồi chính giữa của Redmi Note 13 Pro 5G!

---

### 2. Trải nghiệm trực tuyến qua GitHub Pages (PWA Web App)
* 👉 **[Mở ứng dụng trên điện thoại: https://hhaa1211.github.io/dynamic/](https://hhaa1211.github.io/dynamic/)**
* **Cách cài đặt PWA**:
  1. Truy cập link trên bằng Chrome hoặc Mi Browser trên điện thoại.
  2. Bấm menu `⋮` ở góc trên -> chọn **"Thêm vào màn hình chính" (Install app / Add to Home screen)**.
  3. Ứng dụng sẽ chạy toàn màn hình độc lập như app native!

---

## 🎨 Tính năng đã chuyển đổi từ Nusa

| Trạng thái | Mô tả trên Redmi Note 13 Pro 5G |
| :--- | :--- |
| **🐾 Pet Mascot** | Mắt nhấp nháy chuyển động và vầng sáng hào quang neon RGB (Cyan - Indigo - Violet). |
| **🎵 Music Player** | Đồng bộ tự động bài hát đang phát (Spotify, YouTube Music), hiện ảnh bìa, sóng equalizer, thanh tua. |
| **📜 Synced Lyrics** | Lời bài hát cuộn sáng theo thời gian thực (real-time lyrics). |
| **⏳ Pomodoro Timer** | Đếm ngược 25 phút tập trung, vòng tròn tiến độ trực quan. |
| **💧 Water Reminder** | Theo dõi mục tiêu 2.0L nước, nút bấm nhanh `+250ml`, biểu đồ tiêu thụ theo giờ. |
| **📁 The Shelf & Clipboard** | Lưu nhanh ghi chú, sao chép 1 chạm vào bộ nhớ tạm. |
| **✅ Việc cần làm (To-Do)** | Danh sách công việc với tính năng đánh dấu hoàn thành. |
| **⚡ Turbo Charge 67W** | Hiệu ứng cắm sạc pin siêu tốc động khi cắm cáp Type-C. |

---

## 📂 Cấu trúc Repository

```
dynamic/
├── .agents/skills/              # Skill REA (Reverse Engineer Anything)
├── .github/workflows/           # CI/CD tự động build APK và deploy Pages
│   ├── build-apk.yml            # Tự động biên dịch APK mỗi khi có commit
│   └── deploy-pwa.yml           # Tự động xuất bản Web PWA lên GitHub Pages
├── nusa-pwa/                    # Source code Web App PWA
│   ├── index.html
│   ├── style.css
│   ├── app.js
│   ├── manifest.json
│   └── sw.js
├── nusa-redmi-android/          # Source code Android Native (Kotlin)
│   ├── app/src/main/
│   │   ├── java/com/nusa/dynamicnotch/
│   │   │   ├── MainActivity.kt
│   │   │   ├── NusaOverlayService.kt
│   │   │   └── NusaNotificationListener.kt
│   │   └── res/
│   └── build.gradle
└── README.md
```
