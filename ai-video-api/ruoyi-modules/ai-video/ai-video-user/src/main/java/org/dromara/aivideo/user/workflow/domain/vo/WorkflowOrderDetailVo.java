package org.dromara.aivideo.user.workflow.domain.vo;

import org.dromara.aivideo.workflow.order.dto.WorkflowOrderDetailDTO;

import java.util.List;

/** Explicit user HTTP view for a workflow order detail. */
public record WorkflowOrderDetailVo(
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
    public static WorkflowOrderDetailVo from(WorkflowOrderDetailDTO source) {
        return new WorkflowOrderDetailVo(source.orderId(), source.orderNo(), source.createdAt(),
            Template.from(source.template()), source.inputs().stream().map(Input::from).toList(),
            Task.from(source.task()), source.outputs().stream().map(Asset::from).toList(),
            source.canCancel(), source.canRemake());
    }

    public record Template(String templateId, String title, Media cover) {
        private static Template from(WorkflowOrderDetailDTO.Template source) {
            return new Template(source.templateId(), source.title(), Media.from(source.cover()));
        }
    }

    public record Media(String mediaId, String mediaType, String url, String posterUrl, int width,
                        int height, String alt) {
        private static Media from(WorkflowOrderDetailDTO.Media source) {
            return source == null ? null : new Media(source.mediaId(), source.mediaType(), source.url(),
                source.posterUrl(), source.width(), source.height(), source.alt());
        }
    }

    public record Input(String inputKey, String label, String displayValue, List<Asset> assets) {
        private static Input from(WorkflowOrderDetailDTO.Input source) {
            return new Input(source.inputKey(), source.label(), source.displayValue(),
                source.assets().stream().map(Asset::from).toList());
        }
    }

    public record Task(String taskId, String taskType, String status, String stage, Integer progressPercent,
                       String failureCode, String failureMessage, boolean retryable, String createdAt,
                       String updatedAt) {
        private static Task from(WorkflowOrderDetailDTO.Task source) {
            return new Task(source.taskId(), source.taskType(), source.status(), source.stage(),
                source.progressPercent(), source.failureCode(), source.failureMessage(), source.retryable(),
                source.createdAt(), source.updatedAt());
        }
    }

    public record Asset(String assetId, String label, String mediaType, String fileName, String sizeBytes,
                        String status, boolean primary) {
        private static Asset from(WorkflowOrderDetailDTO.Asset source) {
            return new Asset(source.assetId(), source.label(), source.mediaType(), source.fileName(),
                source.sizeBytes(), source.status(), source.primary());
        }
    }
}
