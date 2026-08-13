package org.dromara.aivideo.task;

import org.dromara.aivideo.task.domain.AiTask;
import org.dromara.aivideo.task.domain.AiTaskAttempt;
import org.dromara.aivideo.task.domain.AiTaskExecution;
import org.dromara.aivideo.task.mapper.AiTaskAttemptMapper;
import org.dromara.aivideo.task.mapper.AiTaskExecutionMapper;
import org.dromara.aivideo.task.mapper.AiTaskMapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class AiTaskMapperContractTest {
    @Test
    void taskMappersUseEntityMapperContract() {
        assertThat(AiTaskMapper.class.getGenericInterfaces()[0].getTypeName()).contains("BaseMapperPlus");
        assertThat(AiTaskExecutionMapper.class.getGenericInterfaces()[0].getTypeName()).contains("BaseMapperPlus");
        assertThat(AiTaskAttemptMapper.class.getGenericInterfaces()[0].getTypeName()).contains("BaseMapperPlus");
        assertThat(BaseMapperPlus.class).isNotNull();
        assertThat(AiTask.class).isNotNull();
        assertThat(AiTaskExecution.class).isNotNull();
        assertThat(AiTaskAttempt.class).isNotNull();
    }
}
