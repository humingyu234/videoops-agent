package org.dromara.common.mybatis.handler;

import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.dromara.common.mybatis.audit.AppAuditRequired;
import org.dromara.common.mybatis.audit.AuditFillContext;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class InjectionMetaObjectHandlerAppAuditTest {

    @Test
    void appEntityRequiresContextAndUsesAppActor() {
        InjectionMetaObjectHandler handler = new InjectionMetaObjectHandler();
        AppEntity entity = new AppEntity();
        assertThatThrownBy(() -> handler.insertFill(SystemMetaObject.forObject(entity)))
            .isInstanceOf(RuntimeException.class);

        try (AuditFillContext.Scope ignored = AuditFillContext.open(42L)) {
            handler.insertFill(SystemMetaObject.forObject(entity));
            assertThat(entity.getCreateBy()).isEqualTo(42L);
            assertThat(entity.getUpdateBy()).isEqualTo(42L);
            assertThat(entity.getCreateDept()).isNull();
        }
    }

    @Test
    void unmarkedEntityKeepsExistingFillPath() {
        InjectionMetaObjectHandler handler = new InjectionMetaObjectHandler();
        BaseEntity entity = new BaseEntity();
        handler.insertFill(SystemMetaObject.forObject(entity));
        assertThat(entity.getCreateBy()).isNotNull();
    }

    @AppAuditRequired
    private static final class AppEntity extends BaseEntity {
    }
}
