package vn.QL_KyTucXa.Security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import vn.QL_KyTucXa.DTO.Request.Room_Request;
import vn.QL_KyTucXa.DTO.Request.StudentContractApply_Request;
import vn.QL_KyTucXa.DTO.Response.Contract_Response;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.DTO.Response.Room_Response;
import vn.QL_KyTucXa.DTO.Response.User_Response;
import vn.QL_KyTucXa.Service.Contract_Service;
import vn.QL_KyTucXa.Service.CurrentUser_Service;
import vn.QL_KyTucXa.Service.Room_Service;
import vn.QL_KyTucXa.Service.User_Service;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class PreAuthorizeSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private User_Service userService;

    @MockitoBean
    private Room_Service roomService;

    @MockitoBean
    private Contract_Service contractService;

    @MockitoBean
    private CurrentUser_Service currentUserService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("GET /api/users - Chưa đăng nhập (Unauthenticated) bị từ chối 401 UNAUTHENTICATED")
    void getUsers_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1004));
    }

    @Test
    @DisplayName("GET /api/users - Sinh viên (STUDENT) gọi endpoint yêu cầu ADMIN bị từ chối 403 FORBIDDEN")
    @WithMockUser(username = "sinhvien", roles = {"STUDENT"})
    void getUsers_asStudent_returns403() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1005));
    }

    @Test
    @DisplayName("GET /api/users - Nhân viên (STAFF) gọi endpoint yêu cầu ADMIN bị từ chối 403 FORBIDDEN")
    @WithMockUser(username = "nhanvien", roles = {"STAFF"})
    void getUsers_asStaff_returns403() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1005));
    }

    @Test
    @DisplayName("GET /api/users - Quản trị viên (ADMIN) truy cập thành công 200 OK")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getUsers_asAdmin_returns200() throws Exception {
        when(userService.getAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(100));
    }

    @Test
    @DisplayName("GET /api/rooms - Công khai (permitAll) truy cập không cần đăng nhập thành công")
    void getRooms_anonymous_returns200() throws Exception {
        when(roomService.getAll(1, 10, "roomNumber", "asc")).thenReturn(
                Page_Response.<Room_Response>builder()
                        .data(Collections.emptyList())
                        .currentPage(1)
                        .totalPages(0)
                        .totalElements(0)
                        .pageSize(10)
                        .build()
        );

        mockMvc.perform(get("/api/rooms"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/rooms - Sinh viên gọi tạo phòng (yêu cầu ADMIN hoặc STAFF) bị từ chối 403")
    @WithMockUser(username = "sinhvien", roles = {"STUDENT"})
    void createRoom_asStudent_returns403() throws Exception {
        Room_Request request = Room_Request.builder()
                .floorId("f-01")
                .roomTypeId("rt-01")
                .roomNumber("P101")
                .capacity(4)
                .status("AVAILABLE")
                .build();

        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1005));
    }

    @Test
    @DisplayName("POST /api/rooms - Nhân viên (STAFF) có quyền ROOM_MANAGE gọi tạo phòng thành công 200 OK")
    @WithMockUser(username = "nhanvien", authorities = {"ROLE_STAFF", "ROOM_MANAGE"})
    void createRoom_asStaff_returns200() throws Exception {
        Room_Request request = Room_Request.builder()
                .floorId("f-01")
                .roomTypeId("rt-01")
                .roomNumber("P101")
                .capacity(4)
                .status("AVAILABLE")
                .build();

        when(roomService.create(any(Room_Request.class))).thenReturn(
                Room_Response.builder().roomId("r-01").roomNumber("P101").build()
        );

        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(100));
    }

    @Test
    @DisplayName("POST /api/contracts/apply - Sinh viên (STUDENT) nộp đơn đăng ký hợp đồng trực tuyến thành công 200 OK (không bị 403)")
    @WithMockUser(username = "sinhvien", roles = {"STUDENT"})
    void applyOnline_asStudent_notForbidden() throws Exception {
        StudentContractApply_Request request = StudentContractApply_Request.builder()
                .bedId("bed-01")
                .periodId("period-01")
                .notes("Đăng ký ở")
                .build();

        when(currentUserService.studentId()).thenReturn("st-01");
        when(contractService.applyOnline(any(), any())).thenReturn(
                Contract_Response.builder().contractId("ct-01").contractCode("HD-01").status("PENDING").build()
        );

        mockMvc.perform(post("/api/contracts/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(100));
    }
}
