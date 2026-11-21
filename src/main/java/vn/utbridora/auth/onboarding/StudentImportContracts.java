package vn.utbridora.auth.onboarding;

import java.time.Instant;
import java.util.List;

/** Exact CP-IMPORT1 wire records. Token/hash/ciphertext transport never enters these values. */
public final class StudentImportContracts {
    private StudentImportContracts() {}
    public record ImportSourceData(String studentCode,String email,String fullName,String majorCode,String termCode,String cohortCode,String classCode) {}
    public record ImportRowDto(String rowId,int rowNo,long rowVersion,ImportSourceData sourceData,String validationState,
        boolean selected,String rowState,List<String> errorCodes,String rosterId,String provisioningEffectId,String userId,
        String provisioningOutcome,String activationState,Long invitationVersion,String currentMailId,String mailState) {}
    public record Page<T>(List<T> content,int page,int size,long totalElements,int totalPages) {}
    public record ImportJobDto(String importJobId,long jobVersion,String jobState,String fileHash,String termId,Instant previewExpiresAt,
        int totalRows,int validRows,int invalidRows,int previewNeedsReviewRows,int selectedRows,int queuedRows,int processingRows,
        int transientFailedRows,int provisionedRows,int alreadyLinkedRows,int needsReviewRows,int permanentFailedRows,int processedRows,
        int successRows,Instant startedAt,Instant finishedAt,Instant observedAt) {}
    public record ImportPreviewDto(ImportJobDto job,Page<ImportRowDto> rows) {}
    public record ConfirmImport(Long expectedJobVersion,String fileHash,List<String> selectedRowIds) {}
    public record ImportAccepted(String importJobId,long jobVersion,String jobState,int selectedRows) {}
    public record ResumeImport(Long expectedJobVersion) {}
    public record ResendActivation(Long expectedRowVersion,Long expectedInvitationVersion) {}
    public record ResendAccepted(String rowId,long rowVersion,long invitationVersion,String mailId,String mailState,Instant acceptedAt) {}
}
