package org.dromara.aivideo.user.discovery.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.aivideo.user.discovery.domain.bo.DiscoveryTemplateQueryBo;
import org.dromara.aivideo.user.discovery.domain.vo.DiscoveryHomeVo;
import org.dromara.aivideo.user.discovery.domain.vo.WorkflowCreationConfigVo;
import org.dromara.aivideo.user.discovery.domain.vo.WorkflowTemplateCardVo;
import org.dromara.aivideo.user.discovery.domain.vo.WorkflowTemplateDetailVo;
import org.dromara.aivideo.user.discovery.service.IDiscoveryService;
import org.dromara.common.core.domain.PageResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("dev")
class DiscoveryControllerTest {

    @Test
    void allDiscoveryReadsRequireAppStudioQueryPermission() throws Exception {
        assertPermission("home");
        assertPermission("templates", DiscoveryTemplateQueryBo.class);
        assertPermission("template", String.class);
        assertPermission("creationConfig", String.class);
    }

    @Test
    void discoveryQueryDoesNotAcceptCallerControlledOwnershipScope() {
        assertThat(Arrays.stream(DiscoveryTemplateQueryBo.class.getDeclaredFields()).map(Field::getName))
            .doesNotContain("tenantId", "ownerId", "workspaceId");
        assertThat(Arrays.stream(DiscoveryController.class.getDeclaredMethods())
            .flatMap(method -> Arrays.stream(method.getParameterTypes())))
            .doesNotContain(Long.class);
    }

    @Test
    void exposesHomeListDetailAndCreationConfigContracts() throws Exception {
        IDiscoveryService service = mock(IDiscoveryService.class);
        WorkflowTemplateCardVo card = card();
        DiscoveryHomeVo home = new DiscoveryHomeVo(
            List.of(),
            List.of(card),
            List.of(new DiscoveryHomeVo.ChannelVo("video_template", "视频模板", "即用型视频制作工作流", "7")),
            List.of(new DiscoveryHomeVo.CategoryVo("11", "营销", "7")),
            List.of(new DiscoveryHomeVo.TagVo("21", "口播"))
        );
        WorkflowTemplateDetailVo detail = new WorkflowTemplateDetailVo(
            card.templateId(), card.title(), card.summary(), card.channel(), card.category(), card.tags(),
            card.cover(), card.preview(), card.usageCount(), card.estimatedDurationSeconds(), card.enabledAt(),
            "模板详情", List.of(), List.of(new WorkflowTemplateDetailVo.RequiredInputVo(
                "portrait", "人物图片", "asset_array", "image", true))
        );
        WorkflowCreationConfigVo config = new WorkflowCreationConfigVo(
            "101", "workflow-form-1", "sha256:" + "a".repeat(64), List.of(), 30,
            new WorkflowCreationConfigVo.BillingPolicyVo("free"));
        when(service.queryHome()).thenReturn(home);
        when(service.queryTemplates(any(DiscoveryTemplateQueryBo.class)))
            .thenReturn(PageResult.build(List.of(card), 7));
        when(service.queryTemplate("101")).thenReturn(detail);
        when(service.queryCreationConfig("101")).thenReturn(config);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DiscoveryController(service)).build();

        mockMvc.perform(get("/api/discovery/home"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.banners").isEmpty())
            .andExpect(jsonPath("$.data.recommendations[0].templateId").value("101"))
            .andExpect(jsonPath("$.data.channels[0].templateCount").value("7"));

        mockMvc.perform(get("/api/discovery/templates")
                .param("pageNum", "2")
                .param("pageSize", "5")
                .param("channel", "video_template")
                .param("sort", "recommended"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.rows[0].templateId").value("101"))
            .andExpect(jsonPath("$.data.total").isNumber())
            .andExpect(jsonPath("$.data.total").value(7));

        mockMvc.perform(get("/api/discovery/templates/101"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.templateId").value("101"))
            .andExpect(jsonPath("$.data.description").value("模板详情"))
            .andExpect(jsonPath("$.data.requiredInputs[0].semanticKey").value("portrait"));

        mockMvc.perform(get("/api/discovery/templates/101/creation-config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.templateId").value("101"))
            .andExpect(jsonPath("$.data.schemaVersion").value("workflow-form-1"))
            .andExpect(jsonPath("$.data.billingPolicy.mode").value("free"));

        verify(service).queryHome();
        verify(service).queryTemplates(any(DiscoveryTemplateQueryBo.class));
        verify(service).queryTemplate("101");
        verify(service).queryCreationConfig("101");
    }

    private void assertPermission(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = DiscoveryController.class.getMethod(methodName, parameterTypes);
        SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
        assertThat(permission).isNotNull();
        assertThat(permission.value()).containsExactly("aivideo:studio:query");
        assertThat(permission.type()).isEqualTo("app");
    }

    private WorkflowTemplateCardVo card() {
        return new WorkflowTemplateCardVo(
            "101", "口播模板", "快速生成口播视频", "video_template",
            new WorkflowTemplateCardVo.CategoryVo("11", "营销"),
            List.of(new WorkflowTemplateCardVo.TagVo("21", "口播")),
            null,
            new WorkflowTemplateCardVo.MediaVo("31", "video", "/preview.mp4", "/poster.jpg", 1080, 1920, "预览"),
            "7", 30, "2026-08-11T09:30:00"
        );
    }
}
