package org.dromara.aivideo.task.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreType;

@JsonIgnoreType
public final class AiTaskLeaseDTO {

    private final String taskId;
    private final String executionId;
    private final String attemptId;
    private final String leaseToken;
    private final String workerId;
    private final String actorType;
    private final String actorId;
    private final String inputVersionId;
    private final int executionNo;
    private final int attemptNo;
    private final int rowVersion;

    public AiTaskLeaseDTO(String taskId, String executionId, String attemptId, String leaseToken,
                          String workerId, String actorType, String actorId, String inputVersionId,
                          int executionNo, int attemptNo, int rowVersion) {
        this.taskId = taskId;
        this.executionId = executionId;
        this.attemptId = attemptId;
        this.leaseToken = leaseToken;
        this.workerId = workerId;
        this.actorType = actorType;
        this.actorId = actorId;
        this.inputVersionId = inputVersionId;
        this.executionNo = executionNo;
        this.attemptNo = attemptNo;
        this.rowVersion = rowVersion;
    }

    /** Timeline compatibility constructor; new generic code must freeze actorType explicitly. */
    public AiTaskLeaseDTO(String taskId, String executionId, String attemptId, String leaseToken,
                          String workerId, String actorId, String inputVersionId,
                          int executionNo, int attemptNo, int rowVersion) {
        this(taskId, executionId, attemptId, leaseToken, workerId, "app_user", actorId, inputVersionId,
            executionNo, attemptNo, rowVersion);
    }

    public String getTaskId() {
        return taskId;
    }

    public String getExecutionId() {
        return executionId;
    }

    public String getAttemptId() {
        return attemptId;
    }

    public String getLeaseToken() {
        return leaseToken;
    }

    public String getWorkerId() {
        return workerId;
    }

    public String getActorId() {
        return actorId;
    }

    public String getActorType() {
        return actorType;
    }

    public String getInputVersionId() {
        return inputVersionId;
    }

    public int getExecutionNo() {
        return executionNo;
    }

    public int getAttemptNo() {
        return attemptNo;
    }

    public int getRowVersion() {
        return rowVersion;
    }
}
