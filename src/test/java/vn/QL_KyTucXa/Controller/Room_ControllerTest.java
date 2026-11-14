package vn.QL_KyTucXa.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.QL_KyTucXa.DTO.Request.Room_Request;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.DTO.Response.Room_Response;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Exception.GlobalExceptionHandler;
import vn.QL_KyTucXa.Service.Room_Service;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class Room_ControllerTest {

    private MockMvc mockMvc;

    @Mock
    private Room_Service roomService;

    @InjectMocks
    private Room_Controller roomController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Room_Request request;
    private Room_Response response;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(roomController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        request = Room_Request.builder()
                .floorId("floor-01")
                .roomTypeId("type-01")
                .roomNumber("P201")
                .capacity(4)
                .status("AVAILABLE")
                .build();

        response = Room_Response.builder()
                .roomId("room-201")
                .roomNumber("P201")
                .capacity(4)
                .status("AVAILABLE")
                .build();
    }

    @Test
    @DisplayName("POST /api/rooms - Tạo phòng thành công")
    void createRoom_success() throws Exception {
        when(roomService.create(any(Room_Request.class))).thenReturn(response);

        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(100))
                .andExpect(jsonPath("$.result.roomNumber").value("P201"));
    }

    @Test
    @DisplayName("GET /api/rooms/{roomId} - Lấy chi tiết phòng thành công")
    void getRoomById_success() throws Exception {
        when(roomService.getById("room-201")).thenReturn(response);

        mockMvc.perform(get("/api/rooms/room-201"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(100))
                .andExpect(jsonPath("$.result.roomId").value("room-201"));
    }

    @Test
    @DisplayName("GET /api/rooms/{roomId} - Không tìm thấy phòng trả về HTTP 404")
    void getRoomById_notFound() throws Exception {
        when(roomService.getById("unknown-id")).thenThrow(new AppException(ErrorCode.ROOM_NOT_FOUND));

        mockMvc.perform(get("/api/rooms/unknown-id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1008));
    }

    @Test
    @DisplayName("GET /api/rooms/floor/{floorId} - Lấy danh sách phòng theo tầng thành công")
    void getRoomsByFloor_success() throws Exception {
        when(roomService.getByFloorId("floor-01")).thenReturn(List.of(response));

        mockMvc.perform(get("/api/rooms/floor/floor-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(100))
                .andExpect(jsonPath("$.result[0].roomId").value("room-201"));
    }

    @Test
    @DisplayName("GET /api/rooms - Lấy danh sách phòng có phân trang mặc định")
    void getAllRooms_defaultPaging_success() throws Exception {
        Page_Response<Room_Response> pageResponse = Page_Response.<Room_Response>builder()
                .currentPage(1)
                .pageSize(10)
                .totalPages(1)
                .totalElements(1)
                .data(List.of(response))
                .build();

        when(roomService.getAll(1, 10, "roomNumber", "asc")).thenReturn(pageResponse);

        mockMvc.perform(get("/api/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(100))
                .andExpect(jsonPath("$.result.data[0].roomNumber").value("P201"));
    }

    @Test
    @DisplayName("GET /api/rooms - Tìm kiếm phòng theo đa tiêu chí")
    void getAllRooms_withSearchFilters_success() throws Exception {
        Page_Response<Room_Response> pageResponse = Page_Response.<Room_Response>builder()
                .currentPage(1)
                .pageSize(10)
                .totalPages(1)
                .totalElements(1)
                .data(List.of(response))
                .build();

        when(roomService.getAll(1, 10, "201", "bldg-01", "floor-01", "type-01", "AVAILABLE", true, "roomNumber", "asc"))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/rooms")
                        .param("roomNumber", "201")
                        .param("buildingId", "bldg-01")
                        .param("floorId", "floor-01")
                        .param("roomTypeId", "type-01")
                        .param("status", "AVAILABLE")
                        .param("hasAvailableBeds", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(100))
                .andExpect(jsonPath("$.result.data[0].roomNumber").value("P201"));
    }

    @Test
    @DisplayName("GET /api/rooms/all - Lấy danh sách tất cả phòng")
    void getAllRoomsWithoutPaging_success() throws Exception {
        when(roomService.getAll()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/rooms/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(100))
                .andExpect(jsonPath("$.result[0].roomNumber").value("P201"));
    }

    @Test
    @DisplayName("PUT /api/rooms/{roomId} - Cập nhật thông tin phòng thành công")
    void updateRoom_success() throws Exception {
        when(roomService.update(eq("room-201"), any(Room_Request.class))).thenReturn(response);

        mockMvc.perform(put("/api/rooms/room-201")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(100))
                .andExpect(jsonPath("$.result.roomId").value("room-201"));
    }

    @Test
    @DisplayName("PATCH /api/rooms/{roomId}/status - Cập nhật trạng thái phòng thành công")
    void updateRoomStatus_success() throws Exception {
        when(roomService.updateStatus("room-201", "MAINTENANCE")).thenReturn(response);

        mockMvc.perform(patch("/api/rooms/room-201/status")
                        .param("status", "MAINTENANCE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(100));
    }

    @Test
    @DisplayName("DELETE /api/rooms/{roomId} - Xóa phòng thành công")
    void deleteRoom_success() throws Exception {
        doNothing().when(roomService).delete("room-201");

        mockMvc.perform(delete("/api/rooms/room-201"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(100))
                .andExpect(jsonPath("$.result").value("room-201"));
    }
}
