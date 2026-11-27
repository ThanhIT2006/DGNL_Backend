package vn.rikkei.cinemabox;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class BookingApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void resetTestMutations() {
        jdbc.update("DELETE FROM bookings WHERE id > 15");
        int[] values = {35,60,42,48,55,20,8,70,100,0,25,30,19};
        for (int i = 0; i < values.length; i++) {
            jdbc.update("UPDATE showtimes SET available_seats = ? WHERE id = ?", values[i], i + 1);
        }
    }

    @Test
    void validationFieldsAndMalformedJson() throws Exception {
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":2,\"showtimeId\":9,\"quantity\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":2,\"showtimeId\":9,\"quantity\":11}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content("{bad json}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    void customerAndShowtimeNotFound() throws Exception {
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":999,\"showtimeId\":9,\"quantity\":1}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("NOT_FOUND"));
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":2,\"showtimeId\":999,\"quantity\":1}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void standardAndGoldBookingTotalsAndPaymentRef() throws Exception {
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":2,\"showtimeId\":9,\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(160000))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.paymentRef", is(org.hamcrest.Matchers.startsWith("PAY-"))));

        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":1,\"showtimeId\":9,\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(144000));

        Integer seats = jdbc.queryForObject("SELECT available_seats FROM showtimes WHERE id = 9", Integer.class);
        assertEquals(96, seats);
    }

    @Test
    void notEnoughSeatsAndNoNegativeSeat() throws Exception {
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":2,\"showtimeId\":10,\"quantity\":1}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("NOT_ENOUGH_SEATS"));
        Integer seats = jdbc.queryForObject("SELECT available_seats FROM showtimes WHERE id = 10", Integer.class);
        assertTrue(seats >= 0);
        assertEquals(0, seats);
    }

    @Test
    void paymentFailureRollsBackSeatsAndBooking() throws Exception {
        Integer seatsBefore = jdbc.queryForObject("SELECT available_seats FROM showtimes WHERE id = 11", Integer.class);
        Integer bookingsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM bookings", Integer.class);

        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":2,\"showtimeId\":11,\"quantity\":10}"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error").value("PAYMENT_FAILED"));

        Integer seatsAfter = jdbc.queryForObject("SELECT available_seats FROM showtimes WHERE id = 11", Integer.class);
        Integer bookingsAfter = jdbc.queryForObject("SELECT COUNT(*) FROM bookings", Integer.class);
        Integer confirmedAfter = jdbc.queryForObject("SELECT COUNT(*) FROM bookings WHERE showtime_id = 11 AND status = 'CONFIRMED' AND payment_ref IS NOT NULL", Integer.class);
        assertEquals(seatsBefore, seatsAfter);
        assertEquals(bookingsBefore, bookingsAfter);
        assertEquals(1, confirmedAfter); // một booking CONFIRMED seed của showtime 11
    }

    @Test
    void concurrentBookingDoesNotOversell() throws Exception {
        jdbc.update("UPDATE showtimes SET available_seats = 5 WHERE id = 13");
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatchReady latch = new CountDownLatchReady(10);

        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> {
                latch.await();
                return mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"customerId\":2,\"showtimeId\":13,\"quantity\":1}"))
                        .andReturn().getResponse().getStatus();
            });
        }

        List<Future<Integer>> futures = new ArrayList<>();
        for (Callable<Integer> task : tasks) {
            futures.add(pool.submit(task));
        }
        latch.ready();

        int created = 0;
        int conflict = 0;
        for (Future<Integer> future : futures) {
            int statusCode = future.get();
            if (statusCode == 201) created++;
            if (statusCode == 409) conflict++;
        }
        pool.shutdown();

        assertEquals(5, created);
        assertEquals(5, conflict);
        Integer remaining = jdbc.queryForObject("SELECT available_seats FROM showtimes WHERE id = 13", Integer.class);
        Integer confirmed = jdbc.queryForObject("SELECT COALESCE(SUM(quantity),0) FROM bookings WHERE showtime_id = 13 AND status = 'CONFIRMED'", Integer.class);
        assertEquals(0, remaining);
        assertEquals(5, confirmed);
        Integer missingRef = jdbc.queryForObject("SELECT COUNT(*) FROM bookings WHERE showtime_id = 13 AND status='CONFIRMED' AND payment_ref IS NULL", Integer.class);
        assertEquals(0, missingRef);
    }

    @Test
    void cancelConfirmedBookingReturnsSeatOnce() throws Exception {
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":2,\"showtimeId\":9,\"quantity\":2}"))
                .andExpect(status().isCreated());
        Long id = jdbc.queryForObject("SELECT MAX(id) FROM bookings", Long.class);
        Integer before = jdbc.queryForObject("SELECT available_seats FROM showtimes WHERE id = 9", Integer.class);

        mvc.perform(post("/api/bookings/{id}/cancel", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(post("/api/bookings/{id}/cancel", id))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("INVALID_STATE"));

        Integer after = jdbc.queryForObject("SELECT available_seats FROM showtimes WHERE id = 9", Integer.class);
        assertEquals(before + 2, after);
    }

    private static class CountDownLatchReady {
        private final java.util.concurrent.CountDownLatch latch;
        CountDownLatchReady(int count) { latch = new java.util.concurrent.CountDownLatch(count); }
        void await() throws InterruptedException { latch.await(); }
        void ready() { latch.countDown(); latch.countDown(); latch.countDown(); latch.countDown(); latch.countDown(); latch.countDown(); latch.countDown(); latch.countDown(); latch.countDown(); latch.countDown(); }
    }
}
