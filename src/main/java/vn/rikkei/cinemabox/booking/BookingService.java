package vn.rikkei.cinemabox.booking;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.rikkei.cinemabox.common.ResourceNotFoundException;
import vn.rikkei.cinemabox.customer.Customer;
import vn.rikkei.cinemabox.customer.CustomerRepository;
import vn.rikkei.cinemabox.payment.PaymentGateway;
import vn.rikkei.cinemabox.showtime.Showtime;
import vn.rikkei.cinemabox.showtime.ShowtimeRepository;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final CustomerRepository customerRepository;
    private final ShowtimeRepository showtimeRepository;
    private final PaymentGateway paymentGateway;

    public BookingService(BookingRepository bookingRepository,
                          CustomerRepository customerRepository,
                          ShowtimeRepository showtimeRepository,
                          PaymentGateway paymentGateway) {
        this.bookingRepository = bookingRepository;
        this.customerRepository = customerRepository;
        this.showtimeRepository = showtimeRepository;
        this.paymentGateway = paymentGateway;
    }

    @Transactional
    public BookingResponse create(BookingRequest request) {
        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách hàng id=" + request.customerId()));

        Showtime showtime = showtimeRepository.findByIdForUpdate(request.showtimeId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy suất chiếu id=" + request.showtimeId()));

        if (showtime.getAvailableSeats() == null || showtime.getAvailableSeats() < request.quantity()) {
            throw new NotEnoughSeatsException("Không đủ ghế trống cho suất chiếu id=" + request.showtimeId());
        }

        long totalAmount = calculateTotal(showtime.getPrice(), request.quantity(), customer.getMemberLevel());
        String paymentRef = paymentGateway.charge(customer.getId(), totalAmount);

        Booking booking = new Booking();
        booking.setCustomer(customer);
        booking.setShowtime(showtime);
        booking.setQuantity(request.quantity());
        booking.setTotalAmount(totalAmount);
        booking.setStatus("CONFIRMED");
        booking.setPaymentRef(paymentRef);

        showtime.setAvailableSeats(showtime.getAvailableSeats() - request.quantity());
        Booking saved = bookingRepository.save(booking);
        return BookingResponse.from(saved);
    }

    @Transactional
    public BookingResponse cancel(Long bookingId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy booking id=" + bookingId));

        if (!"CONFIRMED".equals(booking.getStatus())) {
            throw new InvalidBookingStateException("Booking không ở trạng thái CONFIRMED");
        }

        Showtime showtime = showtimeRepository.findByIdForUpdate(booking.getShowtime().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy suất chiếu id=" + booking.getShowtime().getId()));

        booking.setStatus("CANCELLED");
        showtime.setAvailableSeats(showtime.getAvailableSeats() + booking.getQuantity());
        return BookingResponse.from(bookingRepository.save(booking));
    }

    static long calculateTotal(long ticketPrice, int quantity, String memberLevel) {
        BigDecimal beforeDiscount = BigDecimal.valueOf(ticketPrice).multiply(BigDecimal.valueOf(quantity));
        if ("GOLD".equalsIgnoreCase(memberLevel)) {
            return beforeDiscount.multiply(BigDecimal.valueOf(90))
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR)
                    .longValueExact();
        }
        return beforeDiscount.setScale(0, RoundingMode.UNNECESSARY).longValueExact();
    }
}

