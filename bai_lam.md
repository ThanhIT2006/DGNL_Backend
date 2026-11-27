# Bài làm CinemaBox – Database + Backend

Họ tên:
Mã sinh viên:
Lớp:

## Pha 1 (không dùng AI)

Tự đánh dấu phần đã xong trước mốc 1 (chỉ để GV tham khảo, không thay cho kết quả chấm):

- [x] I1  - [x] I2  - [x] I3  - [x] Endpoint GET /api/showtimes/{id}
- [ ] F1  - [ ] F2  - [ ] F3  - [ ] API khách hàng (tạo, xem, danh sách, validate, lỗi)


Ghi chú (nếu có):

## Pha 2 (được dùng AI)

### Cách bạn chống bán vượt số ghế khi nhiều người đặt cùng lúc

(2–5 câu: chọn cách nào, vì sao, đã kiểm chứng bằng gì)

### Cách bạn hoàn tác khi thanh toán lỗi

### Test bạn đã viết (tên lớp, tên test, mỗi test kiểm tra điều gì)

### Nhật ký dùng AI

| # | Bạn hỏi AI điều gì | AI gợi ý gì | Bạn giữ / sửa / bỏ, vì sao |
|---|---|---|---|
| 1 |  |  |  |
| 2 |  |  |  |
| 3 |  |  |  |

## 4.3 – API đặt vé an toàn

### Cách chống overselling

Phương án được chọn là pessimistic write locking cho bản ghi `showtimes` trong một transaction `@Transactional`. Repository dùng `PESSIMISTIC_WRITE` để khóa suất chiếu ngay trước khi kiểm tra `available_seats`; request thứ hai phải chờ request đầu xong rồi mới đọc lại số ghế mới. Nhờ vậy mỗi request đọc–kiểm tra–giảm ghế trong cùng một chuỗi khóa ghi, không xảy ra tình trạng hai request cùng thấy ghế đủ. CSDL H2 chạy trong chế độ MySQL hỗ trợ khóa ghi này, đã được kiểm chứng bằng test 10 thread đặt 1 ghế trên suất có 5 ghế: 5 request thành công, 5 request trả 409 và `available_seats` kết thúc bằng 0.

Schema cũng thêm `CHECK (available_seats >= 0)` để có ràng buộc DB bổ sung. Trong code luôn kiểm tra đủ ghế trước khi thanh toán. Trade-off là các request đặt cùng một suất phải chờ tuần tự và thao tác giữ khóa khoảng 80 ms khi gọi gateway; đổi lại tính đúng đắn cao và phù hợp dữ liệu in-memory H2 của project.

### Transaction và rollback thanh toán

`BookingService.create(...)` được đánh dấu `@Transactional`. Transaction thực hiện: xác thực customer, khóa suất chiếu, kiểm tra ghế, tính tiền bằng `BigDecimal`/số nguyên, gọi `PaymentGateway.charge(...)`, tạo booking `CONFIRMED`, trừ `available_seats` rồi lưu booking. Gateway không được gọi trước khi khóa và kiểm tra ghế; khóa được giữ xuyên thời gian gateway để request khác không vượt slot.

Khi `PaymentFailedException` xảy ra, booking chưa được lưu và ghế chưa bị trừ, nên không có thay đổi nghiệp vụ để commit. Spring rollback transaction nếu có entity thay đổi nào trước exception; test đã kiểm tra trực tiếp số ghế, tổng booking và booking CONFIRMED trước/sau khi gọi payment với số tiền > 1.000.000 đồng.

Booking thành công chỉ được tạo sau khi gateway trả `paymentRef`, với status `CONFIRMED`; nếu commit/lưu thất bại thì transaction rollback. API hủy được bổ sung để khóa booking và suất chiếu, chuyển CONFIRMED sang CANCELLED và hoàn ghế đúng một lần.

### Danh sách test thực tế

- `BookingApiTest.validationFieldsAndMalformedJson`: thiếu/trường sai quantity và JSON sai định dạng đều trả 400 `VALIDATION_FAILED`.
- `BookingApiTest.customerAndShowtimeNotFound`: customer/suất chiếu không tồn tại trả 404 `NOT_FOUND`.
- `BookingApiTest.standardAndGoldBookingTotalsAndPaymentRef`: STANDARD giữ giá, GOLD giảm 10%, booking có status CONFIRMED và paymentRef.
- `BookingApiTest.notEnoughSeatsAndNoNegativeSeat`: hết ghế trả 409 và không cho số ghế âm.
- `BookingApiTest.paymentFailureRollsBackSeatsAndBooking`: thanh toán bị từ chối trả 402, số ghế và số booking không thay đổi.
- `BookingApiTest.concurrentBookingDoesNotOversell`: 10 thread đặt vé cùng lúc; 5 request thành công/5 bị 409 khi chỉ còn 5 ghế, ghế còn lại và booking confirmed nhất quán.
- `BookingApiTest.cancelConfirmedBookingReturnsSeatOnce`: hủy trả ghế và hủy lần thứ hai bị 409 `INVALID_STATE`.
- `TuKiemTraTest`: hai test API mẫu hiện có vẫn chạy xanh.

Kết quả đã kiểm chứng: `mvn test` – 9 test, 0 failures, 0 errors, BUILD SUCCESS.

### Nhật ký sử dụng AI

| Nội dung hỏi AI | AI đề xuất | Quyết định của tôi | Lý do | Cách kiểm chứng |
|---|---|---|---|---|
| Phương pháp chống overselling cho H2 | Pessimistic write lock, atomic update hoặc optimistic lock | Giữ pessimistic write lock trên suất chiếu | H2 support khóa ghi JPA và lock tuần tự hóa đúng thao tác đọc–kiểm tra–trừ ghế | Test 10 thread: đúng 5 booking thành công và available_seats = 0 |
| Cách đặt transaction quanh gateway | Mở transaction trước toàn bộ luồng nhưng cân nhắc rollback | Giữ `@Transactional` cho toàn luồng, khóa trước, payment trong transaction | Gateway chậm 80 ms nhưng lock bảo vệ ghế; PaymentFailedException làm rollback toàn bộ | Test trực tiếp database trước/sau payment 402 |
| Cách tính tiền không sai floating point | BigDecimal hoặc tính số nguyên | Dùng `BigDecimal` nhân giá × số vé × 90/100 với FLOOR | Long và BigDecimal không dùng floating point; làm tròn xuống đúng đồng | Test STANDARD 160000 và GOLD 144000 |
| Cách chuẩn hóa lỗi | Thêm `@ExceptionHandler` vào GlobalExceptionHandler | Giữ ApiError hiện có, chỉ thêm handler cho validation/payment/409 | Không thay đổi format lỗi API cũ | Test JSON và các mã lỗi 400/402/404/409 |
| Có nên tăng thời gian giữ lock bằng cách không trừ ghế trước payment | Trừ ghế sau payment để dễ rollback | Trừ ghế sau payment và giữ lock suốt transaction | Tránh state tạm thành công trước gateway và rollback vẫn an toàn | Test payment failure giữ nguyên booking count và seats |
