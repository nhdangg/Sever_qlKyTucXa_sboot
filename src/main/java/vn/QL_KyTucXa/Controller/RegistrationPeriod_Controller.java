package vn.QL_KyTucXa.Controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.QL_KyTucXa.DTO.Api_Response;
import vn.QL_KyTucXa.DTO.Request.RegistrationPeriod_Request;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.DTO.Response.RegistrationPeriod_Response;
import vn.QL_KyTucXa.Service.RegistrationPeriod_Service;

import java.util.List;

@RestController
@RequestMapping("/api/registration-periods")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RegistrationPeriod_Controller {

        RegistrationPeriod_Service periodService;

        @PostMapping
        @PreAuthorize("hasAuthority('CONTRACT_MANAGE')")
        public Api_Response<RegistrationPeriod_Response> createPeriod(
                        @RequestBody @Valid RegistrationPeriod_Request request) {
                return Api_Response.<RegistrationPeriod_Response>builder()
                                .result(periodService.create(request))
                                .build();
        }

        @PostMapping("/sync-statuses")
        @PreAuthorize("hasAuthority('CONTRACT_MANAGE') or hasRole('ADMIN')")
        public Api_Response<Integer> syncPeriodStatuses() {
                return Api_Response.<Integer>builder()
                                .result(periodService.autoUpdatePeriodStatuses())
                                .build();
        }

        @GetMapping("/{periodId}")
        @PreAuthorize("isAuthenticated()")
        public Api_Response<RegistrationPeriod_Response> getPeriodById(@PathVariable String periodId) {
                return Api_Response.<RegistrationPeriod_Response>builder()
                                .result(periodService.getById(periodId))
                                .build();
        }

        @GetMapping
        @PreAuthorize("isAuthenticated()")
        public Api_Response<Page_Response<RegistrationPeriod_Response>> getAllPeriods(
                        @RequestParam(value = "page", defaultValue = "1") int page,
                        @RequestParam(value = "size", defaultValue = "10") int size,
                        @RequestParam(value = "academicYear", required = false) String academicYear,
                        @RequestParam(value = "status", required = false) String status,
                        @RequestParam(value = "sortBy", defaultValue = "startDate") String sortBy,
                        @RequestParam(value = "sortDir", defaultValue = "desc") String sortDir) {
                return Api_Response.<Page_Response<RegistrationPeriod_Response>>builder()
                                .result(periodService.getAll(page, size, academicYear, status, sortBy, sortDir))
                                .build();
        }

        @GetMapping("/all")
        @PreAuthorize("isAuthenticated()")
        public Api_Response<List<RegistrationPeriod_Response>> getAllPeriodsWithoutPaging(
                        @RequestParam(value = "academicYear", required = false) String academicYear,
                        @RequestParam(value = "status", required = false) String status) {
                return Api_Response.<List<RegistrationPeriod_Response>>builder()
                                .result(periodService.getAll(academicYear, status))
                                .build();
        }

        @PutMapping("/{periodId}")
        @PreAuthorize("hasAuthority('CONTRACT_MANAGE')")
        public Api_Response<RegistrationPeriod_Response> updatePeriod(
                        @PathVariable String periodId,
                        @RequestBody @Valid RegistrationPeriod_Request request) {
                return Api_Response.<RegistrationPeriod_Response>builder()
                                .result(periodService.update(periodId, request))
                                .build();
        }

        @DeleteMapping("/{periodId}")
        @PreAuthorize("hasAuthority('CONTRACT_MANAGE')")
        public Api_Response<String> deletePeriod(@PathVariable String periodId) {
                periodService.delete(periodId);
                return Api_Response.<String>builder()
                                .result(periodId)
                                .build();
        }
}
