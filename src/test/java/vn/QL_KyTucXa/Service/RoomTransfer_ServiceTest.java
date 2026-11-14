package vn.QL_KyTucXa.Service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import vn.QL_KyTucXa.DTO.Request.RoomTransfer_Request;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.DTO.Response.RoomTransfer_Response;
import vn.QL_KyTucXa.Entity.*;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Mapper.RoomTransfer_Mapper;
import vn.QL_KyTucXa.Repository.Bed_Repository;
import vn.QL_KyTucXa.Repository.Contract_Repository;
import vn.QL_KyTucXa.Repository.RoomTransfer_Repository;
import vn.QL_KyTucXa.Repository.Staff_Repository;

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
class RoomTransfer_ServiceTest {

    @Mock
    private RoomTransfer_Repository roomTransferRepository;

    @Mock
    private Contract_Repository contractRepository;

    @Mock
    private Bed_Repository bedRepository;

    @Mock
    private vn.QL_KyTucXa.Repository.Room_Repository roomRepository;

    @Mock
    private Staff_Repository staffRepository;

    @Mock
    private CurrentUser_Service currentUserService;

    @Mock
    private RoomTransfer_Mapper roomTransferMapper;

    @InjectMocks
    private RoomTransfer_Service roomTransferService;

    private Student studentMale;
    private Student studentFemale;
    private Building buildingMale;
    private Building buildingFemale;
    private Floor floor1;
    private RoomType roomTypeStandard;
    private RoomType roomTypeVip;
    private Room roomOld;
    private Room roomNew;
    private Bed bedOld;
    private Bed bedNew;
    private Contract contract;
    private RoomTransfer transfer;
    private RoomTransfer_Request request;
    private RoomTransfer_Response response;
    private Staff staff;

    @BeforeEach
    void setUp() {
        studentMale = Student.builder()
                .studentId("sv-male")
                .studentCode("SV001")
                .fullName("Nguyễn Văn Nam")
                .gender("MALE")
                .build();

        studentFemale = Student.builder()
                .studentId("sv-female")
                .studentCode("SV002")
                .fullName("Trần Thị Nữ")
                .gender("FEMALE")
                .build();

        buildingMale = Building.builder()
                .buildingId("bldg-male")
                .buildingName("Tòa Nhà Nam")
                .genderType("MALE")
                .build();

        buildingFemale = Building.builder()
                .buildingId("bldg-female")
                .buildingName("Tòa Nhà Nữ")
                .genderType("FEMALE")
                .build();

        floor1 = Floor.builder()
                .floorId("floor-01")
                .building(buildingMale)
                .build();

        roomTypeStandard = RoomType.builder()
                .roomTypeId("rt-std")
                .typeName("Phòng tiêu chuẩn")
                .pricePerMonth(new BigDecimal("600000.00"))
                .build();

        roomTypeVip = RoomType.builder()
                .roomTypeId("rt-vip")
                .typeName("Phòng cao cấp")
                .pricePerMonth(new BigDecimal("900000.00"))
                .build();

        roomOld = Room.builder()
                .roomId("room-101")
                .roomNumber("P101")
                .floor(floor1)
                .roomType(roomTypeStandard)
                .build();

        roomNew = Room.builder()
                .roomId("room-102")
                .roomNumber("P102")
                .floor(floor1)
                .roomType(roomTypeVip)
                .build();

        bedOld = Bed.builder()
                .bedId("bed-01")
                .bedNumber("G01")
                .room(roomOld)
                .status("OCCUPIED")
                .build();

        bedNew = Bed.builder()
                .bedId("bed-02")
                .bedNumber("G02")
                .room(roomNew)
                .status("AVAILABLE")
                .build();

        contract = Contract.builder()
                .contractId("ct-01")
                .contractCode("HD-001")
                .student(studentMale)
                .bed(bedOld)
                .monthlyFee(new BigDecimal("600000.00"))
                .status("ACTIVE")
                .build();

        transfer = RoomTransfer.builder()
                .transferId("tf-01")
                .contract(contract)
                .oldBed(bedOld)
                .newBed(bedNew)
                .transferDate(LocalDate.now())
                .reason("Muốn đổi phòng rộng hơn")
                .status("PENDING")
                .build();

        request = RoomTransfer_Request.builder()
                .contractId("ct-01")
                .newBedId("bed-02")
                .transferDate(LocalDate.now())
                .reason("Muốn đổi phòng rộng hơn")
                .build();

        response = RoomTransfer_Response.builder()
                .transferId("tf-01")
                .contractId("ct-01")
                .oldBedId("bed-01")
                .newBedId("bed-02")
                .status("PENDING")
                .build();

        staff = Staff.builder()
                .staffId("staff-01")
                .fullName("Quản Lý Ký Túc Xá")
                .build();
    }

    @Test
    @DisplayName("Sinh viên tạo yêu cầu chuyển phòng thành công")
    void createForStudent_success() {
        when(contractRepository.findById("ct-01")).thenReturn(Optional.of(contract));
        when(roomTransferRepository.existsByContract_ContractIdAndStatus("ct-01", "PENDING")).thenReturn(false);
        when(bedRepository.findById("bed-02")).thenReturn(Optional.of(bedNew));
        when(roomTransferMapper.toEntity(any(RoomTransfer_Request.class))).thenReturn(transfer);
        when(roomTransferRepository.save(any(RoomTransfer.class))).thenReturn(transfer);
        when(roomTransferMapper.toResponse(transfer)).thenReturn(response);

        RoomTransfer_Response result = roomTransferService.createForStudent("sv-male", request);

        assertThat(result).isNotNull();
        assertThat(result.getTransferId()).isEqualTo("tf-01");
        verify(roomTransferRepository).save(any(RoomTransfer.class));
    }

    @Test
    @DisplayName("Chống IDOR: Sinh viên cố tình tạo yêu cầu chuyển phòng cho hợp đồng của người khác")
    void createForStudent_failed_idor() {
        when(contractRepository.findById("ct-01")).thenReturn(Optional.of(contract)); // contract của sv-male

        AppException ex = assertThrows(AppException.class,
                () -> roomTransferService.createForStudent("sv-female", request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("không thuộc quyền sở hữu");
    }

    @Test
    @DisplayName("Tạo yêu cầu thất bại khi hợp đồng không ở trạng thái ACTIVE")
    void createForStudent_failed_contractNotActive() {
        contract.setStatus("TERMINATED");
        when(contractRepository.findById("ct-01")).thenReturn(Optional.of(contract));

        AppException ex = assertThrows(AppException.class,
                () -> roomTransferService.createForStudent("sv-male", request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("ACTIVE");
    }

    @Test
    @DisplayName("Chặn tạo nhiều yêu cầu PENDING cùng lúc cho một hợp đồng")
    void create_failed_multiplePending() {
        when(contractRepository.findById("ct-01")).thenReturn(Optional.of(contract));
        when(roomTransferRepository.existsByContract_ContractIdAndStatus("ct-01", "PENDING")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> roomTransferService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("đang có yêu cầu chuyển phòng chờ xử lý");
    }

    @Test
    @DisplayName("Chặn chọn giường mới trùng với giường cũ (newBed == oldBed)")
    void create_failed_sameBed() {
        request.setNewBedId("bed-01"); // trùng bedOld
        when(contractRepository.findById("ct-01")).thenReturn(Optional.of(contract));
        when(roomTransferRepository.existsByContract_ContractIdAndStatus("ct-01", "PENDING")).thenReturn(false);
        bedOld.setStatus("AVAILABLE");
        when(bedRepository.findById("bed-01")).thenReturn(Optional.of(bedOld));

        AppException ex = assertThrows(AppException.class, () -> roomTransferService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("không được trùng với giường hiện tại");
    }

    @Test
    @DisplayName("Chặn chuyển sang tòa nhà không đúng giới tính của sinh viên")
    void create_failed_genderMismatch() {
        // Gắn phòng đích vào Tòa Nữ trong khi sinh viên là Nam
        Floor floorFemale = Floor.builder().floorId("f-female").building(buildingFemale).build();
        roomNew.setFloor(floorFemale);

        when(contractRepository.findById("ct-01")).thenReturn(Optional.of(contract));
        when(roomTransferRepository.existsByContract_ContractIdAndStatus("ct-01", "PENDING")).thenReturn(false);
        when(bedRepository.findById("bed-02")).thenReturn(Optional.of(bedNew));

        AppException ex = assertThrows(AppException.class, () -> roomTransferService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("chỉ dành cho sinh viên Nữ");
    }

    @Test
    @DisplayName("Tạo yêu cầu thất bại khi ngày chuyển ở trong quá khứ")
    void create_failed_pastDate() {
        request.setTransferDate(LocalDate.now().minusDays(3));
        when(contractRepository.findById("ct-01")).thenReturn(Optional.of(contract));
        when(roomTransferRepository.existsByContract_ContractIdAndStatus("ct-01", "PENDING")).thenReturn(false);
        when(bedRepository.findById("bed-02")).thenReturn(Optional.of(bedNew));

        AppException ex = assertThrows(AppException.class, () -> roomTransferService.create(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("không được ở trong quá khứ");
    }

    @Test
    @DisplayName("Duyệt chuyển phòng thành công: khóa bi quan, chuyển giường, cập nhật giá thuê mới")
    void approve_success() {
        when(roomTransferRepository.findById("tf-01")).thenReturn(Optional.of(transfer));
        when(bedRepository.findByIdForUpdate("bed-02")).thenReturn(Optional.of(bedNew));
        when(staffRepository.findById("staff-01")).thenReturn(Optional.of(staff));
        when(roomTransferRepository.save(any(RoomTransfer.class))).thenReturn(transfer);
        when(roomTransferMapper.toResponse(transfer)).thenReturn(response);

        RoomTransfer_Response result = roomTransferService.approve("tf-01", "staff-01");

        assertThat(result).isNotNull();
        assertThat(bedOld.getStatus()).isEqualTo("AVAILABLE");
        assertThat(bedNew.getStatus()).isEqualTo("OCCUPIED");
        assertThat(contract.getBed()).isEqualTo(bedNew);
        assertThat(contract.getMonthlyFee()).isEqualTo(new BigDecimal("900000.00")); // Cập nhật theo loại phòng VIP
        assertThat(transfer.getStatus()).isEqualTo("APPROVED");
        verify(bedRepository).save(bedOld);
        verify(bedRepository).save(bedNew);
        verify(contractRepository).save(contract);
    }

    @Test
    @DisplayName("Duyệt chuyển phòng thất bại khi đơn không ở trạng thái PENDING")
    void approve_failed_notPending() {
        transfer.setStatus("REJECTED"); // Đơn đã bị từ chối
        when(roomTransferRepository.findById("tf-01")).thenReturn(Optional.of(transfer));

        AppException ex = assertThrows(AppException.class, () -> roomTransferService.approve("tf-01", "staff-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("PENDING");
    }

    @Test
    @DisplayName("Duyệt chuyển phòng thất bại khi ngày chuyển ở tương lai")
    void approve_failed_futureDate() {
        transfer.setTransferDate(LocalDate.now().plusDays(5));
        when(roomTransferRepository.findById("tf-01")).thenReturn(Optional.of(transfer));

        AppException ex = assertThrows(AppException.class, () -> roomTransferService.approve("tf-01", "staff-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("Chưa đến ngày chuyển phòng");
    }

    @Test
    @DisplayName("Duyệt chuyển phòng thất bại khi giường đích đã bị người khác chiếm (xung đột đồng thời)")
    void approve_failed_concurrentOccupied() {
        bedNew.setStatus("OCCUPIED");
        when(roomTransferRepository.findById("tf-01")).thenReturn(Optional.of(transfer));
        when(bedRepository.findByIdForUpdate("bed-02")).thenReturn(Optional.of(bedNew));

        AppException ex = assertThrows(AppException.class, () -> roomTransferService.approve("tf-01", "staff-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BED_ALREADY_OCCUPIED);
    }

    @Test
    @DisplayName("Duyệt chuyển phòng thất bại khi giường cũ của sinh viên không khớp với giường trên đơn")
    void approve_failed_whenOldBedMismatch() {
        Bed anotherBed = Bed.builder().bedId("bed-99").status("OCCUPIED").build();
        contract.setBed(anotherBed); // hợp đồng đang ở giường khác với transfer.oldBed ("bed-01")

        when(roomTransferRepository.findById("tf-01")).thenReturn(Optional.of(transfer));

        AppException ex = assertThrows(AppException.class, () -> roomTransferService.approve("tf-01", "staff-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("không khớp với giường cũ");
    }

    @Test
    @DisplayName("Duyệt chuyển phòng thất bại khi phòng đích đang ở trạng thái bảo trì hoặc đóng cửa")
    void approve_failed_whenNewRoomMaintenance() {
        roomNew.setStatus("MAINTENANCE");
        when(roomTransferRepository.findById("tf-01")).thenReturn(Optional.of(transfer));
        when(bedRepository.findByIdForUpdate("bed-02")).thenReturn(Optional.of(bedNew));

        AppException ex = assertThrows(AppException.class, () -> roomTransferService.approve("tf-01", "staff-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("bảo trì hoặc tạm đóng");
    }

    @Test
    @DisplayName("Từ chối chuyển phòng thành công: bảo tồn lý do gốc của sinh viên bằng nối chuỗi")
    void reject_success() {
        when(roomTransferRepository.findById("tf-01")).thenReturn(Optional.of(transfer));
        when(staffRepository.findById("staff-01")).thenReturn(Optional.of(staff));
        when(roomTransferRepository.save(any(RoomTransfer.class))).thenReturn(transfer);
        when(roomTransferMapper.toResponse(transfer)).thenReturn(response);

        RoomTransfer_Response result = roomTransferService.reject("tf-01", "staff-01", "Phòng mới đang sửa điện");

        assertThat(result).isNotNull();
        assertThat(transfer.getStatus()).isEqualTo("REJECTED");
        assertThat(transfer.getReason()).contains("Muốn đổi phòng rộng hơn"); // Lý do gốc của SV
        assertThat(transfer.getReason()).contains("Lý do từ chối: Phòng mới đang sửa điện");
    }

    @Test
    @DisplayName("Từ chối thất bại khi đơn không ở trạng thái PENDING")
    void reject_failed_notPending() {
        transfer.setStatus("APPROVED");
        when(roomTransferRepository.findById("tf-01")).thenReturn(Optional.of(transfer));

        AppException ex = assertThrows(AppException.class,
                () -> roomTransferService.reject("tf-01", "staff-01", "Lý do"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("PENDING");
    }

    @Test
    @DisplayName("Sinh viên hủy yêu cầu chuyển phòng của chính mình thành công khi còn PENDING")
    void cancel_success() {
        when(roomTransferRepository.findById("tf-01")).thenReturn(Optional.of(transfer));
        when(roomTransferRepository.save(any(RoomTransfer.class))).thenReturn(transfer);
        when(roomTransferMapper.toResponse(transfer)).thenReturn(response);

        RoomTransfer_Response result = roomTransferService.cancel("tf-01", "sv-male");

        assertThat(result).isNotNull();
        assertThat(transfer.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("Sinh viên hủy đơn thất bại khi đơn của người khác")
    void cancel_failed_otherStudent() {
        when(roomTransferRepository.findById("tf-01")).thenReturn(Optional.of(transfer));

        AppException ex = assertThrows(AppException.class, () -> roomTransferService.cancel("tf-01", "sv-female"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("của chính mình");
    }

    @Test
    @DisplayName("Xóa yêu cầu thành công khi ở trạng thái PENDING hoặc CANCELLED")
    void delete_success() {
        when(roomTransferRepository.findById("tf-01")).thenReturn(Optional.of(transfer));

        roomTransferService.delete("tf-01");

        verify(roomTransferRepository).delete(transfer);
    }

    @Test
    @DisplayName("Xóa yêu cầu thất bại khi đơn đã APPROVED hoặc REJECTED")
    void delete_failed_approved() {
        transfer.setStatus("APPROVED");
        when(roomTransferRepository.findById("tf-01")).thenReturn(Optional.of(transfer));

        AppException ex = assertThrows(AppException.class, () -> roomTransferService.delete("tf-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("PENDING hoặc CANCELLED");
    }

    @Test
    @DisplayName("Lấy danh sách yêu cầu chuyển phòng theo sinh viên trực tiếp không N+1")
    void getByStudentId_success() {
        when(roomTransferRepository.findByContract_Student_StudentId("sv-male")).thenReturn(List.of(transfer));
        when(roomTransferMapper.toResponseList(anyList())).thenReturn(List.of(response));

        List<RoomTransfer_Response> result = roomTransferService.getByStudentId("sv-male");

        assertThat(result).hasSize(1);
        verify(roomTransferRepository).findByContract_Student_StudentId("sv-male");
    }

    @Test
    @DisplayName("Tìm kiếm và phân trang yêu cầu chuyển phòng theo tiêu chí")
    void getAll_search_success() {
        Page<RoomTransfer> page = new PageImpl<>(List.of(transfer));
        when(roomTransferRepository.searchRoomTransfers(eq("PENDING"), eq("SV001"), any(), any(), any(Pageable.class)))
                .thenReturn(page);
        when(roomTransferMapper.toResponseList(anyList())).thenReturn(List.of(response));

        Page_Response<RoomTransfer_Response> result = roomTransferService.getAll(1, 10, "PENDING", "SV001", null, null, "createdAt", "desc");

        assertThat(result).isNotNull();
        assertThat(result.getData()).hasSize(1);
    }

    @Test
    @DisplayName("Lấy danh sách chuyển phòng theo sinh viên thất bại khi sinh viên xem của người khác")
    void getByStudentId_failed_unauthorizedForOtherStudent() {
        when(currentUserService.hasRole("STUDENT")).thenReturn(true);
        when(currentUserService.findStudentId()).thenReturn(Optional.of("my-stu-id"));

        AppException ex = assertThrows(AppException.class, () -> roomTransferService.getByStudentId("other-stu-id"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Lấy yêu cầu chuyển phòng theo ID thất bại khi sinh viên xem của người khác")
    void getById_failed_unauthorizedForOtherStudent() {
        when(roomTransferRepository.findById("tf-01")).thenReturn(Optional.of(transfer));
        when(currentUserService.hasRole("STUDENT")).thenReturn(true);
        when(currentUserService.findStudentId()).thenReturn(Optional.of("other-stu-id"));

        AppException ex = assertThrows(AppException.class, () -> roomTransferService.getById("tf-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Lấy danh sách chuyển phòng theo hợp đồng thất bại khi sinh viên xem của người khác")
    void getByContractId_failed_unauthorizedForOtherStudent() {
        when(contractRepository.findById("ct-01")).thenReturn(Optional.of(contract));
        when(currentUserService.hasRole("STUDENT")).thenReturn(true);
        when(currentUserService.findStudentId()).thenReturn(Optional.of("other-stu-id"));

        AppException ex = assertThrows(AppException.class, () -> roomTransferService.getByContractId("ct-01"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
    }
}

