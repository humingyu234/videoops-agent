package org.dromara.aivideo.user.creation.domain.vo;

import org.dromara.aivideo.creation.dto.CreationOutputDTO;

public record LatestCreationOutputVo(
    String projectId,
    String outputAssetId,
    String taskId,
    String createdAt
) {

    public static LatestCreationOutputVo from(CreationOutputDTO output) {
        return new LatestCreationOutputVo(output.projectId(), output.outputAssetId(), output.taskId(),
            output.createdAt().toString());
    }
}
