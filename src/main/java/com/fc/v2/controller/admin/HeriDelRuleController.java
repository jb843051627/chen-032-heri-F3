package com.fc.v2.controller.admin;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.THeriDelRow;
import com.fc.v2.model.auto.THeriDelRule;
import com.fc.v2.model.custom.DelAudit;
import com.fc.v2.model.custom.DelStroke;
import com.fc.v2.service.ITHeriDelRuleService;
import com.fc.v2.shiro.util.ShiroUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 港口放货台 Controller（gate-ledger：一道一道落签，无散行增删改入口）。
 *
 * <p>页面上先亮出来的不算依据：查询、抽验都以册上（签认册+留痕册）为准。</p>
 *
 * @author fuce
 * @date 2026-10-07
 */
@Api(value = "港口放货台")
@RestController
@RequestMapping("/heriDelRule")
public class HeriDelRuleController extends BaseController {

    @Autowired
    private ITHeriDelRuleService heriDelRuleService;

    @Log(title = "放货台开单", action = "open")
    @ApiOperation(value = "开单分档", notes = "看数量按港里判据定档；判据没写到的退回重定，系统不自己挑")
    @PostMapping("/open")
    @RequiresPermissions("heri:delRule:open")
    public AjaxResult open(@RequestParam String billNo, @RequestParam BigDecimal qty) {
        return retobject(200, heriDelRuleService.open(billNo, qty, null, currentOperator()));
    }

    @Log(title = "放货台落签", action = "sign")
    @ApiOperation(value = "落签下一道", notes = "关次系统派不许手填；抢着签先落的算数，后到的作废留痕")
    @PostMapping("/sign")
    @RequiresPermissions("heri:delRule:sign")
    public AjaxResult sign(@RequestParam String billNo, @RequestParam String action,
                           @RequestParam(required = false) String attemptId,
                           @RequestParam(required = false) Long baseStroke) {
        DelStroke r = heriDelRuleService.sign(billNo, currentOperator(), action, null,
                attemptId, baseStroke);
        return retobject(200, r);
    }

    @Log(title = "放货台压签", action = "resign")
    @ApiOperation(value = "重签悬着的那道", notes = "后一笔压前一笔，前一笔作废留痕；已定的不许动")
    @PostMapping("/resign")
    @RequiresPermissions("heri:delRule:sign")
    public AjaxResult resign(@RequestParam String billNo, @RequestParam String action,
                             @RequestParam(required = false) String attemptId,
                             @RequestParam(required = false) Long baseStroke) {
        return retobject(200, heriDelRuleService.resign(billNo, currentOperator(), action, null,
                attemptId, baseStroke));
    }

    @Log(title = "放货台回退", action = "rollback")
    @ApiOperation(value = "回退", notes = "只退悬着的那一道；已定的原地不动，退了几回记一回")
    @PostMapping("/rollback")
    @RequiresPermissions("heri:delRule:sign")
    public AjaxResult rollback(@RequestParam String billNo,
                               @RequestParam(required = false) String reason) {
        return retobject(200, heriDelRuleService.rollback(billNo, currentOperator(), null, reason));
    }

    @Log(title = "放货台收口重来", action = "redo")
    @ApiOperation(value = "收口重来", notes = "关齐封口后整件事收口，开新一趟；老趟一条不动")
    @PostMapping("/redo")
    @RequiresPermissions("heri:delRule:sign")
    public AjaxResult redo(@RequestParam String billNo) {
        return retobject(200, heriDelRuleService.redo(billNo, currentOperator(), null));
    }

    @Log(title = "放货台确认", action = "confirm")
    @ApiOperation(value = "换单/交核确认", notes = "换单档先换单、改提单档交上去核，确认留痕")
    @PostMapping("/confirm")
    @RequiresPermissions("heri:delRule:sign")
    public AjaxResult confirm(@RequestParam String billNo, @RequestParam String kind) {
        return retobject(200, heriDelRuleService.confirm(billNo, kind, currentOperator(), null));
    }

    @Log(title = "放货台放货", action = "release")
    @ApiOperation(value = "放货", notes = "关齐核发才放：直发档当场放，换单档先换单，改提单档交上去核")
    @PostMapping("/release")
    @RequiresPermissions("heri:delRule:release")
    public AjaxResult release(@RequestParam String billNo) {
        THeriDelRow row = heriDelRuleService.release(billNo, currentOperator(), null);
        return retobject(200, row);
    }

    @ApiOperation(value = "当前趟凭证头", notes = "页面看的汇总数；对不对得上以册上为准")
    @GetMapping("/current")
    @RequiresPermissions("heri:delRule:list")
    public AjaxResult current(@RequestParam String billNo) {
        THeriDelRule head = heriDelRuleService.selectCurrent(billNo);
        if (head == null) {
            return error("单不在册上：" + billNo);
        }
        return retobject(200, head);
    }

    @ApiOperation(value = "指定趟凭证头", notes = "老单子照旧查得回")
    @GetMapping("/round")
    @RequiresPermissions("heri:delRule:list")
    public AjaxResult round(@RequestParam String billNo, @RequestParam Integer roundNo) {
        THeriDelRule head = heriDelRuleService.selectRound(billNo, roundNo);
        if (head == null) {
            return error("单不在册上：" + billNo + " 第" + roundNo + "趟");
        }
        return retobject(200, head);
    }

    @ApiOperation(value = "签认册", notes = "这一趟的全部落笔：有效、作废、被拒都在，痕不许抹")
    @GetMapping("/strokes")
    @RequiresPermissions("heri:delRule:list")
    public AjaxResult strokes(@RequestParam String billNo,
                              @RequestParam(required = false) Integer roundNo) {
        return retobject(200, heriDelRuleService.listStrokes(billNo, roundNo));
    }

    @ApiOperation(value = "留痕册", notes = "谁落的、何时落的、退了几回、最后哪一道定的")
    @GetMapping("/trail")
    @RequiresPermissions("heri:delRule:list")
    public AjaxResult trail(@RequestParam String billNo) {
        return retobject(200, heriDelRuleService.listTrail(billNo));
    }

    @ApiOperation(value = "抽验倒着核", notes = "核得出的算数，核不出的那几笔不作数；以册上为准")
    @GetMapping("/audit")
    @RequiresPermissions("heri:delRule:list")
    public AjaxResult audit(@RequestParam String billNo, @RequestParam Integer roundNo) {
        return retobject(200, heriDelRuleService.audit(billNo, roundNo));
    }

    @ApiOperation(value = "随机抽一单倒着核", notes = "抽到哪单核哪单")
    @GetMapping("/spotCheck")
    @RequiresPermissions("heri:delRule:list")
    public AjaxResult spotCheck() {
        return retobject(200, heriDelRuleService.spotCheck());
    }

    @ApiOperation(value = "港里判据：档→几道关", notes = "加关口不追老账，只影响往后开的单")
    @GetMapping("/gradeGates")
    @RequiresPermissions("heri:delRule:list")
    public AjaxResult gradeGates() {
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("gradeGates", heriDelRuleService.gradeGates());
        data.put("at", new Date());
        return retobject(200, data);
    }

    private static String currentOperator() {
        try {
            return ShiroUtils.getLoginName();
        } catch (RuntimeException e) {
            return "unknown";
        }
    }
}
