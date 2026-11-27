-- F1. Lệnh CREATE TABLE reviews (xem đề). Chỉ tạo bảng reviews, không sửa bảng có sẵn.
CREATE TABLE reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    movie_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    rating INT NOT NULL CHECK (rating >= 1 AND rating <= 5),
    comment VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reviews_movie FOREIGN KEY (movie_id) REFERENCES movies (id),
    CONSTRAINT fk_reviews_customer FOREIGN KEY (customer_id) REFERENCES customers (id)
);