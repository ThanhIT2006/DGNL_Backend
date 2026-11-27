package vn.rikkei.cinemabox.booking;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BookingRequest(
        @NotNull(message = "customerId là bắt buộc")
        @Positive(message = "customerId không hợp lệ")
        Long customerId,

        @NotNull(message = "showtimeId là bắt buộc")
        @Positive(message = "showtimeId không hợp lệ")
        Long showtimeId,

        @NotNull(message = "quantity là bắt buộc")
        @Min(value = 1, message = "quantity phải từ 1 đến 10")
        @Max(value = 10, message = "quantity phải từ 1 đến 10")
        Integer quantity
) {
}

