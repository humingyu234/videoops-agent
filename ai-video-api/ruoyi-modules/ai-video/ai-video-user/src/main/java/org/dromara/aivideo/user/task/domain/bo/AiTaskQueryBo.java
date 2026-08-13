package org.dromara.aivideo.user.task.domain.bo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiTaskQueryBo {

    @Size(max = 64)
    private String taskType;

    @Size(max = 32)
    private String status;

    @Size(max = 64)
    private String keyword;

    @Min(1)
    private Integer pageNum;

    @Min(1)
    @Max(100)
    private Integer pageSize;
}
