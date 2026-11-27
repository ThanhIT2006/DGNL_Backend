package vn.rikkei.cinemabox.showtime;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.rikkei.cinemabox.common.ResourceNotFoundException;
@Transactional(readOnly = true)
@Service
public class ShowtimeService {
    private final ShowtimeRepository showtimeRepository;

    public ShowtimeService(ShowtimeRepository showtimeRepository) {
        this.showtimeRepository = showtimeRepository;
    }
    public ShowtimeResponse getById(Long id) {
        Showtime showtime = showtimeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy suất chiếu id=" + id));
        return ShowtimeResponse.from(showtime);
    }

}
