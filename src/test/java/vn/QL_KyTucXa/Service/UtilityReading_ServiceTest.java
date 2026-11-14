package vn.QL_KyTucXa.Service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.QL_KyTucXa.DTO.Request.UtilityReading_Request;
import vn.QL_KyTucXa.DTO.Response.UtilityReading_Response;
import vn.QL_KyTucXa.Entity.Room;
import vn.QL_KyTucXa.Entity.ServiceType;
import vn.QL_KyTucXa.Entity.UtilityReading;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.Room_Mapper;
import vn.QL_KyTucXa.Mapper.UtilityReading_Mapper;
import vn.QL_KyTucXa.Repository.Contract_Repository;
import vn.QL_KyTucXa.Repository.Invoice_Repository;
import vn.QL_KyTucXa.Repository.Room_Repository;
import vn.QL_KyTucXa.Repository.ServiceType_Repository;
import vn.QL_KyTucXa.Repository.Staff_Repository;
import vn.QL_KyTucXa.Repository.UtilityReading_Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UtilityReading_ServiceTest {

    @Mock
    private UtilityReading_Repository readingRepository;
    @Mock
    private Room_Repository roomRepository;
    @Mock
    private ServiceType_Repository serviceTypeRepository;
    @Mock
    private Staff_Repository staffRepository;
    @Mock
    private Contract_Repository contractRepository;
    @Mock
    private Invoice_Repository invoiceRepository;
    @Mock
    private CurrentUser_Service currentUserService;
    @Mock
    private Room_Mapper roomMapper;
    @Mock
    private UtilityReading_Mapper readingMapper;

    @InjectMocks
    private UtilityReading_Service utilityReadingService;

    private Room room;
    private ServiceType serviceType;
    private UtilityReading reading;
    private UtilityReading_Request request;
    private UtilityReading_Response response;

    @BeforeEach
    void setUp() {
        room = Room.builder().roomId("room-01").build();
        serviceType = ServiceType.builder()
                .serviceTypeId("srv-dien")
                .serviceName("Điện")
                .billingType("METERED")
                .build();

        reading = UtilityReading.builder()
                .readingId("read-01")
                .room(room)
                .serviceType(serviceType)
                .readingMonth(LocalDate.of(2026, 10, 1))
                .previousIndex(BigDecimal.valueOf(100))
                .currentIndex(BigDecimal.valueOf(150))
                .consumption(BigDecimal.valueOf(50))
                .build();

        request = UtilityReading_Request.builder()
                .roomId("room-01")
                .serviceTypeId("srv-dien")
                .readingMonth(LocalDate.of(2026, 10, 1))
                .previousIndex(BigDecimal.valueOf(100))
                .currentIndex(BigDecimal.valueOf(150))
                .build();

        response = UtilityReading_Response.builder()
                .readingId("read-01")
                .consumption(BigDecimal.valueOf(50))
                .build();
    }

    @Test
    @DisplayName("Ghi chỉ số điện nước thành công")
    void create_success() {
        when(roomRepository.findById("room-01")).thenReturn(Optional.of(room));
        when(serviceTypeRepository.findById("srv-dien")).thenReturn(Optional.of(serviceType));
        when(readingRepository.findByRoom_RoomIdAndServiceType_ServiceTypeIdAndReadingMonth(eq("room-01"), eq("srv-dien"), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(readingRepository.save(any(UtilityReading.class))).thenReturn(reading);
        when(readingMapper.toResponse(any(UtilityReading.class))).thenReturn(response);
        when(currentUserService.findStaffId()).thenReturn(Optional.empty());

        UtilityReading_Response result = utilityReadingService.create(request);

        assertThat(result).isNotNull();
        assertThat(result.getConsumption()).isEqualTo(BigDecimal.valueOf(50));
        verify(readingRepository).save(any(UtilityReading.class));
    }

    @Test
    @DisplayName("Ghi chỉ số thất bại khi phòng không tồn tại")
    void create_failed_roomNotFound() {
        when(roomRepository.findById("room-01")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> utilityReadingService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("Ghi chỉ số thất bại khi loại dịch vụ không tồn tại")
    void create_failed_serviceTypeNotFound() {
        when(roomRepository.findById("room-01")).thenReturn(Optional.of(room));
        when(serviceTypeRepository.findById("srv-dien")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> utilityReadingService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("Ghi chỉ số thất bại khi đã có chỉ số tháng này")
    void create_failed_alreadyExists() {
        when(roomRepository.findById("room-01")).thenReturn(Optional.of(room));
        when(serviceTypeRepository.findById("srv-dien")).thenReturn(Optional.of(serviceType));
        when(readingRepository.findByRoom_RoomIdAndServiceType_ServiceTypeIdAndReadingMonth("room-01", "srv-dien", LocalDate.of(2026, 10, 1)))
                .thenReturn(Optional.of(reading));

        AppException ex = assertThrows(AppException.class, () -> utilityReadingService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Lấy chỉ số theo ID thành công")
    void getById_success() {
        when(readingRepository.findById("read-01")).thenReturn(Optional.of(reading));
        when(readingMapper.toResponse(reading)).thenReturn(response);

        UtilityReading_Response result = utilityReadingService.getById("read-01");

        assertThat(result).isNotNull();
        assertThat(result.getReadingId()).isEqualTo("read-01");
    }

    @Test
    @DisplayName("Xóa chỉ số thành công")
    void delete_success() {
        when(readingRepository.findById("read-01")).thenReturn(Optional.of(reading));
        when(invoiceRepository.existsIssuedInvoiceForRoomAndMonth(any(), any())).thenReturn(false);

        utilityReadingService.delete("read-01");

        verify(readingRepository).delete(reading);
    }

    @Test
    @DisplayName("Chia tiền điện nước dồn phần dư lẻ cho người đầu tiên: tổng các phần luôn bằng 100% hóa đơn")
    void calculateRoomUtilitySplit_success_withRemainderDistributed() {
        serviceType.setUnitPrice(new BigDecimal("2000.00")); // 50 * 2000 = 100,000
        when(roomRepository.findById("room-01")).thenReturn(Optional.of(room));
        when(readingRepository.findByRoom_RoomIdAndServiceType_ServiceTypeIdAndReadingMonth(
                eq("room-01"), eq("srv-dien"), any(LocalDate.class)))
                .thenReturn(Optional.of(reading));

        // Giả lập 3 sinh viên ở trong phòng
        vn.QL_KyTucXa.Entity.Student s1 = vn.QL_KyTucXa.Entity.Student.builder().studentId("st-1").studentCode("SV1").fullName("Nguyễn A").build();
        vn.QL_KyTucXa.Entity.Student s2 = vn.QL_KyTucXa.Entity.Student.builder().studentId("st-2").studentCode("SV2").fullName("Trần B").build();
        vn.QL_KyTucXa.Entity.Student s3 = vn.QL_KyTucXa.Entity.Student.builder().studentId("st-3").studentCode("SV3").fullName("Lê C").build();

        vn.QL_KyTucXa.Entity.Contract c1 = vn.QL_KyTucXa.Entity.Contract.builder().contractId("ct-1").student(s1).status("ACTIVE").build();
        vn.QL_KyTucXa.Entity.Contract c2 = vn.QL_KyTucXa.Entity.Contract.builder().contractId("ct-2").student(s2).status("ACTIVE").build();
        vn.QL_KyTucXa.Entity.Contract c3 = vn.QL_KyTucXa.Entity.Contract.builder().contractId("ct-3").student(s3).status("ACTIVE").build();

        when(contractRepository.findEffectiveContractsByRoomIdAndMonth(eq("room-01"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(c1, c2, c3));

        vn.QL_KyTucXa.DTO.Response.UtilitySplit_Response result =
                utilityReadingService.calculateRoomUtilitySplit("room-01", "srv-dien", LocalDate.of(2026, 10, 1));

        assertThat(result).isNotNull();
        assertThat(result.getTotalAmount()).isEqualByComparingTo(new BigDecimal("100000.00"));
        assertThat(result.getActiveResidentCount()).isEqualTo(3);
        assertThat(result.getResidentShares()).hasSize(3);

        // 100,000 / 3 = 33,333.33 (dư 0.01)
        // Người 1: 33,333.34; Người 2: 33,333.33; Người 3: 33,333.33
        BigDecimal share1 = result.getResidentShares().get(0).getShareAmount();
        BigDecimal share2 = result.getResidentShares().get(1).getShareAmount();
        BigDecimal share3 = result.getResidentShares().get(2).getShareAmount();

        assertThat(share1).isEqualByComparingTo(new BigDecimal("33333.34"));
        assertThat(share2).isEqualByComparingTo(new BigDecimal("33333.33"));
        assertThat(share3).isEqualByComparingTo(new BigDecimal("33333.33"));

        // Tổng 3 người phải tuyệt đối bằng tổng hóa đơn phòng
        BigDecimal sum = share1.add(share2).add(share3);
        assertThat(sum).isEqualByComparingTo(result.getTotalAmount());
    }

    @Test
    @DisplayName("Ghi chỉ số khi bỏ quên 1 tháng: tự động lấy chỉ số cuối của tháng gần nhất trước đó")
    void create_monthSkipped_usesMostRecentPriorReading() {
        request.setReadingMonth(LocalDate.of(2026, 12, 1));
        request.setPreviousIndex(null); // Client không truyền
        request.setCurrentIndex(BigDecimal.valueOf(200));

        UtilityReading novemberReading = UtilityReading.builder()
                .readingId("read-oct")
                .room(room)
                .serviceType(serviceType)
                .readingMonth(LocalDate.of(2026, 10, 1))
                .currentIndex(BigDecimal.valueOf(130))
                .build();

        when(roomRepository.findById("room-01")).thenReturn(Optional.of(room));
        when(serviceTypeRepository.findById("srv-dien")).thenReturn(Optional.of(serviceType));
        when(readingRepository.findByRoom_RoomIdAndServiceType_ServiceTypeIdAndReadingMonth(
                eq("room-01"), eq("srv-dien"), eq(LocalDate.of(2026, 12, 1))))
                .thenReturn(Optional.empty());
        // Tháng 11 không có, findTopByRoom...LessThan trả về tháng 10
        when(readingRepository.findTopByRoom_RoomIdAndServiceType_ServiceTypeIdAndReadingMonthLessThanOrderByReadingMonthDesc(
                eq("room-01"), eq("srv-dien"), eq(LocalDate.of(2026, 12, 1))))
                .thenReturn(Optional.of(novemberReading));
        when(readingRepository.save(any(UtilityReading.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(readingMapper.toResponse(any(UtilityReading.class))).thenReturn(response);
        when(currentUserService.findStaffId()).thenReturn(Optional.empty());

        UtilityReading_Response res = utilityReadingService.create(request);

        assertThat(res).isNotNull();
        verify(readingRepository).save(argThat(savedReading ->
                savedReading.getPreviousIndex().compareTo(BigDecimal.valueOf(130)) == 0 &&
                savedReading.getConsumption().compareTo(BigDecimal.valueOf(70)) == 0
        ));
    }

    @Test
    @DisplayName("Lấy danh sách phòng chưa ghi chỉ số: loại bỏ phòng CLOSED và phòng không có người ở")
    void getUnrecordedRooms_excludesClosedAndUnoccupiedRooms() {
        Room activeRoomWithResidents = Room.builder().roomId("r-active").status("AVAILABLE").build();
        Room emptyRoom = Room.builder().roomId("r-empty").status("AVAILABLE").build();
        Room closedRoom = Room.builder().roomId("r-closed").status("CLOSED").build();

        when(roomRepository.findAll()).thenReturn(List.of(activeRoomWithResidents, emptyRoom, closedRoom));
        when(contractRepository.countActiveByRoomIds(anyList())).thenReturn(List.<Object[]>of(
                new Object[]{"r-active", 2L},
                new Object[]{"r-empty", 0L}
        ));
        when(readingRepository.findByReadingMonth(LocalDate.of(2026, 10, 1))).thenReturn(List.of());
        when(roomMapper.toResponseList(List.of(activeRoomWithResidents))).thenReturn(List.of(
                vn.QL_KyTucXa.DTO.Response.Room_Response.builder().roomId("r-active").build()
        ));

        List<vn.QL_KyTucXa.DTO.Response.Room_Response> result =
                utilityReadingService.getUnrecordedRooms(LocalDate.of(2026, 10, 1), null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRoomId()).isEqualTo("r-active");
    }
}
