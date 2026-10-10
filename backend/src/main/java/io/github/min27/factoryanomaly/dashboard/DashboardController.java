package io.github.min27.factoryanomaly.dashboard;

import io.github.min27.factoryanomaly.alert.AlertService;
import io.github.min27.factoryanomaly.decision.EngineProperties;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Thymeleaf 대시보드 (D-020, D-022). 전체 페이지를 한 번 그리고, 이후에는 htmx가 구역(fragment)별로 주기적으로 다시 받아간다.
 */
@Controller
@RequiredArgsConstructor
public class DashboardController {

    /** 알람 상태가 바뀌었음을 알리는 htmx 이벤트. 설비 카드 구역이 다음 폴링을 기다리지 않고 바로 갱신한다. */
    static final String ALERTS_CHANGED = "alerts-changed";

    private final DashboardQueryService queryService;
    private final AlertService alertService;
    private final DashboardProperties properties;
    private final EngineProperties engineProperties;

    @ModelAttribute
    void common(Model model) {
        model.addAttribute("refreshSeconds", properties.refreshSeconds());
        model.addAttribute("primaryEngine", engineProperties.primary());
        model.addAttribute("activeEngines", engineProperties.active());
    }

    @GetMapping("/")
    String root() {
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    String dashboard(Model model) {
        model.addAttribute("equipment", queryService.equipmentStatuses());
        model.addAttribute("alerts", queryService.unresolvedAlerts());
        model.addAttribute("comparison", queryService.engineComparison());
        return "dashboard";
    }

    @GetMapping("/dashboard/equipment")
    String equipment(Model model) {
        model.addAttribute("equipment", queryService.equipmentStatuses());
        return "dashboard :: equipment";
    }

    @GetMapping("/dashboard/alerts")
    String alerts(Model model) {
        model.addAttribute("alerts", queryService.unresolvedAlerts());
        return "dashboard :: alerts";
    }

    @GetMapping("/dashboard/decisions")
    String decisions(Model model) {
        model.addAttribute("comparison", queryService.engineComparison());
        return "dashboard :: decisions";
    }

    /** 확인 후 알람 목록 구역을 다시 그려 돌려준다. 허용되지 않는 상태 변경은 409 (GlobalExceptionHandler). */
    @PostMapping("/dashboard/alerts/{id}/acknowledge")
    String acknowledge(@PathVariable long id, Model model, HttpServletResponse response) {
        alertService.acknowledge(id);
        response.setHeader("HX-Trigger", ALERTS_CHANGED);
        return alerts(model);
    }

    @PostMapping("/dashboard/alerts/{id}/resolve")
    String resolve(@PathVariable long id, Model model, HttpServletResponse response) {
        alertService.resolve(id);
        response.setHeader("HX-Trigger", ALERTS_CHANGED);
        return alerts(model);
    }
}
