-- I1. Phim thể loại ACTION (xem đề). Viết MỘT câu SELECT bên dưới, kết thúc bằng dấu ;
SELECT title,current_date FROM movies WHERE genre = 'ACTION' AND release_date > '2020-01-01' ORDER BY release_date ASC ;
