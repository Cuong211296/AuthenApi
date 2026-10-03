# Chạy shop trên máy tính bảng Samsung Galaxy Tab A (SM-P555, Android 7.1.1)

Mục tiêu: chạy MySQL (MariaDB), backend Spring Boot, frontend và ngrok ngay trên tab, thay cho việc thuê server.

> **Lưu ý trung thực:** các script đã kiểm tra cú pháp và gói cài đặt đã build thử trên PC, nhưng **chưa được chạy trên đúng chiếc tab**. Gặp lỗi ở bước nào thì gửi nội dung lỗi (hoặc `logs/*.log`) để chỉnh. Tab chỉ có **2 GB RAM**, hợp để demo hoặc ít khách, không hợp cho shop đông người.

Sơ đồ chạy trên tab:

```
Internet ──ngrok──> nginx :8080 ──/identity──> Spring Boot :8081 ──> MariaDB :3306
                       └── phục vụ file web/ (storefront + admin)
```

## Chuẩn bị

- PC và tab **cùng một mạng Wi-Fi**.
- Tab sạc đủ pin và cắm sạc trong lúc cài.
- Trên PC có sẵn JDK 17, Maven, Node (đang dùng được cho dự án) và `scp`/`ssh` (Windows 10/11 có sẵn, gõ `ssh` trong PowerShell để kiểm tra).
- Tài khoản ngrok của bạn (cần **authtoken**, lấy ở https://dashboard.ngrok.com/get-started/your-authtoken).

---

## Phần A. Trên tab

### Bước 1. Cài Termux

1. **Không dùng bản Play Store** (đã cũ). Cài từ **F-Droid** (https://f-droid.org/packages/com.termux/) hoặc từ trang GitHub releases của Termux: https://github.com/termux/termux-app/releases (chọn file `.apk` hợp với CPU, nếu không rõ chọn bản `universal`).
2. Android có thể hỏi "Cho phép cài ứng dụng từ nguồn không xác định": bấm Cho phép.
3. Mở Termux, gõ lần lượt:

```bash
pkg update -y
pkg install -y openssh
```

Nếu hỏi về file cấu hình (`Y/I/N`...), cứ nhấn Enter để lấy mặc định.

### Bước 2. Xem máy là 32-bit hay 64-bit, còn bao nhiêu RAM

```bash
uname -m
free -m
whoami
```

- `uname -m` ra `armv7l` là 32-bit, `aarch64` là 64-bit. Script tự chọn đúng bản ngrok theo kết quả này. **Gửi mình 2 dòng kết quả này** nếu bạn muốn mình kiểm tra trước.
- `free -m` cho biết RAM trống. Dưới khoảng 800 MB free thì sẽ rất sát.
- `whoami` ra tên người dùng Termux, dạng `u0_a123`. **Ghi lại**, bước sau cần.

### Bước 3. Bật SSH để điều khiển từ PC (đỡ phải gõ trên tab)

```bash
passwd          # đặt mật khẩu cho Termux, gõ 2 lần (không hiện ký tự)
sshd            # bật SSH server, cổng 8022
ip -4 addr show wlan0 | grep inet
```

Dòng cuối ra địa chỉ dạng `inet 192.168.1.50/24`. **`192.168.1.50` là IP của tab.** Nếu lệnh không ra gì, cài `pkg install -y net-tools` rồi gõ `ifconfig`, hoặc xem IP trong Cài đặt > Wi-Fi > tên mạng đang kết nối.

> Mẹo: vào router đặt **IP tĩnh (DHCP reservation)** cho tab để IP khỏi đổi sau này.

---

## Phần B. Trên PC

### Bước 4. Build gói cài đặt

Mở PowerShell tại thư mục dự án:

```powershell
cd D:\LoginFeature\AuthenApi
powershell -ExecutionPolicy Bypass -File scripts\tablet\build-bundle.ps1
```

Script sẽ: build `app.jar`, build frontend tĩnh (`VITE_API_BASE=/identity`), tạo `tablet-bundle\` gồm `app.jar`, `web\`, các file `.sh` và một file `.env` copy từ `.env` hiện tại nhưng đổi database sang MariaDB trên tab (`DB_USERNAME=shop`, mật khẩu ngẫu nhiên mới). Mất vài phút. Thư mục `tablet-bundle\` được git bỏ qua vì chứa bí mật.

### Bước 5. Chép gói sang tab

Thay `u0_a123` và `192.168.1.50` bằng giá trị thật của bạn:

```powershell
scp -P 8022 -r tablet-bundle u0_a123@192.168.1.50:~/
```

Lần đầu nó hỏi `Are you sure you want to continue connecting`: gõ `yes`, rồi nhập mật khẩu ở bước 3. Gói khoảng 55 MB.

### Bước 6. SSH vào tab và chạy cài đặt

```powershell
ssh -p 8022 u0_a123@192.168.1.50
```

Rồi trong cửa sổ SSH (đang là Termux trên tab):

```bash
termux-wake-lock                       # giữ máy không ngủ lúc đang cài
bash ~/tablet-bundle/termux-setup.sh
```

Script cài Java 17, MariaDB, nginx, ngrok, tạo database `identity_service` và user `shop`, viết cấu hình nginx. Mất khoảng **10-25 phút**. Để màn hình tab sáng và đừng thoát Termux.

Script dừng lại khi gặp lỗi. Nếu thấy chữ `Unsupported CPU` hoặc `MariaDB did not start`, gửi mình nguyên dòng đó.

### Bước 7. Thêm ngrok authtoken (một lần)

```bash
termux-chroot ngrok config add-authtoken DAN_TOKEN_CUA_BAN_VAO_DAY
```

> **Quan trọng:** một domain ngrok tĩnh chỉ chạy được trên **một máy tại một thời điểm**. Trước khi bật bên tab, hãy **tắt ngrok trên PC**.

`termux-chroot` cần vì Android không có `/etc/resolv.conf`, ngrok sẽ lỗi DNS nếu thiếu.

### Bước 8. (Tuỳ chọn) Chép dữ liệu hiện tại từ PC sang

Bỏ qua bước này nếu bạn muốn bắt đầu với database trống: backend tự tạo bảng và tài khoản `admin` (mật khẩu theo `ADMIN_PASSWORD`), nhưng bạn phải nhập lại sản phẩm.

Để mang dữ liệu sang, chạy trên **PC**:

```powershell
docker start mysql_8.0.36
docker exec mysql_8.0.36 mysqldump -uroot -proot --no-tablespaces --set-gtid-purged=OFF identity_service > dump.sql
(Get-Content dump.sql -Raw) -replace 'utf8mb4_0900_ai_ci','utf8mb4_unicode_ci' | Set-Content -Encoding utf8 dump_maria.sql
scp -P 8022 dump_maria.sql u0_a123@192.168.1.50:~/
```

Rồi trên **tab** (SSH), **trước khi start backend lần đầu**:

```bash
mysql -u root identity_service < ~/dump_maria.sql
```

> Cách này **chưa thử**: MariaDB và MySQL 8 không giống hệt, có thể phải chỉnh dump. Nếu lỗi, bỏ qua và dùng database trống.

### Bước 9. Chạy

```bash
bash ~/tablet-bundle/start-all.sh
```

Script bật MariaDB, backend, nginx và ngrok (lấy domain từ `FRONTEND_URL` trong `.env`). Lần đầu khởi động backend **mất vài phút** (CPU 2015), script chờ tối đa 6 phút rồi báo `Backend: up`.

Kiểm tra từ PC:

```powershell
curl http://192.168.1.50:8080/identity/products
```

Rồi mở `http://192.168.1.50:8080` bằng trình duyệt trên PC. Địa chỉ công khai: `https://<domain-ngrok-của-bạn>` (giống như lúc chạy trên PC).

---

## Giữ cho tab chạy ổn định

1. **Wake lock:** `start-all.sh` đã gọi `termux-wake-lock`. Trên thanh thông báo của Termux sẽ có nút "Release wake lock", đừng bấm.
2. **Tắt tối ưu pin cho Termux:** Cài đặt > Ứng dụng > Termux > Pin > chọn *Không tối ưu hoá* (tên menu tuỳ bản Samsung; ở Android 7 có thể là *Cài đặt > Pin > Chi tiết sử dụng > Tối ưu hoá*).
3. **Wi-Fi luôn bật khi ngủ:** Cài đặt > Wi-Fi > Nâng cao > *Giữ Wi-Fi bật khi ngủ* > *Luôn luôn*.
4. **Màn hình:** có thể tắt, nhưng đừng đóng (vuốt xoá) Termux khỏi danh sách ứng dụng gần đây.
5. **Sạc và nhiệt:** để nơi thoáng. Cắm sạc liên tục lâu ngày làm pin chai, nên theo dõi.
6. **Sau khi tab khởi động lại** hoặc hết pin: mở Termux, gõ `sshd` (nếu cần SSH) và `bash ~/tablet-bundle/start-all.sh`. Muốn tự chạy khi bật máy thì cài thêm app **Termux:Boot** (cùng nguồn F-Droid); mình có thể viết script khởi động tự động nếu bạn cần.

## Cập nhật khi code thay đổi

Trên PC build lại (`build-bundle.ps1`), rồi chép **chỉ `app.jar` và `web`**, không đè `.env`:

```powershell
ssh -p 8022 u0_a123@192.168.1.50 "bash ~/tablet-bundle/stop-all.sh"
scp -P 8022 tablet-bundle\app.jar u0_a123@192.168.1.50:~/tablet-bundle/app.jar
scp -P 8022 -r tablet-bundle\web u0_a123@192.168.1.50:~/tablet-bundle/
ssh -p 8022 u0_a123@192.168.1.50 "bash ~/tablet-bundle/start-all.sh"
```

Trên tab `ddl-auto` đã tắt (`none`), nên **backend không tự thêm cột**. Khi có migration mới (file `migration_v*.sql`) phải chạy tay trên tab: `scp` file sang rồi `mariadb -u root <tên-database> < file.sql`.

## Khi cần dừng và quay về PC

```bash
bash ~/tablet-bundle/stop-all.sh
```

Rồi trên PC bật lại như cũ (ngrok, MySQL trong Docker, backend, frontend). **Dữ liệu của tab và PC là hai database riêng**, không tự đồng bộ.

## Xử lý sự cố

| Triệu chứng | Cách xem và sửa |
|---|---|
| Thêm vào giỏ / đặt hàng báo `Uncategorizied exception`, log có `foreign key constraint fails` | MariaDB 13.0.2 của Termux (32-bit) không tạo được bảng có từ 2 khoá ngoại trở lên, và lệnh thêm khoá ngoại của Hibernate để lại khoá hỏng từ chối cả dòng hợp lệ. Vì vậy bộ cài dùng `schema-tablet.sql` (bảng **không có khoá ngoại**) và `JPA_HIBERNATE_DDL_AUTO=none`. Đừng đặt lại `update` trên tab. Khi thêm cột mới ở code, phải chạy tay file migration SQL trên tab (`mariadb -u root identity_service < file.sql`). |
| Backend không lên, hoặc tự chết | `tail -n 60 ~/tablet-bundle/logs/backend.log`. Nếu thấy `Killed` hoặc không có lỗi gì thì thường là hết RAM: tắt app khác trên tab, hoặc giảm `-Xmx` trong `start-all.sh` (ví dụ `-Xmx256m`). |
| `Unknown system variable 'transaction_isolation'` hoặc lỗi kết nối DB lạ | Driver MySQL không hợp phiên bản MariaDB. **Gửi mình log**, cách sửa là đổi dự án sang driver MariaDB (cần sửa `pom.xml` và build lại). |
| `Access denied for user 'shop'` | Mật khẩu trong `.env` và trong MariaDB không khớp. Chạy lại `bash ~/tablet-bundle/termux-setup.sh` (nó đặt lại mật khẩu theo `.env`). |
| MariaDB không start | `tail -n 40 ~/tablet-bundle/logs/mariadb.log`. Nếu có file khoá cũ: `rm -f $PREFIX/var/lib/mysql/*.pid` rồi start lại. |
| ngrok báo `ERR_NGROK_334` hoặc "endpoint already online" | Domain đang chạy ở máy khác (thường là PC). Tắt ngrok bên đó. |
| `proot warning: signal 6` / `IS_IN_SYSENTER` khi chạy `termux-chroot` | Kernel cũ của tab. Đặt `export PROOT_NO_SECCOMP=1` trước lệnh (script `start-all.sh` đã làm). Chạy tay thì gõ `PROOT_NO_SECCOMP=1 termux-chroot ngrok ...`. |
| ngrok lỗi DNS, `lookup ... connection refused` | Phải chạy qua `termux-chroot` (script đã làm). Chạy tay thì nhớ thêm `termux-chroot` phía trước. |
| Mở web từ mạng ngoài ra trang "You are about to visit..." | Đó là trang cảnh báo của ngrok bản miễn phí, khách thật cũng sẽ gặp. Cân nhắc Cloudflare Tunnel hoặc gói ngrok trả phí nếu bán thật. |
| MoMo không gọi lại được | Kiểm tra `MOMO_IPN_URL` trong `~/tablet-bundle/.env` đúng domain ngrok, rồi `stop-all.sh` và `start-all.sh`. |
| Chép file bị `Permission denied` / `Connection refused` | Kiểm tra `sshd` còn chạy trên tab (gõ `sshd` lại) và đúng IP, cổng `8022`. |

## Gỡ sạch

```bash
bash ~/tablet-bundle/stop-all.sh
rm -rf ~/tablet-bundle
pkg uninstall -y mariadb nginx openjdk-17
rm -rf $PREFIX/var/lib/mysql      # xoá luôn dữ liệu database
```
