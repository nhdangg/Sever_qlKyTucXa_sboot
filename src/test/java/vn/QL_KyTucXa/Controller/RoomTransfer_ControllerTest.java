package vn.QL_KyTucXa.Controller;

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
import vn.QL_KyTucXa.DTO.Request.RoomTransfer_Request;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.DTO.Response.RoomTransfer_Response;
import vn.QL_KyTucXa.Exception.GlobalExceptionHandler;
import vn.QL_KyTucXa.Service.CurrentUser_Service;
import vn.QL_KyTucXa.Service.RoomTransfer_Service;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RoomTransfer_ControllerTest {

    private MockMvc mockMvc;

    @Mock
    private RoomTransfer_Service roomTransferService;

    @Mock
    private CurrentUser_Service currentUserService;

    @InjectMocks
    private RoomTransfer_Controller roomTransferController;

    private RoomTransfer_Response response;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(roomTransferController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        response = RoomTransfer_Response.builder()
                .transferId("tf-01")
                .contractId("ct-01")
                .oldBedId("bed-01")
                .newBedId("bed-02")
                .status("PENDING")
                .transferDate(LocalDate.now())
                .build();
    }

    @Test
    @DisplayName("POST /api/room-transfers - Sinh viên gửi yêu cầu chuyển phòng thành công")
    void requestTransfer_asStudent_success() throws Exception {
        when(currentUserService.hasRole("STUDENT")).thenReturn(true);
        when(currentUserService.studentId()).thenReturn("sv-01");
        when(roomTransferService.createForStudent(eq("sv-01"), any(RoomTransfer_Request.class))).thenReturn(response);

        String json = """
                {
                    "contractId": "ct-01",
                    "newBedId": "bed-02",
                    "transferDate": "2026-11-06",
                    "reason": "Đổi phòng mới"
                }
                """;

        mockMvc.perform(post("/api/room-transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(100))
                .andExpect(jsonPath("$.result.transferId").value("tf-01"));
    }

    @Test
    @DisplayName("GET /api/room-transfers/my - Sinh viên xem yêu cầu của mình")
    void getMyTransfers_success() throws Exception {
        when(currentUserService.studentId()).thenReturn("sv-01");
        when(roomTransferService.getByStudentId("sv-01")).thenReturn(List.of(response));

        mockMvc.perform(get("/api/room-transfers/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(100))
                .andExpect(jsonPath("$.result[0].transferId").value("tf-01"));
    }

    @Test
    @DisplayName("GET /api/room-transfers - Quản trị viên tìm kiếm và phân trang yêu cầu chuyển phòng")
    void getAllTransfers_search_success() throws Exception {
        Page_Response<RoomTransfer_Response> pageResponse = Page_Response.<RoomTransfer_Response>builder()
                .currentPage(1)
                .pageSize(10)
                .totalPages(1)
                .totalElements(1)
                .data(List.of(response))
                .build();

        when(roomTransferService.getAll(1, 10, "PENDING", "SV001", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), "createdAt", "desc"))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/room-transfers")
                        .param("page", "1")
                        .param("size", "10")
                        .param("status", "PENDING")
                        .param("student", "SV001")
                        .param("fromDate", "2026-11-01")
                        .param("toDate", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(100))
                .andExpect(jsonPath("$.result.data[0].transferId").value("tf-01"));
    }

    @Test
    @DisplayName("PUT /api/room-transfers/{id}/approve - Quản lý duyệt chuyển phòng")
    void approveTransfer_success() throws Exception {
        response.setStatus("APPROVED");
        when(roomTransferService.approve("tf-01", "staff-01")).thenReturn(response);

        mockMvc.perform(put("/api/room-transfers/tf-01/approve")
                        .param("staffId", "staff-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(100))
                .andExpect(jsonPath("$.result.status").value("APPROVED"));
    }

    @Test
    @DisplayName("PUT /api/room-transfers/{id}/reject - Quản lý từ chối chuyển phòng")
    void rejectTransfer_success() throws Exception {
        response.setStatus("REJECTED");
        when(roomTransferService.reject(eq("tf-01"), eq("staff-01"), any())).thenReturn(response);

        mockMvc.perform(put("/api/room-transfers/tf-01/reject")
                        .param("staffId", "staff-01")
                        .param("reason", "Phòng đã đủ người"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(100))
                .andExpect(jsonPath("$.result.status").value("REJECTED"));
    }

    @Test
    @DisplayName("PUT /api/room-transfers/{id}/cancel - Sinh viên hủy yêu cầu của mình")
    void cancelTransfer_success() throws Exception {
        when(currentUserService.hasRole("STUDENT")).thenReturn(true);
        when(currentUserService.studentId()).thenReturn("sv-01");
        response.setStatus("CANCELLED");
        when(roomTransferService.cancel("tf-01", "sv-01")).thenReturn(response);

        mockMvc.perform(put("/api/room-transfers/tf-01/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(100))
                .andExpect(jsonPath("$.result.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("DELETE /api/room-transfers/{id} - Xóa yêu cầu chuyển phòng")
    void deleteTransfer_success() throws Exception {
        doNothing().when(roomTransferService).delete("tf-01");

        mockMvc.perform(delete("/api/room-transfers/tf-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(100))
                .andExpect(jsonPath("$.result").value("tf-01"));
    }
}

