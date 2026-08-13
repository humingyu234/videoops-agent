package org.dromara.aivideo.workflow.order.dto;

import java.util.List;

/** User-safe workflow order detail returned across the user HTTP boundary. */
public record WorkflowOrderDetailDTO(
    String orderId,
    String orderNo,
    String createdAt,
    Template template,
    List<Input> inputs,
    Task task,
    List<Asset> outputs,
    boolean canCancel,
    boolean canRemake
) {
    public record Template(String templateId, String title, Media cover) {
    }

    public record Media(String mediaId, String mediaType, String url, String posterUrl, int width,
                        int height, String alt) {
    }

    public record Input(String inputKey, String label, String displayValue, List<Asset> assets) {
    }

    public record Task(String taskId, String taskType, String status, String stage, Integer progressPercent,
                       String failureCode, String failureMessage, boolean retryable, String createdAt,
                       String updatedAt) {
    }

    public record Asset(String assetId, String label, String mediaType, String fileName, String sizeBytes,
                        String status, boolean primary) {
    }
}
