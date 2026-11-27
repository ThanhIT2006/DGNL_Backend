package vn.rikkei.cinemabox.showtime;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ShowtimeResponse(
    Long id,
    String moviestitle,
    String roomname,
    LocalDateTime starttime,
    Long price,
    Integer availableSeats){
    public static ShowtimeResponse from(Showtime s) {
        return new ShowtimeResponse(
            s.getId(),
            s.getMovie().getTitle(),
            s.getRoom().getName(),
            s.getStartTime(),
            s.getPrice(),
            s.getAvailableSeats()
        );
    }
}
