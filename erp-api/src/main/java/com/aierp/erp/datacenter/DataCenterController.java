package com.aierp.erp.datacenter;

import com.aierp.common.AjaxResult;
import com.aierp.security.common.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/erp-api/data-center")
public class DataCenterController {
    private final DataCenterService service;
    @GetMapping("/catalog") public AjaxResult catalog() {
        DataCenterAccess.require("read");
        return AjaxResult.success(Map.of("sources",DataSources.ALL,"metrics",DataCenterService.definitions(),
                "metricVersion",DataCenterService.VERSION,"syncMode","全量对账快照","currency","CNY"));
    }
    @GetMapping("/jobs") public AjaxResult jobs() {DataCenterAccess.require("read");return AjaxResult.success(service.jobs());}
    @PostMapping("/sync") public AjaxResult sync() {DataCenterAccess.require("manage");return AjaxResult.success(service.synchronize(SecurityUtils.getUserId()));}
    @GetMapping("/mappings") public AjaxResult mappings() {DataCenterAccess.require("read");return AjaxResult.success(service.mappings());}
    @PutMapping("/mappings") public AjaxResult mapping(@RequestBody DataCenterService.Mapping body) {
        DataCenterAccess.require("manage");service.saveMapping(body,SecurityUtils.getUserId());return AjaxResult.success();
    }
    @GetMapping("/metrics") public AjaxResult metrics(@RequestParam LocalDate from,@RequestParam LocalDate to,
            @RequestParam(required=false) String shop,@RequestParam(required=false) String batch) {
        DataCenterAccess.require("read");return AjaxResult.success(service.metrics(from,to,shop,batch));
    }
    @GetMapping("/rows") public AjaxResult rows(@RequestParam String kind,@RequestParam(required=false) String batch,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,
            @RequestParam(defaultValue="false") boolean issues) {
        DataCenterAccess.require("read");return AjaxResult.success(service.rows(kind,batch,page,size,issues));
    }
    @GetMapping("/lineage") public AjaxResult lineage(@RequestParam String kind,@RequestParam long id,@RequestParam(required=false) String batch) {
        DataCenterAccess.require("read");return AjaxResult.success(service.lineage(kind,id,batch));
    }
    @ExceptionHandler(IllegalArgumentException.class) public AjaxResult invalid(IllegalArgumentException ex) {
        return AjaxResult.error(ex.getMessage());
    }
}
