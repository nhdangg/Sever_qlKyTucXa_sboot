package vn.QL_KyTucXa.DTO.Response;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentBatch_Response {
    private int total;
    private int successCount;
    private int failedCount;
    private List<String> successStudentCodes;
    private List<StudentBatchError> errors;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StudentBatchError {
        private int row;
        private String studentCode;
        private String reason;
    }
}

