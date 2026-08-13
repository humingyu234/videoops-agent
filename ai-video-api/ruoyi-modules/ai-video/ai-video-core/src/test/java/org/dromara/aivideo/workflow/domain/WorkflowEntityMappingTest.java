package org.dromara.aivideo.workflow.domain;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class WorkflowEntityMappingTest {

    @Test
    void inheritedAuditFieldsNotPresentInWorkflowTablesAreExcludedFromMetadata() {
        assertThat(columns(WorkflowTemplate.class)).doesNotContain("create_dept");
        assertThat(columns(WorkflowExecutionConfig.class)).doesNotContain("create_dept");
        assertThat(columns(RunningHubAccount.class)).doesNotContain("create_dept");
        assertThat(columns(DiscoveryCategory.class)).doesNotContain("create_dept", "create_by", "update_by");
        assertThat(columns(DiscoveryTag.class)).doesNotContain("create_dept", "create_by", "update_by");
    }

    private static Iterable<String> columns(Class<?> entityType) {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(entityType);
        if (tableInfo == null) {
            tableInfo = TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityType);
        }
        return tableInfo.getFieldList().stream().map(field -> field.getColumn()).toList();
    }
}
