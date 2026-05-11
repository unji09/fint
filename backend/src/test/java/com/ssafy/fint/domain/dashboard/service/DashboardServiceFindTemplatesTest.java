package com.ssafy.fint.domain.dashboard.service;

import com.ssafy.fint.domain.dashboard.dto.DashboardTemplateGroupResponse;
import com.ssafy.fint.domain.dashboard.entity.DashboardTemplate;
import com.ssafy.fint.domain.dashboard.entity.WidgetType;
import com.ssafy.fint.domain.dashboard.repository.DashboardTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceFindTemplatesTest {

    private static final int GROUP_SIZE = 8;

    @Mock private DashboardTemplateRepository dashboardTemplateRepository;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(
                null, null, dashboardTemplateRepository, null, null, null
        );
    }

    @Test
    @DisplayName("groupIds 없이 호출하면 전체 3그룹을 반환한다.")
    void findAllTemplates() {
        when(dashboardTemplateRepository.findAll()).thenReturn(allTemplates());

        List<DashboardTemplateGroupResponse> result = dashboardService.findTemplates(null);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).groupId()).isEqualTo(1L);
        assertThat(result.get(1).groupId()).isEqualTo(2L);
        assertThat(result.get(2).groupId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("groupIds 로 특정 그룹만 필터링할 수 있다.")
    void findTemplatesByGroupIds() {
        when(dashboardTemplateRepository.findAll()).thenReturn(allTemplates());

        List<DashboardTemplateGroupResponse> result =
                dashboardService.findTemplates(List.of(1L, 3L));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).groupId()).isEqualTo(1L);
        assertThat(result.get(1).groupId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("각 그룹에는 해당 ID 범위의 위젯이 포함된다.")
    void groupContainsCorrectWidgets() {
        when(dashboardTemplateRepository.findAll()).thenReturn(allTemplates());

        List<DashboardTemplateGroupResponse> result =
                dashboardService.findTemplates(List.of(1L));

        assertThat(result).hasSize(1);
        var group1 = result.get(0);
        assertThat(group1.widgets()).hasSize(GROUP_SIZE);
        assertThat(group1.widgets().get(0).templateId()).isEqualTo(1L);
        assertThat(group1.widgets().get(GROUP_SIZE - 1).templateId()).isEqualTo((long) GROUP_SIZE);
    }

    @Test
    @DisplayName("템플릿이 없으면 빈 리스트를 반환한다.")
    void emptyTemplates() {
        when(dashboardTemplateRepository.findAll()).thenReturn(List.of());

        List<DashboardTemplateGroupResponse> result = dashboardService.findTemplates(null);

        assertThat(result).isEmpty();
    }

    private List<DashboardTemplate> allTemplates() {
        return java.util.stream.LongStream.rangeClosed(1, GROUP_SIZE * 3L)
                .mapToObj(this::newTemplate)
                .toList();
    }

    private DashboardTemplate newTemplate(long id) {
        DashboardTemplate template = DashboardTemplate.builder()
                .widgetType(WidgetType.BAR_CHART)
                .title("위젯 " + id)
                .config(Map.of())
                .position(Map.of("x", 0, "y", 0))
                .build();
        ReflectionTestUtils.setField(template, "dashboardTemplateId", id);
        return template;
    }
}
