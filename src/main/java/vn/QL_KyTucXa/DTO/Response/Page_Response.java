package vn.QL_KyTucXa.DTO.Response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Collections;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Page_Response<T> {

    int currentPage;        // Trang hiện tại (1, 2, 3...)
    int pageSize;           // Số lượng phần tử mỗi trang (10, 20, 50...)
    int totalPages;         // Tổng số trang tính theo pageSize
    long totalElements;     // Tổng số bản ghi tìm được trong database

    boolean hasPrevious;    // Có trang trước đó hay không (để disable nút Prev trên UI)
    boolean hasNext;        // Có trang kế tiếp hay không (để disable nút Next trên UI)

    @JsonProperty("isFirst")
    boolean isFirst;        // Đang ở trang đầu tiên hay không

    @JsonProperty("isLast")
    boolean isLast;         // Đang ở trang cuối cùng hay không

    @JsonProperty("first")
    public boolean getFirst() {
        return isFirst;
    }

    @JsonProperty("last")
    public boolean getLast() {
        return isLast;
    }

    @Builder.Default
    List<T> data = Collections.emptyList(); // Danh sách dữ liệu của trang hiện tại
}
