package org.dromara.aivideo.asset.domain;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class WorkflowUploadEntityMappingTest {

    @Test
    void uploadFactsPersistTheOpaqueWorkspaceKeyWithoutInheritedAuditColumns() {
        assertThat(columns(FileObject.class)).contains("file_id", "workspace_id", "owner_user_id", "object_key")
            .doesNotContain("create_dept", "create_by", "update_by");
        assertThat(columns(UploadSession.class)).contains(
            "upload_session_id", "workspace_id", "context_scope", "file_id", "template_id", "schema_hash",
            "input_key", "idempotency_key", "expires_at")
            .doesNotContain("create_dept", "create_by", "update_by");
    }

    private static Iterable<String> columns(Class<?> entityType) {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(entityType);
        if (tableInfo == null) {
            tableInfo = TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityType);
        }
        return java.util.stream.Stream.concat(
                java.util.stream.Stream.of(tableInfo.getKeyColumn()),
                tableInfo.getFieldList().stream().map(field -> field.getColumn()))
            .filter(java.util.Objects::nonNull)
            .toList();
    }
}
