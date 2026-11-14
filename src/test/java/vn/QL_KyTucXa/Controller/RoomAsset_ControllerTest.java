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
import vn.QL_KyTucXa.DTO.Request.RoomAsset_Request;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.DTO.Response.RoomAsset_Response;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Exception.GlobalExceptionHandler;
import vn.QL_KyTucXa.Service.RoomAsset_Service;

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
class RoomAsset_ControllerTest {

        private MockMvc mockMvc;

        @Mock
        private RoomAsset_Service roomAssetService;

        @InjectMocks
        private RoomAsset_Controller roomAssetController;

        private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        private RoomAsset_Request request;
        private RoomAsset_Response response;

        @BeforeEach
        void setUp() {
                mockMvc = MockMvcBuilders.standaloneSetup(roomAssetController)
                                .setControllerAdvice(new GlobalExceptionHandler())
                                .build();

                request = RoomAsset_Request.builder()
                                .roomId("room-01")
                                .assetId("asset-01")
                                .quantity(2)
                                .assignedDate(LocalDate.now().minusDays(1))
                                .conditionNote("Tốt")
                                .build();

                response = RoomAsset_Response.builder()
                                .roomAssetId("ra-01")
                                .roomId("room-01")
                                .roomNumber("P101")
                                .assetId("asset-01")
                                .assetCode("GHE-01")
                                .assetName("Ghế văn phòng")
                                .quantity(2)
                                .assignedDate(LocalDate.now().minusDays(1))
                                .conditionNote("Tốt")
                                .build();
        }

        @Test
        @DisplayName("POST /api/room-assets - Gán tài sản cho phòng thành công")
        void assignAssetToRoom_success() throws Exception {
                when(roomAssetService.create(any(RoomAsset_Request.class))).thenReturn(response);

                String json = """
                                {
                                    "roomId": "room-01",
                                    "assetId": "asset-01",
                                    "quantity": 2,
                                    "assignedDate": "2026-11-01",
                                    "conditionNote": "Tốt"
                                }
                                """;

                mockMvc.perform(post("/api/room-assets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(100))
                                .andExpect(jsonPath("$.result.roomAssetId").value("ra-01"))
                                .andExpect(jsonPath("$.result.quantity").value(2));
        }

        @Test
        @DisplayName("GET /api/room-assets - Lấy danh sách có phân trang và bộ lọc thành công")
        void getAllRoomAssets_paged_success() throws Exception {
                Page_Response<RoomAsset_Response> pageResponse = Page_Response.<RoomAsset_Response>builder()
                                .currentPage(1)
                                .pageSize(10)
                                .totalPages(1)
                                .totalElements(1)
                                .data(List.of(response))
                                .build();

                when(roomAssetService.getAll(1, 10, "bldg-01", "room-01", "asset-01", "assignedDate", "desc"))
                                .thenReturn(pageResponse);

                mockMvc.perform(get("/api/room-assets")
                                .param("page", "1")
                                .param("size", "10")
                                .param("buildingId", "bldg-01")
                                .param("roomId", "room-01")
                                .param("assetId", "asset-01"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(100))
                                .andExpect(jsonPath("$.result.data[0].roomAssetId").value("ra-01"));
        }

        @Test
        @DisplayName("GET /api/room-assets/all - Lấy danh sách không phân trang thành công")
        void getAllRoomAssetsWithoutPaging_success() throws Exception {
                when(roomAssetService.getAll()).thenReturn(List.of(response));

                mockMvc.perform(get("/api/room-assets/all"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(100))
                                .andExpect(jsonPath("$.result[0].roomAssetId").value("ra-01"));
        }

        @Test
        @DisplayName("GET /api/room-assets/{id} - Lấy chi tiết thành công")
        void getRoomAssetById_success() throws Exception {
                when(roomAssetService.getById("ra-01")).thenReturn(response);

                mockMvc.perform(get("/api/room-assets/ra-01"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(100))
                                .andExpect(jsonPath("$.result.roomAssetId").value("ra-01"));
        }

        @Test
        @DisplayName("GET /api/room-assets/room/{roomId} - Lấy tài sản theo phòng thành công")
        void getAssetsByRoom_success() throws Exception {
                when(roomAssetService.getByRoomId("room-01")).thenReturn(List.of(response));

                mockMvc.perform(get("/api/room-assets/room/room-01"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(100))
                                .andExpect(jsonPath("$.result[0].roomId").value("room-01"));
        }

        @Test
        @DisplayName("GET /api/room-assets/asset/{assetId} - Lấy các phòng đang dùng tài sản thành công")
        void getAssetsByAssetId_success() throws Exception {
                when(roomAssetService.getByAssetId("asset-01")).thenReturn(List.of(response));

                mockMvc.perform(get("/api/room-assets/asset/asset-01"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(100))
                                .andExpect(jsonPath("$.result[0].assetId").value("asset-01"));
        }

        @Test
        @DisplayName("PUT /api/room-assets/{id} - Cập nhật thành công")
        void updateRoomAsset_success() throws Exception {
                when(roomAssetService.update(eq("ra-01"), any(RoomAsset_Request.class))).thenReturn(response);

                String json = """
                                {
                                    "roomId": "room-01",
                                    "assetId": "asset-01",
                                    "quantity": 2,
                                    "assignedDate": "2026-11-01",
                                    "conditionNote": "Tốt"
                                }
                                """;

                mockMvc.perform(put("/api/room-assets/ra-01")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(100))
                                .andExpect(jsonPath("$.result.roomAssetId").value("ra-01"));
        }

        @Test
        @DisplayName("DELETE /api/room-assets/{id} - Xóa thành công")
        void deleteRoomAsset_success() throws Exception {
                doNothing().when(roomAssetService).delete("ra-01");

                mockMvc.perform(delete("/api/room-assets/ra-01"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(100))
                                .andExpect(jsonPath("$.result").value("ra-01"));
        }
}
