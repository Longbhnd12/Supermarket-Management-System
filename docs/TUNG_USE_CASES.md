# Nghiệp vụ do Tùng phụ trách

## Phạm vi

| Nhóm | Use case |
| --- | --- |
| Authentication & Profile | UC01 Login, UC02 Logout, UC03 Change Password, UC44 View/Update Profile, UC45 Forgot Password |
| User & Access Management | UC04 View Users, UC05 Manage Users, UC06 Manage Roles & Permissions |
| Supplier Portal | UC48 View Purchase Orders, UC49 Respond to Purchase Order |

```text
User
  │
  ├─ UC01 Login ──► xác định role/permission
  │                    │
  │         ┌──────────┴──────────┐
  │         ▼                     ▼
  │   Nhân viên nội bộ          Supplier
  │         │                     │
  │   UC03 / UC44 / UC02       UC48 ──► UC49
  │
  └─ UC45 Forgot Password ──► mã reset qua email ──► mật khẩu mới

Admin
  └─ UC04 View Users ──► UC05 Manage Users ──► UC06 Roles & Permissions
```

## Authentication & Profile

### UC01 - Login

Người dùng đăng nhập bằng username hoặc email và password. Hệ thống kiểm tra account tồn tại, password BCrypt hợp lệ và account ở trạng thái `ACTIVE`; sau đó tạo ngữ cảnh xác thực và trả về role để frontend chuyển tới màn hình phù hợp.

- Sai username/password: từ chối đăng nhập.
- Account inactive/locked: từ chối đăng nhập.
- Đăng nhập thành công/thất bại: ghi System Log.
- Role và permission quyết định API/màn hình người dùng được dùng sau khi đăng nhập.

### UC02 - Logout

Từ Account Menu, người dùng logout. Hệ thống ghi audit log, kết thúc xác thực, xóa session/token cookie và frontend quay về màn hình Login.

### UC03 - Change Password

Người dùng đã đăng nhập phải nhập identifier, current password, new password và confirm password.

- Identifier phải thuộc account đang đăng nhập.
- Current password phải đúng.
- New password phải đạt policy, khác mật khẩu hiện tại và khớp confirm password.
- Thành công: hash mật khẩu mới và ghi `PASSWORD_CHANGED`; không ghi password vào log.

### UC44 - View/Update Profile

Người dùng chỉ xem/cập nhật profile của chính mình. Role và status là read-only vì thuộc UC05 của Admin.

- Hiển thị username, email, role, status và thông tin employee nếu account được liên kết employee.
- Full name/phone chỉ áp dụng khi có employee liên kết.
- Khi cập nhật: kiểm tra email hợp lệ/unique, lưu thay đổi và ghi audit log.

### UC45 - Forgot Password

Luồng gồm hai bước:

1. Người dùng nhập username/email; hệ thống tìm account và gửi mã reset tới email đã đăng ký.
2. Người dùng nhập mã, mật khẩu mới và confirm password; hệ thống kiểm tra mã, thời hạn, policy mật khẩu rồi cập nhật mật khẩu và invalidate mã.

Business rules:

- Mã reset có thời hạn và chỉ dùng một lần.
- Mã sai bị giới hạn số lần nhập; quá giới hạn thì mã bị hủy.
- Không tiết lộ account có tồn tại hay không qua thông báo reset password.
- Cần cấu hình Gmail SMTP để gửi email thực tế.

> Lưu ý kỹ thuật: bộ đếm mã reset lưu in-memory phù hợp bài học/đồ án nhỏ. Khi chạy production hoặc nhiều backend instance, nên dùng DB hoặc Redis.

## User & Access Management

### UC04 - View Users

Admin/role có quyền `PERM_USER_VIEW` xem danh sách user có phân trang, tìm theo username/email và lọc theo role/status. Kết quả hiển thị username, email, role, status và last login.

### UC05 - Manage Users

Role có quyền `PERM_USER_MANAGE` có thể tạo, cập nhật, kích hoạt/vô hiệu hóa và xóa mềm user.

- Username và email phải unique.
- Role phải tồn tại.
- Manager, Cashier và Warehouse Staff cần liên kết Employee theo rule nghiệp vụ.
- Supplier account cần liên kết Supplier.
- Không cho phép thao tác gây mất account quản trị cuối cùng hoặc tạo liên kết employee/supplier không hợp lệ.
- Các thay đổi được ghi audit log: create/update/status/delete.

### UC06 - Manage Roles & Permissions

Admin quản lý permission của từng role. Permission được chuyển thành authority `PERM_*` và được SecurityConfig kiểm tra tại API.

- Không được gỡ các quyền quản trị tối thiểu của role `ADMIN`.
- Supplier chỉ có permission Supplier Portal; không được gọi API purchase order nội bộ.
- Cập nhật permission được ghi audit log.

## Supplier Portal

### UC48 - View Purchase Orders

Supplier chỉ xem Purchase Order thuộc supplier gắn với account đang đăng nhập.

Điều kiện truy cập:

```text
currentUser.role = SUPPLIER
AND currentUser.supplierId = purchaseOrder.supplierId
```

Supplier có thể tìm/lọc theo PO number, status và khoảng ngày. Các trạng thái thấy được là `SENT`, `ACCEPTED`, `REJECTED_BY_SUPPLIER`, `PARTIALLY_FULFILLED` và `FULFILLED`.

### UC49 - Respond to Purchase Order

Supplier chỉ phản hồi một PO của chính mình khi trạng thái là `SENT`.

| Action | Validation | Kết quả |
| --- | --- | --- |
| Accept | PO thuộc supplier, PO đang `SENT` | PO chuyển `ACCEPTED` |
| Reject | Bắt buộc có reason/comment | PO chuyển `REJECTED_BY_SUPPLIER` |
| Propose Changes | Bắt buộc reason; quantity/price > 0; delivery date không trước issued date; phải có ít nhất một thay đổi | Lưu proposal, PO giữ `SENT` để chờ xử lý |

Mọi phản hồi đều:

- Ghi System Log.
- Gửi notification cho requester và role Manager.
- Kiểm tra ownership để supplier không thể thao tác PO của supplier khác.

## Điểm cần làm rõ

`PROPOSE_CHANGES` đã lưu đề xuất và gửi notification, nhưng PO vẫn ở `SENT`. Điều này hợp lý nếu rule quy định proposal được thương lượng ngoài hệ thống và Supplier sẽ Accept/Reject sau đó.

Nếu Manager phải duyệt proposal trong hệ thống, cần bổ sung business rule/flow tiếp theo:

```text
Supplier proposes changes
  → Manager reviews proposal
  → Accept proposal: cập nhật PO và gửi PO revised
  → Reject proposal: giữ PO gốc hoặc chuyển lại SENT
```

Không nên tự thêm use case mới nếu SRS không yêu cầu; cần thống nhất rõ cách proposal được xử lý trong tài liệu nghiệp vụ.

## Tóm tắt ngắn

Tùng phụ trách Authentication, User & Access Management và Supplier Portal. Người dùng đăng nhập rồi được cấp quyền theo role/permission; Admin quản lý account và permission; mỗi người dùng có thể đổi mật khẩu, cập nhật profile hoặc reset password qua email. Supplier chỉ được xem và phản hồi Purchase Order của chính supplier đó. Các thao tác nhạy cảm đều có audit log.
