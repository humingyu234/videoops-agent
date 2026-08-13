package org.dromara.aivideo.user.discovery.service.impl;

import org.dromara.aivideo.user.discovery.domain.bo.DiscoveryTemplateQueryBo;
import org.dromara.aivideo.workflow.constant.WorkflowErrorCodes;
import org.dromara.aivideo.workflow.dto.WorkflowTemplateDTOs;
import org.dromara.aivideo.workflow.service.IWorkflowTemplateService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class DiscoveryServiceImplTest {

    @Test
    void delegatesVisiblePageQueryAndMapsFrameworkPageResult() {
        IWorkflowTemplateService core = mock(IWorkflowTemplateService.class);
        when(core.queryVisiblePage(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(PageResult.build(List.of(card("101")), 13));
        DiscoveryServiceImpl service = new DiscoveryServiceImpl(core);
        DiscoveryTemplateQueryBo query = new DiscoveryTemplateQueryBo();
        query.setPageNum(3);
        query.setPageSize(5);
        query.setChannel("video_template");
        query.setCategoryCode("11");
        query.setTagCodes("21, 22");
        query.setKeyword("口播");
        query.setSort("latest");

        var result = service.queryTemplates(query);

        assertThat(result.getTotal()).isEqualTo(13);
        assertThat(result.getRows()).singleElement().satisfies(row -> {
            assertThat(row.templateId()).isEqualTo("101");
            assertThat(row.category().categoryCode()).isEqualTo("11");
            assertThat(row.tags()).extracting(tag -> tag.tagCode()).containsExactly("21");
            assertThat(row.cover()).isNull();
            assertThat(row.preview().mediaId()).isEqualTo("31");
            assertThat(row.estimatedDurationSeconds()).isEqualTo(30);
            assertThat(row.enabledAt()).isEqualTo("2026-08-11T09:30");
        });

        ArgumentCaptor<WorkflowTemplateDTOs.PublicQuery> publicQuery =
            ArgumentCaptor.forClass(WorkflowTemplateDTOs.PublicQuery.class);
        ArgumentCaptor<PageQuery> pageQuery = ArgumentCaptor.forClass(PageQuery.class);
        verify(core).queryVisiblePage(publicQuery.capture(), pageQuery.capture());
        assertThat(publicQuery.getValue()).isEqualTo(new WorkflowTemplateDTOs.PublicQuery(
            "video_template", "11", List.of("21", "22"), "口播", "latest"));
        assertThat(pageQuery.getValue().getPageNum()).isEqualTo(3);
        assertThat(pageQuery.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void mapsDetailAndCreationConfigWithoutExecutionInternals() {
        IWorkflowTemplateService core = mock(IWorkflowTemplateService.class);
        WorkflowTemplateDTOs.PublicCard card = card("101");
        when(core.queryVisibleDetail("101")).thenReturn(new WorkflowTemplateDTOs.PublicDetail(
            card.templateId(), card.title(), card.summary(), card.channel(), card.category(), card.tags(),
            card.cover(), card.preview(), card.usageCount(), card.estimatedDurationSeconds(), card.enabledAt(),
            "模板详情", List.of(card.preview()), List.of(
                new WorkflowTemplateDTOs.RequiredInput("portrait", "人物图片", "asset_array", "image", true))));
        var defaultValue = tools.jackson.databind.json.JsonMapper.builder().build().readTree("\"默认文案\"");
        when(core.queryCreationConfig("101")).thenReturn(new WorkflowTemplateDTOs.CreationConfig(
            "101", "workflow-form-1", "sha256:" + "a".repeat(64),
            List.of(new WorkflowTemplateDTOs.InputField(
                "copy", "script", "文案", "输入文案", "textarea", "string", true,
                defaultValue, "请输入", List.of(new WorkflowTemplateDTOs.InputOption("a", "选项 A")),
                new WorkflowTemplateDTOs.InputConstraints(null, null, 1, 100, null, null,
                    null, null, null, null))),
            30, new WorkflowTemplateDTOs.BillingPolicy("free")));
        DiscoveryServiceImpl service = new DiscoveryServiceImpl(core);

        var detail = service.queryTemplate("101");
        var config = service.queryCreationConfig("101");

        assertThat(detail.description()).isEqualTo("模板详情");
        assertThat(detail.cases()).singleElement().extracting(media -> media.mediaId()).isEqualTo("31");
        assertThat(detail.requiredInputs()).singleElement().satisfies(required -> {
            assertThat(required.semanticKey()).isEqualTo("portrait");
            assertThat(required.assetType()).isEqualTo("image");
        });
        assertThat(config.templateId()).isEqualTo("101");
        assertThat(config.fields()).singleElement().satisfies(field -> {
            assertThat(field.inputKey()).isEqualTo("copy");
            assertThat(field.defaultValue()).isEqualTo(defaultValue);
            assertThat(field.options()).singleElement().extracting(option -> option.value()).isEqualTo("a");
            assertThat(field.constraints().maxLength()).isEqualTo(100);
        });
        assertThat(config.billingPolicy().mode()).isEqualTo("free");
    }

    @Test
    void omitsEmptyOptionsForNonSelectCreationFields() {
        IWorkflowTemplateService core = mock(IWorkflowTemplateService.class);
        when(core.queryCreationConfig("101")).thenReturn(new WorkflowTemplateDTOs.CreationConfig(
            "101", "workflow-form-1", "sha256:" + "a".repeat(64),
            List.of(new WorkflowTemplateDTOs.InputField(
                "sourceVideo", null, "源视频", null, "video", "asset_array", true,
                null, null, List.of(),
                new WorkflowTemplateDTOs.InputConstraints(null, null, null, null, null, 1,
                    "video", null, null, null))),
            null, new WorkflowTemplateDTOs.BillingPolicy("free")));

        var config = new DiscoveryServiceImpl(core).queryCreationConfig("101");

        assertThat(config.fields()).singleElement()
            .extracting(field -> field.options())
            .isNull();
    }

    @Test
    void mapsRealHomeAggregatesAndCapsRecommendationsAtSix() {
        IWorkflowTemplateService core = mock(IWorkflowTemplateService.class);
        List<WorkflowTemplateDTOs.PublicCard> recommendations = IntStream.rangeClosed(1, 8)
            .mapToObj(index -> card(String.valueOf(index)))
            .toList();
        when(core.queryDiscoveryHome()).thenReturn(new WorkflowTemplateDTOs.DiscoveryHome(
            List.of(new Object()), recommendations,
            List.of(new WorkflowTemplateDTOs.ChannelSummary(
                "video_template", "视频模板", "即用型视频制作工作流", "8")),
            List.of(new WorkflowTemplateDTOs.CategorySummary("11", "营销", "8")),
            List.of(new WorkflowTemplateDTOs.Tag("21", "口播"))));

        var result = new DiscoveryServiceImpl(core).queryHome();

        assertThat(result.banners()).isEmpty();
        assertThat(result.recommendations()).hasSize(6)
            .extracting(item -> item.templateId()).containsExactly("1", "2", "3", "4", "5", "6");
        assertThat(result.channels()).singleElement().satisfies(channel -> {
            assertThat(channel.channel()).isEqualTo("video_template");
            assertThat(channel.templateCount()).isEqualTo("8");
        });
        assertThat(result.categories()).singleElement().extracting(category -> category.categoryCode())
            .isEqualTo("11");
        assertThat(result.tags()).singleElement().extracting(tag -> tag.tagCode()).isEqualTo("21");
    }

    @Test
    void preservesCoreServiceExceptionAndStableErrorCode() {
        IWorkflowTemplateService core = mock(IWorkflowTemplateService.class);
        ServiceException failure = new ServiceException(
            "模板不可用", WorkflowErrorCodes.WORKFLOW_TEMPLATE_UNAVAILABLE);
        when(core.queryVisibleDetail("404")).thenThrow(failure);

        assertThatThrownBy(() -> new DiscoveryServiceImpl(core).queryTemplate("404"))
            .isSameAs(failure)
            .isInstanceOfSatisfying(ServiceException.class,
                error -> assertThat(error.getCode()).isEqualTo(WorkflowErrorCodes.WORKFLOW_TEMPLATE_UNAVAILABLE));
    }

    private WorkflowTemplateDTOs.PublicCard card(String templateId) {
        return new WorkflowTemplateDTOs.PublicCard(
            templateId, "口播模板 " + templateId, "快速生成口播视频", "video_template",
            new WorkflowTemplateDTOs.Category("11", "营销"),
            List.of(new WorkflowTemplateDTOs.Tag("21", "口播")), null,
            new WorkflowTemplateDTOs.Media("31", "video", "/preview.mp4", "/poster.jpg",
                1080, 1920, "预览"),
            "7", 30, LocalDateTime.of(2026, 8, 11, 9, 30));
    }
}
