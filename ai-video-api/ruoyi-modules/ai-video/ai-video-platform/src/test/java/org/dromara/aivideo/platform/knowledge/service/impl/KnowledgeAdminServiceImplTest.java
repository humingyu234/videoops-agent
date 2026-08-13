package org.dromara.aivideo.platform.knowledge.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aivideo.knowledge.domain.KnowledgeBinding;
import org.dromara.aivideo.knowledge.domain.KnowledgeItem;
import org.dromara.aivideo.knowledge.domain.KnowledgeVersion;
import org.dromara.aivideo.knowledge.mapper.KnowledgeBindingMapper;
import org.dromara.aivideo.knowledge.mapper.KnowledgeItemMapper;
import org.dromara.aivideo.knowledge.mapper.KnowledgeVersionMapper;
import org.dromara.aivideo.platform.knowledge.domain.bo.KnowledgeItemAdminQueryBo;
import org.dromara.aivideo.platform.knowledge.domain.bo.KnowledgeItemSaveBo;
import org.dromara.aivideo.platform.knowledge.domain.vo.KnowledgeImportSummaryVo;
import org.dromara.aivideo.platform.knowledge.domain.vo.KnowledgeItemAdminVo;
import org.dromara.aivideo.platform.knowledge.domain.vo.KnowledgeItemDetailVo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("dev")
class KnowledgeAdminServiceImplTest {

    private static final long MEBIBYTE = 1024L * 1024L;

    @Test
    void knowledgeListRowsDoNotExposeSourceReference() {
        assertThat(Arrays.stream(KnowledgeItemAdminVo.class.getRecordComponents())
            .map(java.lang.reflect.RecordComponent::getName))
            .doesNotContain("sourceRef");
    }

    @Test
    void knowledgeListRowsDoNotExposeContentByteCount() {
        assertThat(Arrays.stream(KnowledgeItemAdminVo.class.getRecordComponents())
            .map(java.lang.reflect.RecordComponent::getName))
            .doesNotContain("contentBytes");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void pageSelectsOnlyLightweightVersionColumns() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
            KnowledgeItem.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
            KnowledgeVersion.class);
        KnowledgeItemMapper itemMapper = mock(KnowledgeItemMapper.class);
        KnowledgeVersionMapper versionMapper = mock(KnowledgeVersionMapper.class);
        KnowledgeBindingMapper bindingMapper = mock(KnowledgeBindingMapper.class);
        KnowledgeItem item = new KnowledgeItem();
        item.setKnowledgeItemId(7L);
        Page<KnowledgeItem> itemPage = new Page<>(1, 20);
        itemPage.setRecords(List.of(item));
        itemPage.setTotal(1L);
        when(itemMapper.selectPage(any(IPage.class), any(LambdaQueryWrapper.class))).thenReturn(itemPage);
        when(versionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        KnowledgeAdminServiceImpl service = new KnowledgeAdminServiceImpl(itemMapper, versionMapper, bindingMapper);

        service.page(null, null);

        ArgumentCaptor<LambdaQueryWrapper<KnowledgeVersion>> wrapperCaptor =
            ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(versionMapper).selectList(wrapperCaptor.capture());
        String selectedColumns = wrapperCaptor.getValue().getSqlSelect();
        assertThat(selectedColumns).isNotBlank();
        assertThat(selectedColumns).contains("knowledge_item_id", "knowledge_version_id", "version_no", "status");
        assertThat(selectedColumns).doesNotContain("content", "structure_json", "source_summary");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void pageUsesTwentyRowsWhenPaginationIsMissing() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
            KnowledgeItem.class);
        KnowledgeItemMapper itemMapper = mock(KnowledgeItemMapper.class);
        KnowledgeVersionMapper versionMapper = mock(KnowledgeVersionMapper.class);
        KnowledgeBindingMapper bindingMapper = mock(KnowledgeBindingMapper.class);
        Page<KnowledgeItem> emptyPage = new Page<>(1, 20);
        emptyPage.setRecords(List.of());
        when(itemMapper.selectPage(any(IPage.class), any(LambdaQueryWrapper.class))).thenReturn(emptyPage);
        KnowledgeAdminServiceImpl service = new KnowledgeAdminServiceImpl(itemMapper, versionMapper, bindingMapper);

        service.page(null, null);

        ArgumentCaptor<IPage<KnowledgeItem>> pageCaptor = ArgumentCaptor.forClass(IPage.class);
        verify(itemMapper).selectPage(pageCaptor.capture(), any(LambdaQueryWrapper.class));
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(20L);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void pageCapsPageSizeAndIgnoresClientSort() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
            KnowledgeItem.class);
        KnowledgeItemMapper itemMapper = mock(KnowledgeItemMapper.class);
        KnowledgeVersionMapper versionMapper = mock(KnowledgeVersionMapper.class);
        KnowledgeBindingMapper bindingMapper = mock(KnowledgeBindingMapper.class);
        Page<KnowledgeItem> emptyPage = new Page<>(1, 100);
        emptyPage.setRecords(List.of());
        when(itemMapper.selectPage(any(IPage.class), any(LambdaQueryWrapper.class))).thenReturn(emptyPage);
        KnowledgeAdminServiceImpl service = new KnowledgeAdminServiceImpl(itemMapper, versionMapper, bindingMapper);
        PageQuery requestedPage = new PageQuery();
        requestedPage.setPageNum(-1);
        requestedPage.setPageSize(500);
        requestedPage.setOrderByColumn("name");
        requestedPage.setIsAsc("asc");

        service.page(null, requestedPage);

        ArgumentCaptor<IPage<KnowledgeItem>> pageCaptor = ArgumentCaptor.forClass(IPage.class);
        verify(itemMapper).selectPage(pageCaptor.capture(), any(LambdaQueryWrapper.class));
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(100L);
        assertThat(pageCaptor.getValue().orders()).isEmpty();
    }

    @Test
    void rejectsMoreThanTwentyFilesBeforeReading() {
        KnowledgeAdminServiceImpl service = importService();
        List<MultipartFile> files = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<String> knowledgeTypes = new ArrayList<>();
        List<String> statuses = new ArrayList<>();
        for (int index = 0; index < 21; index++) {
            files.add(textFile("file-" + index + ".md", "text/markdown", "知识" + index));
            names.add("知识" + index);
            knowledgeTypes.add("case");
            statuses.add("draft");
        }

        assertThatThrownBy(() -> service.importFiles(files, names, knowledgeTypes, statuses, 42L))
            .isInstanceOf(org.dromara.common.core.exception.ServiceException.class)
            .hasMessageContaining("20");
    }

    @Test
    void rejectsSingleFileLargerThanTenMebibytesBeforeReading() {
        KnowledgeAdminServiceImpl service = importService();
        MultipartFile file = reportedSizeFile("oversized.md", "text/markdown", 10L * MEBIBYTE + 1L);

        assertThatThrownBy(() -> service.importFiles(List.of(file), List.of("超大知识"),
            List.of("case"), List.of("draft"), 42L))
            .isInstanceOf(org.dromara.common.core.exception.ServiceException.class)
            .hasMessageContaining("10MB");
    }

    @Test
    void rejectsTotalSizeLargerThanTwentyMebibytesBeforeReading() {
        KnowledgeAdminServiceImpl service = importService();
        List<MultipartFile> files = List.of(
            reportedSizeFile("one.md", "text/markdown", 8L * MEBIBYTE),
            reportedSizeFile("two.md", "text/markdown", 8L * MEBIBYTE),
            reportedSizeFile("three.md", "text/markdown", 8L * MEBIBYTE)
        );

        assertThatThrownBy(() -> service.importFiles(files, List.of("一", "二", "三"),
            List.of("case", "case", "case"), List.of("draft", "draft", "draft"), 42L))
            .isInstanceOf(org.dromara.common.core.exception.ServiceException.class)
            .hasMessageContaining("20MB");
    }

    @Test
    void rejectsActualTotalSizeWhenMultipartReportedSizesAreMisleading() {
        KnowledgeAdminServiceImpl service = importService();
        byte[] eightMebibytes = new byte[(int) (8L * MEBIBYTE)];
        Arrays.fill(eightMebibytes, (byte) 'a');
        List<MultipartFile> files = List.of(
            misleadingSizeFile("one.md", eightMebibytes),
            misleadingSizeFile("two.md", eightMebibytes),
            misleadingSizeFile("three.md", eightMebibytes)
        );

        assertThatThrownBy(() -> service.importFiles(files, List.of("一", "二", "三"),
            List.of("case", "case", "case"), List.of("draft", "draft", "draft"), 42L))
            .isInstanceOf(org.dromara.common.core.exception.ServiceException.class)
            .hasMessageContaining("总大小");
    }

    @Test
    void importsThroughInputStreamWithoutCallingGetBytes() {
        KnowledgeAdminServiceImpl service = importService();
        MockMultipartFile file = new MockMultipartFile("files", "streamed.md", "text/markdown",
            "流式读取知识".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public byte[] getBytes() throws java.io.IOException {
                throw new java.io.IOException("不允许调用 getBytes");
            }
        };

        KnowledgeImportSummaryVo result = service.importFiles(List.of(file), List.of("流式知识"),
            List.of("case"), List.of("draft"), 42L);

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failedCount()).isZero();
    }

    @Test
    void rejectsMimeTypeThatIsNotAllowedForTextKnowledge() {
        KnowledgeAdminServiceImpl service = importService();
        MultipartFile file = textFile("fake.md", "application/pdf", "伪装文本");

        KnowledgeImportSummaryVo result = service.importFiles(List.of(file), List.of("伪装知识"),
            List.of("case"), List.of("draft"), 42L);

        assertThat(result.successCount()).isZero();
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.files().get(0).message()).contains("MIME");
    }

    @Test
    void acceptsGenericMimeOnlyWhenWhitelistedExtensionAndTextChecksPass() {
        KnowledgeAdminServiceImpl service = importService();
        List<MultipartFile> files = List.of(
            textFile("windows.md", null, "Windows Markdown 知识"),
            textFile("browser.yaml", "application/octet-stream", "title: 浏览器知识")
        );

        KnowledgeImportSummaryVo result = service.importFiles(files, List.of("Markdown", "YAML"),
            List.of("case", "case"), List.of("draft", "draft"), 42L);

        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.failedCount()).isZero();
    }

    @Test
    void rejectsKnownBinarySignatureEvenWithGenericMimeAndTextExtension() {
        KnowledgeAdminServiceImpl service = importService();
        MultipartFile file = new MockMultipartFile("files", "fake.md", "application/octet-stream",
            "%PDF-1.7\n1 0 obj".getBytes(StandardCharsets.US_ASCII));

        KnowledgeImportSummaryVo result = service.importFiles(List.of(file), List.of("伪装 PDF"),
            List.of("case"), List.of("draft"), 42L);

        assertThat(result.successCount()).isZero();
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.files().get(0).message()).contains("二进制");
    }

    @Test
    void rejectsTextFileContainingNulByte() {
        KnowledgeAdminServiceImpl service = importService();
        MultipartFile file = new MockMultipartFile("files", "binary.md", "text/markdown",
            new byte[]{'t', 'e', 'x', 't', 0, 'x'});

        KnowledgeImportSummaryVo result = service.importFiles(List.of(file), List.of("二进制知识"),
            List.of("case"), List.of("draft"), 42L);

        assertThat(result.successCount()).isZero();
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.files().get(0).message()).contains("二进制");
    }

    @Test
    void rejectsImportWhenEditableMetadataListsAreMissing() {
        KnowledgeAdminServiceImpl service = importService();
        MultipartFile file = textFile("missing.md", "text/markdown", "知识");

        assertThatThrownBy(() -> service.importFiles(List.of(file), null, null, null, 42L))
            .isInstanceOf(org.dromara.common.core.exception.ServiceException.class)
            .hasMessageContaining("名称、知识类型和状态");
    }

    @Test
    void rejectsImportWhenMetadataCountsDoNotMatchFiles() {
        KnowledgeAdminServiceImpl service = importService();
        List<MultipartFile> files = List.of(
            textFile("one.md", "text/markdown", "知识一"),
            textFile("two.md", "text/markdown", "知识二")
        );

        assertThatThrownBy(() -> service.importFiles(files, List.of("只有一个名称"),
            List.of("case", "case"), List.of("draft", "draft"), 42L))
            .isInstanceOf(org.dromara.common.core.exception.ServiceException.class)
            .hasMessageContaining("一一对应");
    }

    @Test
    void rejectsImportWhenAnyEditableMetadataValueIsBlank() {
        KnowledgeAdminServiceImpl service = importService();
        MultipartFile file = textFile("blank.md", "text/markdown", "知识");

        assertThatThrownBy(() -> service.importFiles(List.of(file), List.of(" "),
            List.of("case"), List.of("draft"), 42L))
            .isInstanceOf(org.dromara.common.core.exception.ServiceException.class)
            .hasMessageContaining("不能为空");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void pageFiltersByKnowledgeType() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
            KnowledgeItem.class);
        KnowledgeItemMapper itemMapper = mock(KnowledgeItemMapper.class);
        KnowledgeVersionMapper versionMapper = mock(KnowledgeVersionMapper.class);
        KnowledgeBindingMapper bindingMapper = mock(KnowledgeBindingMapper.class);
        Page<KnowledgeItem> emptyPage = new Page<>(1, 10);
        emptyPage.setRecords(List.of());
        when(itemMapper.selectPage(any(IPage.class), any(LambdaQueryWrapper.class))).thenReturn(emptyPage);
        KnowledgeAdminServiceImpl service = new KnowledgeAdminServiceImpl(itemMapper, versionMapper, bindingMapper);
        KnowledgeItemAdminQueryBo query = new KnowledgeItemAdminQueryBo();
        query.setKnowledgeType("case");

        service.page(query, new PageQuery());

        ArgumentCaptor<LambdaQueryWrapper<KnowledgeItem>> wrapperCaptor =
            ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(itemMapper).selectPage(any(IPage.class), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("knowledge_type_code");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs()).containsValue("case");
    }

    @Test
    void importsSupportedTextFilesWithEditableMetadata() {
        KnowledgeItemMapper itemMapper = mock(KnowledgeItemMapper.class);
        KnowledgeVersionMapper versionMapper = mock(KnowledgeVersionMapper.class);
        KnowledgeBindingMapper bindingMapper = mock(KnowledgeBindingMapper.class);
        when(itemMapper.selectOne(any())).thenReturn(null);
        when(itemMapper.insert(any(KnowledgeItem.class))).thenReturn(1);
        when(versionMapper.insert(any(KnowledgeVersion.class))).thenReturn(1);
        when(bindingMapper.insert(any(KnowledgeBinding.class))).thenReturn(1);
        KnowledgeAdminServiceImpl service = new KnowledgeAdminServiceImpl(itemMapper, versionMapper, bindingMapper);

        byte[] markdown = "# 标题\n\n有效知识".getBytes(StandardCharsets.UTF_8);
        List<MultipartFile> files = List.of(
            new MockMultipartFile("files", "guide.md", "text/markdown", markdown),
            new MockMultipartFile("files", "guide-copy.md", "text/markdown", markdown),
            new MockMultipartFile("files", "._guide.md", "text/markdown", markdown),
            new MockMultipartFile("files", "notes.txt", "text/plain",
                "用户犹豫时先询问核心顾虑。".getBytes(StandardCharsets.UTF_8)),
            new MockMultipartFile("files", "broken.md", "text/markdown", new byte[]{(byte) 0xC3, (byte) 0x28})
        );

        KnowledgeImportSummaryVo result = service.importFiles(files,
            List.of("营销知识", "营销知识副本", "忽略", "文本知识", "损坏文件"),
            List.of("case", "case", "case", "psychology", "case"),
            List.of("published", "published", "published", "reviewing", "published"), 42L);

        assertThat(result.totalCount()).isEqualTo(5);
        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.skippedCount()).isEqualTo(2);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.files()).extracting(KnowledgeImportSummaryVo.KnowledgeImportFileVo::status)
            .containsExactly("success", "skipped", "skipped", "success", "failed");

        ArgumentCaptor<KnowledgeItem> itemCaptor = ArgumentCaptor.forClass(KnowledgeItem.class);
        ArgumentCaptor<KnowledgeVersion> versionCaptor = ArgumentCaptor.forClass(KnowledgeVersion.class);
        ArgumentCaptor<KnowledgeBinding> bindingCaptor = ArgumentCaptor.forClass(KnowledgeBinding.class);
        verify(itemMapper, org.mockito.Mockito.times(2)).insert(itemCaptor.capture());
        verify(versionMapper, org.mockito.Mockito.times(2)).insert(versionCaptor.capture());
        verify(bindingMapper, org.mockito.Mockito.times(2)).insert(bindingCaptor.capture());

        KnowledgeItem item = itemCaptor.getAllValues().get(0);
        KnowledgeVersion version = versionCaptor.getAllValues().get(0);
        KnowledgeBinding binding = bindingCaptor.getAllValues().get(0);
        assertThat(item.getStableCode()).matches("[0-9a-f]{64}");
        assertThat(item.getName()).isEqualTo("营销知识");
        assertThat(item.getKnowledgeTypeCode()).isEqualTo("case");
        assertThat(item.getCurrentPublishedVersionId()).isEqualTo(version.getKnowledgeVersionId());
        assertThat(version.getStatus()).isEqualTo("published");
        assertThat(version.getPublishedBy()).isEqualTo(42L);
        assertThat(binding.getKnowledgeItemId()).isEqualTo(item.getKnowledgeItemId());
        assertThat(binding.getKnowledgeVersionId()).isEqualTo(version.getKnowledgeVersionId());
        assertThat(binding.getIndustryCode()).isEqualTo("*");
        assertThat(binding.getPurposeCode()).isEqualTo("*");
        assertThat(binding.getVideoTypeCode()).isEqualTo("*");
        assertThat(binding.getStatus()).isEqualTo("published");

        KnowledgeItem textItem = itemCaptor.getAllValues().get(1);
        KnowledgeVersion textVersion = versionCaptor.getAllValues().get(1);
        assertThat(textItem.getName()).isEqualTo("文本知识");
        assertThat(textItem.getKnowledgeTypeCode()).isEqualTo("psychology");
        assertThat(textItem.getCurrentPublishedVersionId()).isNull();
        assertThat(textVersion.getStatus()).isEqualTo("reviewing");
    }

    @Test
    void createsAndReadsKnowledgeDetail() {
        KnowledgeItemMapper itemMapper = mock(KnowledgeItemMapper.class);
        KnowledgeVersionMapper versionMapper = mock(KnowledgeVersionMapper.class);
        KnowledgeBindingMapper bindingMapper = mock(KnowledgeBindingMapper.class);
        when(itemMapper.insert(any(KnowledgeItem.class))).thenReturn(1);
        when(versionMapper.insert(any(KnowledgeVersion.class))).thenReturn(1);
        when(bindingMapper.insert(any(KnowledgeBinding.class))).thenReturn(1);
        KnowledgeAdminServiceImpl service = new KnowledgeAdminServiceImpl(itemMapper, versionMapper, bindingMapper);

        KnowledgeItemSaveBo bo = new KnowledgeItemSaveBo();
        bo.setName("转化问卷知识");
        bo.setKnowledgeType("primary_template");
        bo.setStatus("published");
        bo.setSummary("给问卷生成使用");
        bo.setContent("先确认用户的目标和受众。");

        Long itemId = service.create(bo, 42L);

        ArgumentCaptor<KnowledgeItem> itemCaptor = ArgumentCaptor.forClass(KnowledgeItem.class);
        ArgumentCaptor<KnowledgeVersion> versionCaptor = ArgumentCaptor.forClass(KnowledgeVersion.class);
        verify(itemMapper).insert(itemCaptor.capture());
        verify(versionMapper).insert(versionCaptor.capture());
        assertThat(itemId).isEqualTo(itemCaptor.getValue().getKnowledgeItemId());
        assertThat(itemCaptor.getValue().getCurrentPublishedVersionId())
            .isEqualTo(versionCaptor.getValue().getKnowledgeVersionId());

        when(itemMapper.selectById(itemId)).thenReturn(itemCaptor.getValue());
        when(versionMapper.selectOne(any())).thenReturn(versionCaptor.getValue());
        KnowledgeItemDetailVo detail = service.detail(itemId);
        assertThat(detail.name()).isEqualTo("转化问卷知识");
        assertThat(detail.knowledgeType()).isEqualTo("primary_template");
        assertThat(detail.status()).isEqualTo("published");
        assertThat(detail.content()).isEqualTo("先确认用户的目标和受众。");
    }

    @Test
    void updatingPublishedKnowledgeAppendsVersionWithoutChangingPublishedHistory() {
        KnowledgeItemMapper itemMapper = mock(KnowledgeItemMapper.class);
        KnowledgeVersionMapper versionMapper = mock(KnowledgeVersionMapper.class);
        KnowledgeBindingMapper bindingMapper = mock(KnowledgeBindingMapper.class);
        KnowledgeItem item = new KnowledgeItem();
        item.setKnowledgeItemId(9L);
        item.setStableCode("published_history");
        item.setName("旧名称");
        item.setKnowledgeTypeCode("case");
        item.setCurrentPublishedVersionId(99L);
        KnowledgeVersion publishedVersion = new KnowledgeVersion();
        publishedVersion.setKnowledgeVersionId(99L);
        publishedVersion.setKnowledgeItemId(9L);
        publishedVersion.setVersionNo(3);
        publishedVersion.setStatus("published");
        publishedVersion.setContent("不可变的已发布正文");
        when(itemMapper.selectById(9L)).thenReturn(item);
        when(versionMapper.selectOne(any())).thenReturn(publishedVersion);
        when(versionMapper.updateById(any(KnowledgeVersion.class))).thenReturn(1);
        when(versionMapper.insert(any(KnowledgeVersion.class))).thenReturn(1);
        when(bindingMapper.insert(any(KnowledgeBinding.class))).thenReturn(1);
        when(itemMapper.updateById(item)).thenReturn(1);
        KnowledgeAdminServiceImpl service = new KnowledgeAdminServiceImpl(itemMapper, versionMapper, bindingMapper);
        KnowledgeItemSaveBo bo = new KnowledgeItemSaveBo();
        bo.setName("新名称");
        bo.setKnowledgeType("writing_technique");
        bo.setStatus("draft");
        bo.setSummary("新摘要");
        bo.setContent("新的草稿正文");

        service.update(9L, bo, 42L);

        assertThat(publishedVersion.getContent()).isEqualTo("不可变的已发布正文");
        assertThat(publishedVersion.getStatus()).isEqualTo("published");
        assertThat(item.getCurrentPublishedVersionId()).isEqualTo(99L);
        ArgumentCaptor<KnowledgeVersion> versionCaptor = ArgumentCaptor.forClass(KnowledgeVersion.class);
        verify(versionMapper).insert(versionCaptor.capture());
        KnowledgeVersion appendedVersion = versionCaptor.getValue();
        assertThat(appendedVersion.getKnowledgeVersionId()).isNotEqualTo(99L);
        assertThat(appendedVersion.getVersionNo()).isEqualTo(4);
        assertThat(appendedVersion.getStatus()).isEqualTo("draft");
        assertThat(appendedVersion.getContent()).isEqualTo("新的草稿正文");
        ArgumentCaptor<KnowledgeBinding> bindingCaptor = ArgumentCaptor.forClass(KnowledgeBinding.class);
        verify(bindingMapper).insert(bindingCaptor.capture());
        assertThat(bindingCaptor.getValue().getKnowledgeVersionId())
            .isEqualTo(appendedVersion.getKnowledgeVersionId());
    }

    @Test
    void deletingDraftOnlyKnowledgeRemovesBindingsThenVersionsThenItem() {
        KnowledgeItemMapper itemMapper = mock(KnowledgeItemMapper.class);
        KnowledgeVersionMapper versionMapper = mock(KnowledgeVersionMapper.class);
        KnowledgeBindingMapper bindingMapper = mock(KnowledgeBindingMapper.class);
        when(itemMapper.selectById(7L)).thenReturn(new KnowledgeItem());
        when(versionMapper.selectCount(any())).thenReturn(0L);
        when(bindingMapper.delete(any())).thenReturn(1);
        when(versionMapper.delete(any())).thenReturn(1);
        when(itemMapper.deleteById(7L)).thenReturn(1);
        KnowledgeAdminServiceImpl service = new KnowledgeAdminServiceImpl(itemMapper, versionMapper, bindingMapper);

        service.delete(7L);

        org.mockito.InOrder order = inOrder(bindingMapper, versionMapper, itemMapper);
        order.verify(bindingMapper).delete(any());
        order.verify(versionMapper).delete(any());
        order.verify(itemMapper).deleteById(7L);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void deletingKnowledgeWithPublishedOrRetiredHistoryIsRejected() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
            KnowledgeVersion.class);
        KnowledgeItemMapper itemMapper = mock(KnowledgeItemMapper.class);
        KnowledgeVersionMapper versionMapper = mock(KnowledgeVersionMapper.class);
        KnowledgeBindingMapper bindingMapper = mock(KnowledgeBindingMapper.class);
        KnowledgeItem item = new KnowledgeItem();
        item.setKnowledgeItemId(7L);
        when(itemMapper.selectById(7L)).thenReturn(item);
        when(versionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        KnowledgeAdminServiceImpl service = new KnowledgeAdminServiceImpl(itemMapper, versionMapper, bindingMapper);

        assertThatThrownBy(() -> service.delete(7L))
            .isInstanceOf(org.dromara.common.core.exception.ServiceException.class)
            .hasMessageContaining("发布历史");

        ArgumentCaptor<LambdaQueryWrapper<KnowledgeVersion>> wrapperCaptor =
            ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(versionMapper).selectCount(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("status");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
            .contains("published", "retired");
        verify(bindingMapper, never()).delete(any());
        verify(versionMapper, never()).delete(any());
        verify(itemMapper, never()).deleteById(7L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"draft", "reviewing"})
    void changingPublishedKnowledgeToEditableStatusAppendsVersion(String targetStatus) {
        KnowledgeItemMapper itemMapper = mock(KnowledgeItemMapper.class);
        KnowledgeVersionMapper versionMapper = mock(KnowledgeVersionMapper.class);
        KnowledgeBindingMapper bindingMapper = mock(KnowledgeBindingMapper.class);
        KnowledgeItem item = new KnowledgeItem();
        item.setKnowledgeItemId(9L);
        item.setCurrentPublishedVersionId(99L);
        KnowledgeVersion version = new KnowledgeVersion();
        version.setKnowledgeVersionId(99L);
        version.setKnowledgeItemId(9L);
        version.setVersionNo(1);
        version.setStatus("published");
        version.setContent("已发布正文");
        KnowledgeBinding binding = new KnowledgeBinding();
        binding.setKnowledgeBindingId(999L);
        binding.setKnowledgeVersionId(99L);
        binding.setStatus("published");
        when(itemMapper.selectById(9L)).thenReturn(item);
        when(versionMapper.selectOne(any())).thenReturn(version);
        when(versionMapper.updateById(version)).thenReturn(1);
        when(versionMapper.insert(any(KnowledgeVersion.class))).thenReturn(1);
        when(bindingMapper.insert(any(KnowledgeBinding.class))).thenReturn(1);
        when(itemMapper.updateById(item)).thenReturn(1);
        KnowledgeAdminServiceImpl service = new KnowledgeAdminServiceImpl(itemMapper, versionMapper, bindingMapper);

        service.changeStatus(9L, targetStatus, 42L);

        assertThat(item.getCurrentPublishedVersionId()).isEqualTo(99L);
        assertThat(version.getStatus()).isEqualTo("published");
        assertThat(binding.getStatus()).isEqualTo("published");
        ArgumentCaptor<KnowledgeVersion> versionCaptor = ArgumentCaptor.forClass(KnowledgeVersion.class);
        verify(versionMapper).insert(versionCaptor.capture());
        assertThat(versionCaptor.getValue().getVersionNo()).isEqualTo(2);
        assertThat(versionCaptor.getValue().getStatus()).isEqualTo(targetStatus);
        assertThat(versionCaptor.getValue().getContent()).isEqualTo("已发布正文");
        verify(versionMapper, never()).updateById(any(KnowledgeVersion.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"draft", "reviewing", "published"})
    void changingRetiredKnowledgeAppendsVersionInsteadOfRevivingHistory(String targetStatus) {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
            KnowledgeBinding.class);
        KnowledgeItemMapper itemMapper = mock(KnowledgeItemMapper.class);
        KnowledgeVersionMapper versionMapper = mock(KnowledgeVersionMapper.class);
        KnowledgeBindingMapper bindingMapper = mock(KnowledgeBindingMapper.class);
        KnowledgeItem item = new KnowledgeItem();
        item.setKnowledgeItemId(10L);
        KnowledgeVersion retiredVersion = new KnowledgeVersion();
        retiredVersion.setKnowledgeVersionId(100L);
        retiredVersion.setKnowledgeItemId(10L);
        retiredVersion.setVersionNo(4);
        retiredVersion.setStatus("retired");
        retiredVersion.setContent("已退役正文");
        retiredVersion.setStructureJson("{\"kind\":\"copy\"}");
        retiredVersion.setSourceSummary("历史摘要");
        when(itemMapper.selectById(10L)).thenReturn(item);
        when(versionMapper.selectOne(any())).thenReturn(retiredVersion);
        when(versionMapper.updateById(retiredVersion)).thenReturn(1);
        when(versionMapper.insert(any(KnowledgeVersion.class))).thenReturn(1);
        when(bindingMapper.insert(any(KnowledgeBinding.class))).thenReturn(1);
        when(itemMapper.updateById(item)).thenReturn(1);
        KnowledgeAdminServiceImpl service = new KnowledgeAdminServiceImpl(itemMapper, versionMapper, bindingMapper);

        service.changeStatus(10L, targetStatus, 42L);

        assertThat(retiredVersion.getStatus()).isEqualTo("retired");
        ArgumentCaptor<KnowledgeVersion> versionCaptor = ArgumentCaptor.forClass(KnowledgeVersion.class);
        verify(versionMapper).insert(versionCaptor.capture());
        assertThat(versionCaptor.getValue().getVersionNo()).isEqualTo(5);
        assertThat(versionCaptor.getValue().getStatus()).isEqualTo(targetStatus);
        assertThat(versionCaptor.getValue().getContent()).isEqualTo("已退役正文");
        assertThat(versionCaptor.getValue().getStructureJson()).isEqualTo("{\"kind\":\"copy\"}");
        if ("published".equals(targetStatus)) {
            assertThat(item.getCurrentPublishedVersionId())
                .isEqualTo(versionCaptor.getValue().getKnowledgeVersionId());
        }
        verify(versionMapper, never()).updateById(any(KnowledgeVersion.class));
    }

    @Test
    void changingPublishedKnowledgeToRetiredRetiresItInPlace() {
        KnowledgeItemMapper itemMapper = mock(KnowledgeItemMapper.class);
        KnowledgeVersionMapper versionMapper = mock(KnowledgeVersionMapper.class);
        KnowledgeBindingMapper bindingMapper = mock(KnowledgeBindingMapper.class);
        KnowledgeItem item = new KnowledgeItem();
        item.setKnowledgeItemId(11L);
        item.setCurrentPublishedVersionId(110L);
        KnowledgeVersion version = new KnowledgeVersion();
        version.setKnowledgeVersionId(110L);
        version.setKnowledgeItemId(11L);
        version.setVersionNo(1);
        version.setStatus("published");
        KnowledgeBinding binding = new KnowledgeBinding();
        binding.setKnowledgeBindingId(111L);
        binding.setKnowledgeVersionId(110L);
        binding.setStatus("published");
        when(itemMapper.selectById(11L)).thenReturn(item);
        when(versionMapper.selectOne(any())).thenReturn(version);
        when(bindingMapper.selectOne(any())).thenReturn(binding);
        when(versionMapper.updateById(version)).thenReturn(1);
        when(bindingMapper.updateById(binding)).thenReturn(1);
        when(itemMapper.updateById(item)).thenReturn(1);
        KnowledgeAdminServiceImpl service = new KnowledgeAdminServiceImpl(itemMapper, versionMapper, bindingMapper);

        service.changeStatus(11L, "retired", 42L);

        assertThat(version.getStatus()).isEqualTo("retired");
        assertThat(binding.getStatus()).isEqualTo("retired");
        assertThat(item.getCurrentPublishedVersionId()).isNull();
        verify(versionMapper, never()).insert(any(KnowledgeVersion.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"draft", "reviewing", "published", "retired"})
    void changingKnowledgeToSameStatusIsIdempotent(String status) {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
            KnowledgeBinding.class);
        KnowledgeItemMapper itemMapper = mock(KnowledgeItemMapper.class);
        KnowledgeVersionMapper versionMapper = mock(KnowledgeVersionMapper.class);
        KnowledgeBindingMapper bindingMapper = mock(KnowledgeBindingMapper.class);
        KnowledgeItem item = new KnowledgeItem();
        item.setKnowledgeItemId(12L);
        KnowledgeVersion version = new KnowledgeVersion();
        version.setKnowledgeVersionId(120L);
        version.setKnowledgeItemId(12L);
        version.setVersionNo(1);
        version.setStatus(status);
        when(itemMapper.selectById(12L)).thenReturn(item);
        when(versionMapper.selectOne(any())).thenReturn(version);
        when(versionMapper.updateById(version)).thenReturn(1);
        when(bindingMapper.insert(any(KnowledgeBinding.class))).thenReturn(1);
        when(itemMapper.updateById(item)).thenReturn(1);
        KnowledgeAdminServiceImpl service = new KnowledgeAdminServiceImpl(itemMapper, versionMapper, bindingMapper);

        service.changeStatus(12L, status, 42L);

        verify(versionMapper, never()).insert(any(KnowledgeVersion.class));
        verify(versionMapper, never()).updateById(any(KnowledgeVersion.class));
        verify(itemMapper, never()).updateById(any(KnowledgeItem.class));
        verifyNoInteractions(bindingMapper);
    }

    private KnowledgeAdminServiceImpl importService() {
        KnowledgeItemMapper itemMapper = mock(KnowledgeItemMapper.class);
        KnowledgeVersionMapper versionMapper = mock(KnowledgeVersionMapper.class);
        KnowledgeBindingMapper bindingMapper = mock(KnowledgeBindingMapper.class);
        when(itemMapper.insert(any(KnowledgeItem.class))).thenReturn(1);
        when(versionMapper.insert(any(KnowledgeVersion.class))).thenReturn(1);
        when(bindingMapper.insert(any(KnowledgeBinding.class))).thenReturn(1);
        return new KnowledgeAdminServiceImpl(itemMapper, versionMapper, bindingMapper);
    }

    private MultipartFile textFile(String fileName, String mimeType, String content) {
        return new MockMultipartFile("files", fileName, mimeType, content.getBytes(StandardCharsets.UTF_8));
    }

    private MultipartFile reportedSizeFile(String fileName, String mimeType, long reportedSize) {
        return new MockMultipartFile("files", fileName, mimeType, "知识".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public long getSize() {
                return reportedSize;
            }
        };
    }

    private MultipartFile misleadingSizeFile(String fileName, byte[] content) {
        return new MockMultipartFile("files", fileName, "application/octet-stream", content) {
            @Override
            public long getSize() {
                return 1L;
            }
        };
    }
}
